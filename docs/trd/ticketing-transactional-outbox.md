# Ticketing 예약 상태 이벤트 Transactional Outbox

## 문제

결제 완료 Consumer가 Reservation을 `CONFIRMED`로 바꾸고 Ticket을 `ISSUED`로 전환한 뒤 `SOLD_CONFIRMED`를 Kafka에 직접 발행하면 DB 트랜잭션과 Kafka 발행의 원자성을 보장할 수 없다. Kafka 발행 후 DB가 롤백되면 좌석 서비스에는 SOLD 이벤트가 전달됐지만 예약과 티켓은 결제 전 상태로 남을 수 있다. 반대로 DB 커밋 후 발행이 실패하면 결제된 좌석이 SOLD로 전환되지 않을 수 있다.

## 적용 범위

이번 단계는 결제 완료 뒤 생성되는 `SOLD_CONFIRMED`와 판매 취소 뒤 생성되는 `SALE_CANCELED`에 Transactional Outbox를 적용한다. 두 이벤트를 예약 번호를 messageKey로 사용하는 같은 Outbox에 기록해, 결제 직후 취소되더라도 SOLD가 먼저 발행 완료되기 전에는 판매 취소를 선택하지 않는다. 임시 선점 만료를 뜻하는 `HOLD_RELEASED`는 SOLD를 되돌리지 않고, 정상 판매 취소를 뜻하는 `SALE_CANCELED`만 같은 예약 소유의 SOLD를 AVAILABLE로 전환한다.

Relay는 여러 Ticketing 인스턴스에서 동시에 Scheduler가 실행될 수 있다는 전제로 동작한다. 각 Relay는 MySQL `FOR UPDATE SKIP LOCKED`로 짧게 행을 선점하고, DB 락을 해제한 다음 Kafka를 호출한다. 동일 Outbox 행을 정상적인 동시 실행에서 여러 Relay가 함께 발행하지 않도록 `PROCESSING`, `claimToken`, `claimedAt` 기반의 논리적 lease를 사용한다.

## 처리 흐름

```text
Kafka payment.booking
  -> PaymentConsumer
  -> BookingService @Transactional
     -> Reservation PENDING_PAYMENT/PENDING_DEPOSIT -> CONFIRMED
     -> Ticket PENDING -> ISSUED
     -> ticketing_outbox_events PENDING 저장
  -> DB commit

TicketingOutboxRelayScheduler (`ticketing.outbox.claim-enabled=true`, 기본 3초 간격)
  -> [짧은 DB 트랜잭션, READ COMMITTED]
     -> 예약별 PENDING/FAILED 선두를 FOR UPDATE SKIP LOCKED로 조회
     -> PROCESSING + claimToken + claimedAt 기록
     -> commit과 함께 DB row lock 해제
  -> [DB 트랜잭션 없음]
     -> claim한 이벤트를 Kafka booking.ticketing에 비동기 요청
     -> 전송 결과 수집
  -> [짧은 DB 트랜잭션]
     -> id + PROCESSING + claimToken 일치 조건으로 결과 반영
     -> 성공: PUBLISHED / 실패: FAILED 및 retryCount 증가
```

Reservation, Ticket, Outbox는 같은 Ticketing DB 트랜잭션에서 변경된다. 따라서 Outbox 직전이나 직후에 런타임 예외가 발생하면 세 변경이 함께 롤백된다. Outbox 기록기는 기존 트랜잭션이 없으면 실패하도록 `MANDATORY` 전파 속성을 사용한다. Kafka 네트워크 호출은 결제 완료 및 Relay 상태 저장 트랜잭션 밖에서 Scheduler가 수행한다.

## claim 데이터 모델

`ticketing_outbox_events`는 기존 topic, messageKey, payload, eventType, status, retryCount에 다음 lease 정보를 추가한다.

| 필드 | 의미 |
|---|---|
| `status` | `PENDING`, `FAILED`, `PROCESSING`, `PUBLISHED` 처리 상태 |
| `claim_token` | 해당 행의 처리 결과를 쓸 수 있는 Relay 실행 UUID |
| `claimed_at` | claim 시작 시각. 죽은 Relay의 lease 만료 판단에 사용 |

`claimToken`은 사용자 요청이나 Pod 이름에서 받지 않는다. Scheduler가 배치를 claim할 때 `UUID.randomUUID()`로 새 값을 만들며, 한 번의 claim 배치가 같은 토큰을 사용한다. Pod 이름을 쓰지 않는 이유는 같은 Pod의 이전 실행과 다음 실행도 구분해야 하기 때문이다.

```text
Pod A / relay 실행 1 -> token-a
Pod A / relay 실행 2 -> token-b
Pod B / relay 실행 1 -> token-c
```

상태 흐름은 다음과 같다.

```text
PENDING ─┐
         ├─ claim 성공 -> PROCESSING ─┬─ Kafka 성공 -> PUBLISHED
FAILED ──┘                            └─ Kafka 실패 -> FAILED(retryCount + 1)

PROCESSING ─ claim timeout -> FAILED(retryCount + 1) -> 재claim 가능
```

`(status, retry_count, id)` 인덱스는 claim 후보 조회에, `(status, claimed_at)` 인덱스는 만료 lease 회수에 사용한다. `id`가 기본 키이므로 결과 반영의 `id + claimToken` 조회에는 별도 claim token 인덱스를 추가하지 않았다.

## `SKIP LOCKED` 선점 원리

여러 Relay가 단순 SELECT만 수행하면 같은 PENDING 행을 모두 읽고 중복 발행할 수 있다. claim 단계에서는 다음 형태의 MySQL 쿼리를 실행한다.

```sql
SELECT event.*
FROM ticketing_outbox_events event
WHERE event.status = :status -- PENDING과 FAILED를 각각 조회
  AND NOT EXISTS (
    SELECT 1
    FROM ticketing_outbox_events older
    WHERE older.topic = event.topic
      AND older.message_key = event.message_key
      AND older.id < event.id
      AND older.status IN ('PENDING', 'FAILED', 'PROCESSING')
  )
ORDER BY event.retry_count, event.id
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

- `FOR UPDATE`는 선택한 행을 claim 트랜잭션 동안 다른 Relay가 변경하지 못하게 한다.
- `SKIP LOCKED`는 다른 Relay가 잡은 행의 해제를 기다리지 않고 현재 조회에서 건너뛰게 한다.
- `NOT EXISTS`는 같은 topic·messageKey의 오래된 PENDING·FAILED·PROCESSING이 있으면 후속 이벤트를 선택하지 않는다.
- PENDING과 FAILED를 각각 최대 100건 조회한다. PENDING이 계속 유입돼도 FAILED 재시도 배치가 굶지 않으며, Kafka 전달 목록에서는 PENDING 배치를 먼저 둔다.
- claim 트랜잭션은 `READ COMMITTED`를 사용해 MySQL 기본 `REPEATABLE READ`의 불필요한 gap/next-key lock 범위를 줄인다.
- 조회 직후 같은 트랜잭션에서 PROCESSING과 claim 정보를 기록하고 즉시 commit한다. Kafka 호출 동안 비관적 row lock이나 DB 트랜잭션을 유지하지 않는다.

`SKIP LOCKED`는 한 번의 동시 실행에서 각 Relay가 정확히 같은 개수의 행을 받는 공정성을 보장하지 않는다. 어떤 Relay는 10건, 다른 Relay는 더 적게 받을 수 있지만, 잠긴 행을 기다리지 않고 서로 겹치지 않는 행을 가져가며 남은 행은 다음 poll에서 처리한다.

## 소유권 비교와 오래된 실행 차단

Kafka 결과는 다음 조건이 모두 맞을 때만 반영한다.

```sql
UPDATE ticketing_outbox_events
SET status = 'PUBLISHED', claim_token = NULL, claimed_at = NULL
WHERE id = :id
  AND status = 'PROCESSING'
  AND claim_token = :claimToken;
```

실패도 동일한 소유권 조건으로 `FAILED`, `retry_count + 1`을 반영한다. update count가 0이면 lease가 이미 만료되거나 다른 Relay가 다시 claim한 것이므로, 지연된 이전 실행은 새 소유자의 상태를 덮어쓰지 않는다. Redis 락을 현재 값이 내 token일 때만 해제하는 compare-and-delete와 같은 원리다.

## 만료 claim 회수

Relay가 PROCESSING을 저장한 뒤 종료되면 해당 행을 그대로 두지 않는다. 기본 30초마다 `claimedAt`이 claim timeout보다 오래된 PROCESSING을 FAILED로 되돌리고 retryCount를 증가시킨다. 기본 timeout은 5분이다.

```text
PROCESSING + claimedAt < now - claimTimeout
  -> FAILED
  -> claimToken/claimedAt 제거
  -> 다음 Relay가 재claim
```

claim timeout은 Kafka producer의 최대 전송 대기시간과 정상 배치 처리시간보다 길어야 한다. 너무 짧으면 아직 전송 중인 이벤트를 다른 Relay가 회수해 중복 발행할 수 있고, 너무 길면 죽은 Pod의 복구가 늦어진다. 기본값의 적정성은 아직 부하 실험으로 검증하지 않았다.

## 데이터와 예약별 순서

`SOLD_CONFIRMED`와 `SALE_CANCELED`의 messageKey는 예약 번호다.

Relay는 같은 topic·messageKey에서 ID가 가장 낮은 활성 이벤트만 선택한다. 앞선 이벤트가 `PENDING`, `FAILED`, `PROCESSING`이면 후속 이벤트를 선택하지 않으며, 서로 다른 예약은 독립적으로 처리한다. 따라서 SOLD가 한 Relay에서 PROCESSING인 동안 다른 Relay가 후속 SALE_CANCELED를 가져갈 수 없다. 신규 PENDING을 FAILED보다 먼저 정렬하고 FAILED끼리는 retryCount가 낮은 행을 우선한다.

## 멱등성과 전달 보장

동일 결제 이벤트를 순차 중복 소비하면 Reservation 상태 머신이 `ALREADY_APPLIED`를 반환하므로 Outbox를 다시 만들지 않는다. 동시에 같은 Reservation을 확정하면 `@Version` 낙관적 락으로 한 트랜잭션만 커밋되고, 패자가 만든 Outbox도 해당 트랜잭션과 함께 롤백된다.

Relay는 at-least-once 성격을 가진다. claim은 정상적인 다중 인스턴스 경쟁에서 중복 발행을 줄이지만, Kafka 발행에는 성공하고 `PUBLISHED` DB 반영 전에 프로세스가 종료되는 원자성 공백까지 제거하지는 못한다. 만료 후 다른 Relay가 같은 이벤트를 다시 발행할 수 있으므로 좌석 Consumer의 의미상 멱등성이 계속 필요하다. 이번 구현은 Kafka exactly-once를 주장하지 않는다.

| 장애 시점 | DB 상태 | 복구 및 결과 |
|---|---|---|
| claim commit 전 종료 | PENDING/FAILED | DB rollback, 다른 Relay가 claim |
| claim commit 후 Kafka 호출 전 종료 | PROCESSING | timeout 후 FAILED로 회수하여 재시도 |
| Kafka 실패 | PROCESSING | 소유권 확인 후 FAILED, 재시도 |
| Kafka 성공 후 PUBLISHED 반영 전 종료 | PROCESSING | timeout 후 재발행 가능, Consumer 멱등성 필요 |
| 오래된 Relay가 timeout 뒤 결과 저장 | 다른 token의 PROCESSING 또는 최종 상태 | token 불일치로 update count 0, 상태 덮어쓰기 차단 |

## 스케줄

- claim Relay 활성화: `ticketing.outbox.claim-enabled`, 기본 `false`
- Relay: `ticketing.outbox.relay.fixed-delay-ms`, 기본 3,000ms
- claim timeout: `ticketing.outbox.claim-timeout-ms`, 기본 300,000ms
- 만료 claim 검사: `ticketing.outbox.claim-recovery-delay-ms`, 기본 30,000ms
- PUBLISHED 정리: `ticketing.outbox.cleanup.cron`, 기본 매일 03:00
- 한 번의 상태별 claim 배치 크기: PENDING 최대 100건 + FAILED 최대 100건

## 검증 범위

- `SOLD_CONFIRMED`를 Kafka에 직접 발행하지 않고 PENDING Outbox로 저장하는지 단위 검증한다.
- 실제 BookingService 결제 완료 경로에서 Reservation, Ticket, Outbox가 함께 커밋되고 순차 중복 이벤트가 Outbox를 추가하지 않는지 H2 DB 통합 검증한다.
- 실제 BookingService 경로에서 Outbox 직렬화가 실패하면 Reservation과 Ticket 변경도 롤백되는지 H2 DB 통합 검증한다.
- 실제 BookingService의 가상계좌 입금 완료를 순차 중복 처리해도 SOLD Outbox가 한 건만 남는지 H2 DB 통합 검증한다.
- 실제 BookingService에서 결제 확정 후 취소하면 SOLD 다음 SALE_CANCELED 순서로 Outbox가 기록되는지 H2 DB 통합 검증한다.
- Outbox 기록기를 트랜잭션 밖에서 직접 호출하면 `MANDATORY` 조건으로 실패하는지 검증한다.
- Outbox 기록 뒤 예외가 발생하면 Reservation, Ticket, Outbox가 모두 롤백되는지 H2 DB 통합 검증한다.
- 실제 MySQL에서 같은 Reservation을 동시에 확정할 때 한 트랜잭션만 커밋되고 낙관적 락 패자의 Outbox도 함께 롤백되는지 검증한다.
- 결제 완료와 가상계좌 입금 완료 이벤트를 순차 중복 호출해 Outbox 기록이 한 번만 요청되는지 단위 검증한다.
- 같은 예약의 선행 활성 이벤트가 후속 이벤트를 막고 다른 예약은 독립적으로 선택되는지 Repository 테스트로 검증한다.
- 선행 SOLD가 FAILED인 동안 후속 SALE_CANCELED를 선택하지 않는지 Repository 테스트로 검증한다.
- Scheduler가 PENDING을 FAILED보다 먼저 Relay에 전달하고, PUBLISHED 행을 정리하는지 단위 검증한다.
- 테스트 외부 트랜잭션을 끈 상태에서 실제 Repository와 mock KafkaTemplate을 연결해, Kafka 호출 시 DB 트랜잭션이 없고 실패가 FAILED와 retryCount로 저장된 뒤 다음 Relay 성공 시 PUBLISHED로 커밋되는지 H2 DB 통합 검증한다.
- H2에서 잘못된 claimToken으로는 PROCESSING 상태를 변경할 수 없고, 올바른 token만 PUBLISHED/FAILED를 반영하는지 검증한다.
- H2에서 만료된 PROCESSING을 FAILED로 회수하고 새 token으로 다시 claim하는지 검증한다.
- PENDING이 batch size보다 많아도 FAILED 배치를 별도로 claim해 재시도가 굶지 않는지 검증한다.
- 실제 MySQL 8.4에서 두 트랜잭션이 commit 전까지 동시에 열린 상태로 `SKIP LOCKED` claim을 수행해 선택 행이 겹치지 않고, 남은 행이 다음 poll에서 모두 claim되는지 검증한다.
- 실제 MySQL 8.4에서 한 Relay가 SOLD를 claim한 동안 다른 Relay가 같은 예약의 SALE_CANCELED를 추월하지 못하는지 검증한다.
- 선행 SOLD가 PROCESSING으로 commit된 뒤 다음 poll에서도 후속 SALE_CANCELED가 선택되지 않고, SOLD가 PUBLISHED가 된 뒤에만 선택되는지 검증한다.
- timeout으로 회수된 행을 새 token이 재claim한 뒤 오래된 token의 실패 결과가 도착해도 새 PROCESSING 상태를 덮어쓰지 못하는지 검증한다.
- timeout이 지나지 않은 PROCESSING은 회수 대상에서 제외되는지 검증한다.
- Relay 스레드가 중단되면 Kafka 결과를 확인하지 못한 행을 즉시 FAILED로 단정하지 않고 PROCESSING으로 남겨 timeout 회수에 맡기는지 검증한다.
- `ticketing.outbox.claim-enabled=false`에서는 claim Relay Scheduler Bean이 생성되지 않는지 검증한다.

## 아직 검증하거나 주장하지 않는 내용

- 실제 Kafka 장애, 브로커 재시작, 프로세스 강제 종료를 포함한 전달 통합 검증
- Kafka exactly-once 또는 이벤트가 물리적으로 한 번만 발행된다는 보장
- Kubernetes에서 실제 Ticketing Pod 두 개 이상을 실행한 end-to-end 검증
- FAILED 이벤트의 시간 기반 backoff, 최대 재시도, DLT 및 운영자 redrive
- Outbox 적재량과 Relay 지연에 대한 부하 시험 수치
- batch size, poll 주기, claim timeout 기본값의 성능상 적정성
- 외부 결제 취소 성공 뒤 Ticketing DB 트랜잭션이 실패하는 부분 실패의 자동 복구. 결제 서비스는 동일 idempotency key의 중복 취소를 막지만, Ticketing이 자동으로 재시도하거나 보상하는 흐름은 아직 없다.

## 배포와 롤백 제약

Outbox 기록 코드와 Relay를 한 번에 처음 배포한 뒤 구 버전으로 롤백하면, 신 버전이 남긴 PENDING/FAILED 행을 구 버전이 처리하지 못한다. 실제 배포에서는 Relay와 테이블을 먼저 배포한 다음 이벤트 기록 경로를 전환하는 단계적 배포가 필요하다. 한 번에 배포한다면 롤백 전에 활성 Outbox를 모두 발행하고 비어 있는지 확인하는 drain 절차가 필요하다. 이 절차를 자동화하거나 배포 환경에서 검증한 상태는 아니다.

`SALE_CANCELED`는 새 이벤트 타입이므로 구 Consumer와 신 Producer가 공존하는 Rolling Update에서 바로 발행하면 안 된다. 구 Consumer는 알 수 없는 타입을 정상 반환하므로 메시지를 처리하지 않고 ACK할 수 있다. 다중 인스턴스 배포에서는 먼저 `SALE_CANCELED` 소비 지원만 배포하고 모든 구 Consumer가 제거된 것을 확인한 뒤, Outbox Relay와 생산 경로를 활성화해야 한다.

claim 기능 배포도 DDL과 애플리케이션 전환을 구분해야 한다. 구 Relay는 PROCESSING을 활성 선행 이벤트로 인식하지 않으므로 구·신 Relay를 동시에 실행하면, 신 Relay가 SOLD를 PROCESSING으로 claim한 사이 구 Relay가 후속 SALE_CANCELED를 선택할 수 있다. 따라서 단순히 같은 Rolling Update 안에서 신 Relay를 바로 켜지 않는다.

안전한 전환 순서는 다음과 같다.

1. nullable `claim_token`, `claimed_at` 컬럼과 PROCESSING 상태를 DB가 수용하도록 먼저 반영한다.
2. `ticketing.outbox.claim-enabled=false`로 신 버전을 배포한다. 신 Pod에서는 claim Scheduler가 생성되지 않고, 남아 있는 구 Pod의 Relay만 계속 동작한다.
3. 모든 구 버전 Pod가 제거됐는지 확인한다.
4. `TICKETING_OUTBOX_CLAIM_ENABLED=true`로 설정을 바꿔 claim Relay를 활성화한다. 이 전환 중 false인 신 Pod는 Relay를 실행하지 않으므로 true인 Pod와 소유권 방식이 충돌하지 않는다.

기본값을 `false`로 둔 이유는 실수로 신 Relay와 구 Relay가 섞이는 것보다 일시적으로 Relay가 멈춰 PENDING이 쌓이는 편이 복구 가능하고 순서 정합성에 안전하기 때문이다. 모든 구 Relay가 제거되고 claim 기능을 켠 이후부터 정상적인 다중 인스턴스 경쟁에서 동일 행 중복 claim 방지 보장이 성립한다. 이 단계적 배포와 Kubernetes 다중 Pod 전환은 아직 실제 환경에서 검증하지 않았다.

롤백할 때도 claim Relay를 먼저 비활성화하고 PROCESSING이 없을 때까지 회수·처리한 뒤 구 버전으로 내려야 한다. PROCESSING을 남긴 채 구 버전을 실행하면 구 버전 쿼리가 해당 상태를 이해하지 못한다.

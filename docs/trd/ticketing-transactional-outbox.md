# Ticketing 예약 상태 이벤트 Transactional Outbox

## 문제

결제 완료 Consumer가 Reservation을 `CONFIRMED`로 바꾸고 Ticket을 `ISSUED`로 전환한 뒤 `SOLD_CONFIRMED`를 Kafka에 직접 발행하면 DB 트랜잭션과 Kafka 발행의 원자성을 보장할 수 없다. Kafka 발행 후 DB가 롤백되면 좌석 서비스에는 SOLD 이벤트가 전달됐지만 예약과 티켓은 결제 전 상태로 남을 수 있다. 반대로 DB 커밋 후 발행이 실패하면 결제된 좌석이 SOLD로 전환되지 않을 수 있다.

## 적용 범위

이번 단계는 결제 완료 뒤 생성되는 `SOLD_CONFIRMED`와 판매 취소 뒤 생성되는 `SALE_CANCELED`에 Transactional Outbox를 적용한다. 두 이벤트를 예약 번호를 messageKey로 사용하는 같은 Outbox에 기록해, 결제 직후 취소되더라도 SOLD가 먼저 발행 완료되기 전에는 판매 취소를 선택하지 않는다. 임시 선점 만료를 뜻하는 `HOLD_RELEASED`는 SOLD를 되돌리지 않고, 정상 판매 취소를 뜻하는 `SALE_CANCELED`만 같은 예약 소유의 SOLD를 AVAILABLE로 전환한다.

Relay는 Ticketing 애플리케이션이 한 인스턴스로 실행된다는 전제다. 여러 Pod에서 동시에 Relay를 실행할 때 필요한 DB claim 또는 `SKIP LOCKED` 기반 배타 처리는 이번 범위에 포함하지 않는다.

## 처리 흐름

```text
Kafka payment.booking
  -> PaymentConsumer
  -> BookingService @Transactional
     -> Reservation PENDING_PAYMENT/PENDING_DEPOSIT -> CONFIRMED
     -> Ticket PENDING -> ISSUED
     -> ticketing_outbox_events PENDING 저장
  -> DB commit

TicketingOutboxRelayScheduler (기본 3초 간격)
  -> 예약별 PENDING/FAILED 선두 조회
  -> DB 트랜잭션 밖에서 Kafka booking.ticketing 발행
  -> 짧은 DB 트랜잭션으로 성공: PUBLISHED / 실패: FAILED 및 retryCount 증가
```

Reservation, Ticket, Outbox는 같은 Ticketing DB 트랜잭션에서 변경된다. 따라서 Outbox 직전이나 직후에 런타임 예외가 발생하면 세 변경이 함께 롤백된다. Outbox 기록기는 기존 트랜잭션이 없으면 실패하도록 `MANDATORY` 전파 속성을 사용한다. Kafka 네트워크 호출은 결제 완료 및 Relay 상태 저장 트랜잭션 밖에서 Scheduler가 수행한다.

## 데이터와 순서

`ticketing_outbox_events`는 topic, messageKey, payload, eventType, status, retryCount를 저장한다. `SOLD_CONFIRMED`와 `SALE_CANCELED`의 messageKey는 예약 번호다.

Relay는 같은 topic·messageKey에서 ID가 가장 낮은 활성 이벤트만 선택한다. 앞선 이벤트가 `PENDING` 또는 `FAILED`이면 후속 이벤트를 선택하지 않으며, 서로 다른 예약은 독립적으로 처리한다. 신규 `PENDING` 배치를 `FAILED` 재시도 배치보다 먼저 전달한다.

## 멱등성과 전달 보장

동일 결제 이벤트를 순차 중복 소비하면 Reservation 상태 머신이 `ALREADY_APPLIED`를 반환하므로 Outbox를 다시 만들지 않는다. 동시에 같은 Reservation을 확정하면 `@Version` 낙관적 락으로 한 트랜잭션만 커밋되고, 패자가 만든 Outbox도 해당 트랜잭션과 함께 롤백된다.

Relay는 at-least-once 성격을 가진다. Kafka 발행에는 성공했지만 `PUBLISHED` DB 커밋 전에 프로세스가 종료되면 같은 이벤트가 재발행될 수 있다. 좌석 상태 Consumer는 이미 SOLD인 동일 예약의 `SOLD_CONFIRMED`를 의미상 멱등 처리해야 한다. 이번 구현은 Kafka exactly-once를 주장하지 않는다.

## 스케줄

- Relay: `ticketing.outbox.relay.fixed-delay-ms`, 기본 3,000ms
- PUBLISHED 정리: `ticketing.outbox.cleanup.cron`, 기본 매일 03:00
- 한 번의 상태별 조회 크기: 최대 100건

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

## 아직 검증하거나 주장하지 않는 내용

- 두 개 이상의 Ticketing 인스턴스에서 동일 Outbox를 중복 선택하지 않는다는 보장
- 실제 Kafka 장애, 브로커 재시작, 프로세스 강제 종료를 포함한 전달 통합 검증
- Kafka exactly-once 또는 이벤트가 물리적으로 한 번만 발행된다는 보장
- FAILED 이벤트의 시간 기반 backoff, 최대 재시도, DLT 및 운영자 redrive
- Outbox 적재량과 Relay 지연에 대한 부하 시험 수치
- 외부 결제 취소 성공 뒤 Ticketing DB 트랜잭션이 실패하는 부분 실패의 자동 복구. 결제 서비스는 동일 idempotency key의 중복 취소를 막지만, Ticketing이 자동으로 재시도하거나 보상하는 흐름은 아직 없다.

## 배포와 롤백 제약

Outbox 기록 코드와 Relay를 한 번에 처음 배포한 뒤 구 버전으로 롤백하면, 신 버전이 남긴 PENDING/FAILED 행을 구 버전이 처리하지 못한다. 실제 배포에서는 Relay와 테이블을 먼저 배포한 다음 이벤트 기록 경로를 전환하는 단계적 배포가 필요하다. 한 번에 배포한다면 롤백 전에 활성 Outbox를 모두 발행하고 비어 있는지 확인하는 drain 절차가 필요하다. 이 절차를 자동화하거나 배포 환경에서 검증한 상태는 아니다.

`SALE_CANCELED`는 새 이벤트 타입이므로 구 Consumer와 신 Producer가 공존하는 Rolling Update에서 바로 발행하면 안 된다. 구 Consumer는 알 수 없는 타입을 정상 반환하므로 메시지를 처리하지 않고 ACK할 수 있다. 다중 인스턴스 배포에서는 먼저 `SALE_CANCELED` 소비 지원만 배포하고 모든 구 Consumer가 제거된 것을 확인한 뒤, Outbox Relay와 생산 경로를 활성화해야 한다. 이번 단계의 실행 전제는 구·신 버전이 공존하지 않는 단일 인스턴스 교체이며, 이 배포 순서는 아직 Kubernetes에서 검증하지 않았다.

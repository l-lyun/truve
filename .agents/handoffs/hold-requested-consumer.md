# HOLD_REQUESTED Consumer 작업 인수인계

## 작업 브랜치

- 선행 PR 브랜치: `codex/seat-hold-saga-orchestrator`
- 현재 작업 브랜치: `codex/hold-requested-consumer`
- 현재 브랜치는 선행 PR 브랜치에서 분기한 stacked branch다.
- 선행 PR은 닫거나 병합하지 말고 그대로 유지한다.

다른 PC에서 다음 명령으로 이어간다.

```bash
git fetch origin
git switch codex/hold-requested-consumer
git pull --ff-only
```

## 현재까지 구현된 내용

1. `BookingConsumer`가 `HOLD_REQUESTED`를 받아 비동기 HOLD handler로 전달한다.
2. DB 트랜잭션 밖에서 Redis의 세션, fingerprint, 좌석별 `holdId` 소유권을 Lua로 한 번에 검증한다.
3. 성공 DB 트랜잭션에서 다음 작업을 함께 커밋한다.
   - 전체 좌석 `AVAILABLE -> HOLD`
   - Ticket snapshot 생성
   - Reservation `HOLD_PENDING -> PAYMENT_READY`
   - `booking.payment / CREATE` Outbox 저장
4. 좌석 하나라도 충돌하거나 낙관적 락 충돌이 발생하면 성공 트랜잭션 전체를 롤백한다.
5. 확정 가능한 업무 실패는 별도 `REQUIRES_NEW` 트랜잭션에서 `HOLD_FAILED` 또는 `EXPIRED`로 저장하고 Redis를 조건부 보상한다.
6. 동일 이벤트와 결제 이후 늦게 도착한 이벤트를 상태 머신과 Ticket 좌석 집합으로 멱등 처리한다.
7. `booking.ticketing` Consumer에만 최대 3회 고정 간격 재시도와 `booking.ticketing.dlt` 전송을 적용했다.
8. Consumer가 끝내 처리하지 못해 만료된 `HOLD_PENDING`은 주기적인 조건부 bulk update로 `EXPIRED` 처리한다.

## 2026-08-27 재개 후 검증

다른 PC에서 중단됐던 전체 테스트와 당시 남겨둔 실제 인프라 검증을 완료했다.

- `./gradlew :ticketing:test`: 224개 전체 통과
- 실제 Kafka container:
  - 일시 오류가 재시도 안에 성공하면 DLT가 생성되지 않음
  - 계속 실패하면 재시도 소진 뒤 `booking.ticketing.dlt`에 한 번 전달됨
  - 잘못된 JSON과 알 수 없는 event type은 재시도 없이 DLT로 전달됨
  - DLT 발행 실패 시 원본 Consumer offset이 커밋되지 않음
- 실제 MySQL:
  - stale `ScheduledSeat @Version` 충돌 발생 확인
  - 동일 JPA 트랜잭션에서 다중 좌석 중 한 좌석의 version이 충돌하면 나머지 좌석, Ticket, Reservation, Payment Outbox까지 전체 롤백되는 것을 확인

Kafka 검증은 DB·Redis·Feign을 띄우지 않는 최소 Spring Kafka context로 구성했고, MySQL 검증은 `mysql:8.4` Testcontainer를 사용한다.

## 현재 남겨둔 설계 한계

- Redis 소유권 검사와 DB 트랜잭션 시작 사이에는 짧은 TOCTOU 구간이 남는다. 강하게 해결하려면 후속 작업에서 fencing token을 도입한다.
- 이번 만료 정리는 DB 좌석을 아직 HOLD하지 않은 `HOLD_PENDING`만 처리한다.
- `PAYMENT_READY` 만료는 좌석 `HOLD -> AVAILABLE`, 결제 승인 경쟁, Redis 정리를 한 흐름으로 설계한 뒤 구현해야 한다.
- DLT 자동 redrive는 아직 없다. 실제 운영 전에는 DLT 적재 건수와 `HOLD_PENDING` 체류 시간 알람 및 수동 재처리 절차가 필요하다.

## 커밋 및 PR 순서

현재 브랜치의 커밋은 기능 경계별로 이미 나뉘어 있다. 추가 작업도 테스트와 구현 목적이 섞이지 않도록 별도 커밋으로 만든다.

권장 후속 커밋:

```text
test(ticketing): HOLD Consumer Kafka 복구 정책 검증
test(ticketing): 좌석 HOLD 낙관락 동시성 검증
```

검증이 끝나면 현재 브랜치를 원격에 푸시하고 stacked PR을 만든다.

- head: `codex/hold-requested-consumer`
- base: `codex/seat-hold-saga-orchestrator`
- 권장 제목: `feat(ticketing): HOLD_REQUESTED Consumer 구현`
- PR 본문에 선행 PR 의존 관계, 전체 테스트와 Kafka/MySQL 실제 검증 결과, Redis-DB TOCTOU 한계를 명시한다.

선행 PR이 먼저 병합된 뒤에만 이 PR의 base를 기본 개발 브랜치로 변경한다.

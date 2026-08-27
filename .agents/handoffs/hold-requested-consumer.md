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

## 먼저 실행할 검증

관련 테스트는 통과했지만 다른 PC로 이동하기 직전 전체 테스트가 중단됐다. 코드를 수정하기 전에 아래 전체 검증부터 다시 실행한다.

Windows:

```powershell
.\gradlew.bat :ticketing:test
```

Unix 계열:

```bash
./gradlew :ticketing:test
```

실패하면 이번 브랜치에서 추가된 다음 영역부터 확인한다.

- `BookingConsumerKafkaConfig`의 Spring context 생성
- `BookingConsumerKafkaProperties` 설정 바인딩
- `HoldPendingExpirationScheduler`의 예약 bulk update
- 결제 이후 상태의 중복 `HOLD_REQUESTED` 처리

## 리뷰 전에 추가할 검증

이번 브랜치의 핵심 단위·JPA·Redis 테스트는 작성되어 있다. 다음 두 검증은 아직 남아 있다.

1. 실제 Kafka container 검증
   - 일시 오류가 정해진 횟수 안에 성공하면 DLT가 생기지 않는지
   - 계속 실패하면 `booking.ticketing.dlt`에 한 번 전달되는지
   - 잘못된 JSON과 알 수 없는 event type은 재시도 없이 DLT로 가는지
   - DLT 발행 실패 시 원본 메시지가 정상 ACK되지 않는지
2. 실제 MySQL 동시성 검증
   - 서로 다른 두 트랜잭션이 같은 `ScheduledSeat` version을 수정하면 하나만 성공하는지
   - 다중 좌석 중 한 좌석이 충돌할 때 나머지 좌석, Ticket, Reservation, Outbox가 모두 롤백되는지

실제 Kafka 검증을 이번 PR에 넣는다면 `ticketing/build.gradle`에 `spring-kafka-test` 테스트 의존성을 추가하고 최소 test context로 작성한다. 전체 애플리케이션과 MySQL·Redis를 함께 띄우는 방식은 피한다.

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
- PR 본문에 선행 PR 의존 관계, 전체 테스트 결과, Kafka/MySQL 미검증 여부, Redis-DB TOCTOU 한계를 명시한다.

선행 PR이 먼저 병합된 뒤에만 이 PR의 base를 기본 개발 브랜치로 변경한다.

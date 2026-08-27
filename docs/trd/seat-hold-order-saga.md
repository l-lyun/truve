# Redis 좌석 선점과 비동기 주문 생성 Saga

- 상태: In Progress
- 구현 여부: PR 1 기반 모델·이벤트 계약, PR 2 Redis lease·주문 접수 Saga, PR 3 비동기 DB HOLD·Ticket·Payment Outbox 구현. 취소·만료 스케줄러와 결제 UX 전환은 후속 PR 예정
- 선행 문서: `seat-hold-concurrency.md`
- 영향 범위: `ticketing`, Redis, Kafka, Ticketing DB, 결제 진입 UX

## 배경

현재 좌석 선택 API는 Redis에만 임시 선점을 기록하고, 사용자가 별도의 예매 생성 API를 호출해야 `Reservation`, `Ticket`, DB 좌석 `HOLD`가 생성된다. 이 구조에서는 Redis 선점에 성공했지만 주문이 없는 상태가 정상 흐름에서도 존재한다.

목표 구조는 Redis로 좌석 경쟁을 먼저 제어한 뒤 같은 `hold()` 요청에서 주문 접수 상태와 Outbox를 저장하고, DB 좌석 `HOLD`와 Ticket 생성은 Kafka Consumer가 비동기로 처리하는 것이다. 사용자는 주문 접수 직후 결제수단 선택 화면에 진입할 수 있고, DB 좌석 `HOLD`가 완료된 주문은 만료 전까지 같은 예약 번호로 결제를 다시 시도할 수 있어야 한다.

## 목표

- Redis Lua로 여러 좌석 선점을 전부 성공하거나 전부 실패하도록 처리한다.
- 동일 사용자·회차의 동시 `hold()` 요청을 짧은 Redis 요청 락으로 직렬화한다.
- `hold()`를 비트랜잭션 오케스트레이터로 두고 Redis 선점과 DB 주문 접수·Outbox 저장을 두 로컬 트랜잭션으로 조율한다.
- DB 주문 접수 또는 Outbox 저장 실패 시 새로 획득한 Redis 선점만 소유권을 확인해 보상 해제한다.
- Outbox Relay와 Kafka Consumer를 통해 DB 좌석을 비동기로 `AVAILABLE -> HOLD` 전환한다.
- DB 좌석 반영 시 `ScheduledSeat @Version` 낙관적 락을 최종 정합성 방어선으로 사용한다.
- 결제 화면 진입과 실제 결제 가능 시점을 분리하고, `PAYMENT_READY` 주문은 만료 전까지 재결제를 허용한다.

## 비목표와 수용하는 한계

- Redis와 MySQL을 XA/2PC 분산 트랜잭션으로 묶지 않는다.
- Kafka exactly-once를 주장하지 않는다.
- Redis 선점 직후 프로세스가 종료되는 원자성 공백은 완전히 제거하지 않고, 멱등 재시도와 Redis TTL로 수렴시킨다.
- 프로세스 종료 공백에서 orphan 좌석이 유지될 수 있는 최대 시간은 Redis 좌석 lease TTL이다.

## 시간 경계

| 대상 | 기본 시간 | 역할 |
|---|---:|---|
| 사용자·회차 요청 락 | 10초 | 동일 사용자의 다중 탭·중복 `hold()` 실행을 짧게 직렬화하는 뮤텍스 |
| Redis 좌석 lease | 10분 | 주문 접수와 비동기 DB `HOLD` 처리 동안 좌석의 임시 소유권 유지 |
| 세션별 좌석 Set | 11분 | 세션 누적 최대 4석 제한과 일괄 정리를 위한 보조 인덱스 |
| 주문 결제 기한 | `expiresAt` | DB `HOLD` 주문이 결제 가능한 절대 만료 시각 |

10초는 좌석 보유 시간이나 Kafka 이벤트 처리 제한 시간이 아니다. 요청 락은 정상적으로 `Reservation(HOLD_PENDING)`과 Outbox가 커밋되면 즉시 compare-and-delete로 해제한다. Kafka 전달이 10초를 넘더라도 Redis 좌석 lease와 주문 `expiresAt`이 유효하면 처리할 수 있다.

## 목표 처리 흐름

```text
Client hold 요청
  -> Idempotency-Key로 holdId 생성, 좌석 fingerprint 계산
  -> 사용자·회차 Redis 요청 락 획득
  -> Redis Lua 좌석 선점
  -> [짧은 Ticketing DB 트랜잭션]
     -> Reservation HOLD_PENDING 저장
     -> HOLD_REQUESTED Outbox 저장
  -> DB 실패 시 새 Redis 선점 compare-and-delete 보상
  -> 사용자 요청 락 compare-and-delete 해제
  -> HOLD_ACCEPTED + reservationNumber 응답

Ticketing Outbox Relay
  -> HOLD_REQUESTED를 Kafka에 at-least-once 발행

Hold Consumer
  -> holdId 멱등성 확인
  -> [Ticketing DB 트랜잭션]
     -> ScheduledSeat 조회 및 상태 검증
     -> ScheduledSeat @Version 조건으로 AVAILABLE -> HOLD
     -> Ticket 생성
     -> Reservation HOLD_PENDING -> PAYMENT_READY
     -> Payment 생성 요청 Outbox 저장
  -> 충돌·만료 시 HOLD_FAILED/EXPIRED 기록과 Redis 보상

Payment
  -> PAYMENT_READY 주문으로 결제 시도 생성
  -> 실패하면 같은 Reservation으로 새 결제 시도 허용
  -> 성공하면 Reservation CONFIRMED, Ticket ISSUED, 좌석 HOLD -> SOLD
```

## Redis 데이터 모델

```text
seat:hold:lock:{showScheduleId}:{userId} -> requestToken
seat:hold:{showScheduleId}:{scheduledSeatId} -> holdId
seat:holds:{showScheduleId}:{sessionToken} -> Set<scheduledSeatId>
seat:hold:meta:{holdId} -> Hash(sessionToken, seatFingerprint)
```

- 요청 락은 같은 사용자·회차의 동시에 실행되는 `hold()`를 빠르게 거절하는 완충 장치다.
- 좌석 키의 값은 `sessionToken` 대신 Saga 전체의 소유권 식별자인 `holdId`를 사용한다.
- 세션 Set은 여러 요청을 합산한 최대 4석 제한과 퇴장·만료 정리를 지원한다.
- `holdId`는 Redis 선점, Reservation, 로그·추적 식별자와 보상 조건에 동일하게 사용한다.
- `holdId`는 `userId + showScheduleId + Idempotency-Key`로 결정해 요청 본문이 달라도 같은 키가 같은 작업을 가리키게 한다.
- 정렬한 좌석 ID의 fingerprint를 Reservation에 저장하고, 같은 멱등 키로 다른 좌석을 요청하면 충돌로 거절한다.
- 해제와 보상은 현재 값이 자신의 `requestToken` 또는 `holdId`와 일치할 때만 수행한다.

최초 선점 Lua는 stale Set 멤버를 좌석 소유권 키와 대조해 제거하고, 기존 좌석과 신규 좌석의 합집합이 4개 이하인지 검사한다. 요청 좌석 중 하나라도 다른 `holdId` 소유라면 어떤 좌석도 새로 만들지 않는다.

Lua 결과는 최소한 다음을 구분한다.

```text
NEWLY_ACQUIRED  새 holdId가 좌석을 획득
ALREADY_OWNED   같은 holdId의 멱등 재시도
CONFLICT        다른 holdId가 좌석 소유
LIMIT_EXCEEDED  세션 누적 최대 좌석 수 초과
```

## `hold()` 오케스트레이션 Saga

`hold()` 전체에 하나의 `@Transactional`을 적용하지 않는다. Redis Lua와 MySQL 트랜잭션은 서로 다른 로컬 트랜잭션이며, MySQL 롤백이 Redis 쓰기를 자동으로 롤백하지 않는다.

```text
T1: Redis Lua
  -> 좌석 lease와 세션 Set 원자 변경

T2: Ticketing DB 트랜잭션
  -> Reservation(HOLD_PENDING)
  -> Outbox(HOLD_REQUESTED)

T2 실패
  -> T1에서 NEWLY_ACQUIRED한 좌석만 holdId compare-and-delete 보상
```

DB 커밋 예외를 오케스트레이터가 감지할 수 있도록 T2는 별도 Spring Bean 또는 `TransactionTemplate` 경계에서 실행한다. 같은 클래스의 내부 `@Transactional` 호출에 의존하지 않는다.

보상 여부를 단순 메모리 boolean만으로 판단하지 않는다. Lua가 `NEWLY_ACQUIRED`와 `ALREADY_OWNED`를 구분해 반환하고, 새로 획득한 요청만 DB 실패 시 보상한다. 같은 `holdId` 재시도의 DB 오류에서 기존 Redis 선점을 지우면 먼저 실행된 요청의 정상 Outbox를 방해할 수 있다.

## 주문 접수와 Outbox

동기 DB 트랜잭션은 좌석 행을 변경하지 않고 다음 두 레코드를 함께 저장한다.

```text
Reservation
  holdId         UNIQUE
  holdRequestFingerprint
  number         UNIQUE
  userId
  showScheduleId
  status         HOLD_PENDING
  expiresAt

Outbox
  eventId        UNIQUE
  aggregateId    reservationNumber
  messageKey     reservationNumber
  eventType      HOLD_REQUESTED
  payload        holdId, reservationNumber, userId, sessionToken, showScheduleId, scheduledSeatIds, expiresAt
  status         PENDING
```

성공 응답은 이 DB 트랜잭션이 커밋된 뒤에만 반환한다. 응답이 유실되어 같은 idempotency key로 재시도되면 기존 `holdId`, Reservation, Outbox를 조회해 같은 예약 번호를 반환한다.
같은 키를 다른 좌석 목록과 함께 재사용하면 저장된 `holdRequestFingerprint`가 일치하지 않으므로 `INVALID_BOOKING_SEAT_HOLD`로 거절한다. Redis만 성공하고 DB 저장 전 종료된 경우에도 고정된 `holdId` 때문에 다른 좌석 목록을 추가로 선점할 수 없다.

`ALREADY_OWNED` 재시도에서 주문이 아직 없다면 새 10분을 부여하지 않고 Redis meta key의 남은 TTL로 `expiresAt`을 정한다. DB 커밋 결과 재조회까지 실패해 커밋 여부가 불확실하면 Redis lease를 지우지 않고 TTL에 맡긴다.

기존 Ticketing Outbox의 claim, retry, `PROCESSING`, `claimToken`, `claimedAt` 원칙을 재사용한다. Kafka messageKey는 모든 예약 생명주기 이벤트에서 `reservationNumber`로 고정해 같은 주문의 순서를 유지한다. `holdId`는 payload에 포함해 Redis 보상과 멱등성 확인에 사용한다.

## 비동기 DB `HOLD`와 낙관적 락

`HOLD_REQUESTED` Consumer는 요청 좌석 전체를 하나의 DB 트랜잭션에서 처리한다. 신규 Consumer 경로는 비관적 락 없는 조회와 `ScheduledSeat @Version`을 사용한다. 기존 동기 예매 생성 경로의 `PESSIMISTIC_WRITE`는 클라이언트 전환 전까지 유지한다.

```sql
UPDATE scheduled_seats
SET status = 'HOLD',
    reservation_number = :reservationNumber,
    version = version + 1
WHERE id = :scheduledSeatId
  AND version = :expectedVersion;
```

- 모든 좌석이 존재하고 같은 회차이며 `AVAILABLE`인지 먼저 검증한다.
- 좌석 하나의 version 충돌도 전체 트랜잭션을 롤백시켜 부분 `HOLD`를 허용하지 않는다.
- 낙관적 충돌 뒤 재조회했을 때 같은 `reservationNumber` 또는 `holdId` 소유면 멱등 성공으로 종료한다.
- 다른 소유자라면 무조건 재시도하지 않고 `HOLD_FAILED`로 확정한 뒤 Redis를 보상한다.
- 인프라성 일시 오류만 제한 횟수 재시도하고, 초과 시 DLT 또는 운영자 재처리 대상으로 보낸다.
- `holdId` unique 제약과 Reservation 상태 머신으로 동일 이벤트의 순차 중복을 의미상 멱등 처리한다.
- 처리 eventId 이력이 필요해지면 별도 Inbox를 추가한다.
- 현재 시각이 `expiresAt`을 지났거나 Redis 좌석 소유권이 event의 `holdId`와 다르면 늦은 이벤트를 적용하지 않는다.

성공 Consumer 트랜잭션에는 `ScheduledSeat HOLD`, Ticket 생성, `Reservation PAYMENT_READY`, `booking.payment / CREATE` Outbox를 함께 기록한다. 동일 이벤트 재처리는 `PAYMENT_READY`와 좌석·Ticket 집합을 확인해 멱등 성공하며 Payment Outbox를 중복 생성하지 않는다. DB `HOLD`가 완료된 뒤에는 DB가 최종 좌석 소유권의 기준이지만 Redis 좌석 lease도 `expiresAt`, 명시적 해제 또는 판매 완료까지 유지한다. DB 커밋 뒤 즉시 정리하는 것은 10초 요청 락뿐이다.

실패 상태 저장은 원래 실패해 롤백된 Consumer 트랜잭션과 분리된 `REQUIRES_NEW` 로컬 트랜잭션에서 처리한다. `HOLD_FAILED` 또는 `EXPIRED`와 `blockBooking=null`이 커밋된 뒤에만 Redis lease를 compare-and-delete로 보상한다. DB·Redis 일시 오류나 커밋 여부 불확실 상태는 실패로 확정하거나 보상하지 않고 Kafka 예외 전파로 재처리한다.

## 결제 UX와 재결제

`HOLD_ACCEPTED`는 좌석 경쟁에서 승리했고 주문 접수가 영속화됐다는 의미다. 아직 실제 결제가 가능한 상태라는 의미는 아니다.

```text
HOLD_PENDING   주문 접수 완료, DB 좌석 HOLD 처리 중
PAYMENT_READY  DB 좌석 HOLD 완료, 결제 가능
HOLD_FAILED    DB 좌석 반영 실패
EXPIRED        결제 기한 만료
CONFIRMED      결제 완료
```

hold API 응답은 `reservationNumber`, `status=HOLD_PENDING`, `expiresAt`을 반환한다. 클라이언트는 응답 직후 결제수단 선택 화면에 진입할 수 있지만, 승인 API는 `PAYMENT_READY` 주문만 허용한다. 정상 상황에서는 사용자가 결제수단을 선택하는 동안 비동기 DB 처리가 끝나는 것을 기대하지만, UX와 정합성은 특정 처리 시간에 의존하지 않는다.

`PAYMENT_READY`이고 DB 좌석이 해당 Reservation의 `HOLD`이며 현재 시각이 `expiresAt` 이전이면 사용자가 화면을 나갔다 돌아와도 같은 Reservation으로 다시 결제할 수 있다. Reservation 식별자는 유지하고 결제 시도마다 별도의 `paymentAttemptId`를 발급한다. 결제 실패는 주문을 즉시 만료시키지 않고 다시 `PAYMENT_READY`로 전이한다. 후속 결제 연동 PR에서는 `HOLD_PENDING -> PAYMENT_READY` 직전과 `PAYMENT_READY -> PENDING_PAYMENT` 결제 시작 직전에 만료 시각과 좌석 소유권을 트랜잭션 안에서 재검증한다.

## 만료 Saga

Redis 좌석 lease와 Reservation `expiresAt`은 같은 결제 기한을 기준으로 한다. Redis TTL만으로 DB `HOLD`를 해제할 수 없으므로 만료 처리가 필수다.

```text
expiresAt 경과
  -> Reservation HOLD_PENDING/PAYMENT_READY -> EXPIRED
  -> 동일 reservationNumber 소유의 ScheduledSeat HOLD -> AVAILABLE
  -> Redis 값이 holdId와 같으면 선점 키와 세션 Set 정리
```

만료와 결제 완료가 경쟁하면 Reservation version·상태 전이와 ScheduledSeat 소유권·version 검증으로 한쪽만 승리하게 한다. 결제 승인 시작 후 만료 허용 여부와 만료 Scheduler의 다중 인스턴스 claim 방식은 구현 계획에서 확정한다.

## 최종 락과 제약 구조

| 구간 | 장치 | 결정 |
|---|---|---|
| 동일 사용자·회차 중복 `hold()` | Redis 요청 락, TTL 10초 | 유지 |
| 서로 다른 사용자의 좌석 경쟁 | Redis Lua 좌석 lease, TTL 10분 | 유지, 소유권 값을 `holdId`로 변경 |
| 세션 누적 최대 4석 | Redis Set + Lua | 유지 |
| Redis와 주문 접수 연결 | `hold()` Saga + 보상 | 신규 |
| 주문 접수와 Kafka 발행 | Reservation + RDB Outbox 한 DB 트랜잭션 | 신규 |
| 최초 DB 좌석 `AVAILABLE -> HOLD` | `ScheduledSeat @Version` 낙관적 락 | 기존 비관적 락 대체 |
| 중복 활성 주문 | DB unique 제약 | 유지 |
| 별도 예매 생성 요청 직렬화 | `booking:lock:{userId}:{showScheduleId}` | 별도 예매 생성 API 제거 시 제거 |
| Redis booking claim | `booking:{sessionToken}:...` | `holdId` 소유권으로 통합하여 제거 |
| 결제 결과 Reservation 변경 | `Reservation @Version` | 유지 |
| Payment 승인·취소 | Payment 비관적 락 | 유지. 외부 PG 호출 중 장기 보유 위험은 별도 개선 과제 |
| 결제 완료·판매 취소 좌석 전이 | 현재 소유권 검사 + DB 락 | 기존 이벤트 TRD와 함께 구현 시 확정 |

## 장애 시나리오

| 장애 시점 | 상태 | 복구 |
|---|---|---|
| Redis 선점 실패 | Redis/DB 모두 미변경 | 충돌 또는 제한 오류 반환 |
| Redis 성공, DB 트랜잭션 실패 | Redis만 선점 | `NEWLY_ACQUIRED` 좌석 compare-and-delete 보상 |
| Redis 성공 직후 프로세스 종료 | Redis만 선점, Outbox 없음 | 성공 응답 없음. 동일 idempotency key 재시도 또는 Redis TTL 만료 |
| DB 커밋 후 응답 유실 | Reservation/Outbox 존재 | 동일 idempotency key로 기존 예약 반환 |
| Kafka/Relay 지연 | HOLD_PENDING, Redis lease 존재 | Outbox 재시도. 10초 요청 락 TTL과 무관 |
| 동일 이벤트 중복 전달 | 같은 holdId 재처리 | unique 제약과 상태 머신으로 멱등 종료 |
| Consumer 낙관적 락 충돌 | DB HOLD 트랜잭션 롤백 | 같은 소유면 멱등 성공, 다른 소유면 HOLD_FAILED와 Redis 보상 |
| 만료 뒤 늦은 이벤트 | Redis 소유권 없음 또는 expiresAt 경과 | DB 좌석을 `HOLD`로 되살리지 않고 EXPIRED/HOLD_FAILED 처리 |
| DB HOLD 성공 후 Redis 정리 실패 | DB가 좌석 소유권 기준 | TTL과 정합성 점검 작업으로 Redis stale 상태 제거 |

## 후속 구현과 통합 조건

- PR 3에서 `BookingConsumer`의 `HOLD_REQUESTED` 라우팅과 처리 경로를 추가해 이벤트 정상 ACK 유실 문제를 해소한다. 이 Consumer에만 최대 3회 고정 간격 재시도와 `booking.ticketing.dlt`를 적용하고, 최종 실패로 만료 시각을 넘긴 `HOLD_PENDING`은 정합성 작업이 `EXPIRED`로 수렴시킨다. 실제 Kafka 컨테이너 기반 retry·DLT 검증과 DLT redrive는 후속 운영 과제다.
- 새 흐름은 hold 응답의 `reservationNumber` 자체를 주문으로 사용한다. 기존 `POST /api/bookings`를 이어서 호출하면 활성 `HOLD_PENDING` 주문 제약으로 실패하므로 클라이언트 전환은 PR 3 통합 완료와 함께 진행한다.
- 기존 좌석 반납 API는 sessionToken 소유권 모델이어서 holdId lease를 해제하지 못한다. Reservation 상태와 Outbox, Redis compare-and-delete를 조율하는 취소 Saga가 완성되기 전에는 새 흐름의 취소 API로 사용하지 않는다.
- `HOLD_PENDING` 생성 시 가격·등급·공연 스냅샷 중 공연·등급 요약은 동기 접수에서 저장하고, 좌석별 가격과 Ticket은 Consumer에서 확정한다.
- 주문 상태 조회를 polling, SSE 또는 WebSocket 중 어떤 방식으로 제공할지 결정한다.
- 결제 시도 엔티티와 실패 후 `PAYMENT_READY` 복귀 규칙을 설계한다.
- `PAYMENT_READY` 만료 시 좌석 해제와 결제 승인 경쟁 정책을 설계한다. 이번 `HOLD_PENDING` 만료 정리는 조건부 bulk update라 여러 인스턴스에서도 중복 상태 전이가 발생하지 않는다.
- 현재 개발 단계에서는 별도 migration script 없이 JPA 엔티티 컬럼만 변경한다. 실제 배포 전에는 `ScheduledSeat.version`, Reservation 신규 컬럼과 상태 값의 migration 전략을 별도로 확정한다.

## 관측성

로그와 trace에는 `holdId`와 `reservationNumber`를 공통으로 남기고 Kafka messageKey는 `reservationNumber`를 사용한다. 다음 지표와 알람이 필요하다.

- Redis 선점은 존재하지만 Reservation/Outbox가 없는 orphan hold 수와 최대 age
- `HOLD_PENDING` 건수와 최대 체류 시간
- ScheduledSeat 낙관적 락 충돌 수
- Redis 보상 실패 수
- Outbox lag, retry, 영구 실패/DLT 수
- 만료 처리 지연과 만료 후 남아 있는 DB `HOLD` 수

## 검증 계획

- 동일 사용자·회차 동시 `hold()` 중 하나만 요청 락을 획득하는지 검증한다.
- 서로 다른 사용자가 같은 좌석을 동시에 요청하면 Redis Lua에서 하나만 성공하는지 검증한다.
- 여러 좌석 중 하나가 충돌하면 어떤 좌석도 추가되지 않는지 검증한다.
- Redis `NEWLY_ACQUIRED` 뒤 DB Outbox 저장이 실패하면 해당 holdId 소유 좌석만 보상되는지 검증한다.
- `ALREADY_OWNED` 재시도의 DB 오류가 기존 Redis 선점을 지우지 않는지 검증한다.
- 동일 idempotency key 재요청이 기존 Reservation과 예약 번호를 반환하는지 검증한다.
- Reservation과 `HOLD_REQUESTED` Outbox가 함께 커밋·롤백되는지 검증한다.
- 같은 `HOLD_REQUESTED`를 중복 소비해도 Reservation, Ticket, 좌석 상태가 한 번만 변경되는지 검증한다.
- 실제 MySQL에서 같은 ScheduledSeat version을 동시에 변경하면 하나만 커밋되고 다른 트랜잭션 전체가 롤백되는지 검증한다.
- Kafka 처리가 10초를 넘겨도 유효한 좌석 lease와 `expiresAt` 안에서는 정상 처리되는지 검증한다.
- Redis lease 만료 또는 소유권 변경 뒤 도착한 이벤트가 DB 좌석을 `HOLD`로 되살리지 않는지 검증한다.
- `PAYMENT_READY` 주문이 첫 결제 실패 뒤 같은 Reservation으로 다시 결제 가능한지 검증한다.
- 결제 완료와 만료가 동시에 실행돼 좌석이 `SOLD`에서 `AVAILABLE`로 되돌아가지 않는지 검증한다.

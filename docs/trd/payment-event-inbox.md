# 결제 이벤트 Inbox와 예약 상태 전이

## 문제

Payment는 결제 결과와 Outbox 레코드를 같은 트랜잭션에 기록하지만 발행 이벤트에 고유 식별자가 없다. Ticketing Consumer는 메시지를 수신하면 즉시 Reservation을 변경하므로 Kafka 재전달 시 Ticket 발급을 다시 실행하고, 서로 다른 이벤트가 동시에 같은 Reservation을 갱신해도 충돌을 감지하지 못한다.

## 이벤트 식별

Payment Outbox 레코드는 생성 시 UUID `eventId`를 갖는다. Relay 재시도는 같은 레코드의 같은 `eventId`를 `event-id` Kafka 헤더에 전달한다. `reservationNumber`는 변경할 Aggregate를 찾는 식별자이고, `eventId`는 전달 메시지의 중복을 식별한다.

Relay는 `PENDING`과 `FAILED` 레코드를 ID 순서로 최대 100건 조회하므로 일시적인 Kafka 발행 실패를 다시 시도한다. 한 이벤트가 실패하면 같은 `reservationNumber`를 key로 가진 뒤 이벤트는 이번 실행에서 발행하지 않아 단일 Relay 내 인과 순서 역전을 줄인다. 다만 이번 단계에는 backoff·최대 재시도·다중 Pod 간 선점이 없으므로, 여러 인스턴스에서의 중복·순서 역전 가능성은 완전히 제거되지 않는다. Inbox는 중복을 방어하지만 이벤트 인과 순서와 Relay 운영 안전성은 후속 다중 인스턴스 Outbox Relay 단계에서 완성한다.

## Ticketing Inbox

```text
ticketing_inbox_events
- id
- event_id       UNIQUE NOT NULL
- topic          NOT NULL
- event_type     NOT NULL
- aggregate_id   NOT NULL
- processed_at   NOT NULL
- created_at
- updated_at
```

Consumer는 헤더와 payload를 변환한 뒤 트랜잭션 Handler를 호출한다. Handler는 Inbox를 저장하고 Reservation 상태 전이와 Ticket 발급을 같은 트랜잭션에서 수행한다. 비즈니스 처리 실패 시 Inbox도 롤백되어 Kafka 재처리가 가능하다. 동일 eventId가 여러 인스턴스에서 동시에 처리되면 DB UNIQUE 제약이 최종 승자를 결정한다.

## 상태 기반 멱등성과 낙관적 락

Inbox는 같은 eventId를 차단하고 Reservation 상태 머신은 같은 의미를 가진 서로 다른 eventId를 차단한다. Reservation의 `@Version`은 서로 다른 이벤트가 같은 버전을 동시에 갱신하는 것을 감지한다.

```text
카드 결제: PENDING_PAYMENT -> CONFIRMED, Ticket PENDING -> ISSUED
가상계좌 발급: PENDING_PAYMENT -> PENDING_DEPOSIT, Ticket PENDING 유지
입금 완료: PENDING_DEPOSIT -> CONFIRMED, Ticket PENDING -> ISSUED
```

이미 목표 상태에 도달한 의미상 중복 이벤트는 상태·티켓·후속 이벤트를 다시 변경하지 않는다. 취소·완료 등 반대 최종 상태는 결제 이벤트가 덮어쓰지 못한다.

## 트랜잭션과 실패 처리

- 동일 eventId가 이미 커밋됨: 비즈니스 로직 없이 정상 종료한다.
- 동일 eventId가 동시에 삽입됨: UNIQUE 충돌 트랜잭션을 롤백하고 중복으로 종료한다.
- 상태 전이 또는 Ticket 발급 실패: Inbox까지 롤백하고 예외를 Kafka에 전파한다.
- 낙관적 락 충돌: Inbox까지 롤백하고 재처리 시 최신 상태 머신으로 다시 판단한다.

## 이번 범위의 한계

Ticketing이 `SOLD_CONFIRMED`를 직접 Kafka에 발행하는 현재 구조는 이번 변경에서 유지한다. 따라서 결제 이벤트 수신의 멱등성과 DB 상태 전이는 보강되지만, Ticketing DB 변경과 후속 이벤트 발행의 원자성은 다음 Transactional Outbox 작업에서 완성한다.

또한 기존 Outbox 레코드에 대한 `event_id` 백필과 구·신 버전이 동시에 실행되는 롤링 배포 호환성은 이번 개발 환경 범위에 포함하지 않는다.

Ticketing Consumer의 장기 장애에 대한 retry topic·DLT·수동 redrive 정책과 실제 Kafka 재전달 통합 테스트도 후속 범위다. 따라서 이번 단계에서 검증한 것은 Handler 재호출 시의 DB 멱등성과 롤백이며, Kafka 장애 후 최종 전달 보장까지 주장하지 않는다.

# 결제 이벤트 소비와 예약 상태 전이 멱등성

## 문제

Ticketing Consumer는 Kafka 메시지를 받을 때마다 Reservation과 Ticket을 변경한다. 같은 결제 이벤트가 다시 전달되면 Ticket 발급과 `SOLD_CONFIRMED` 발행이 반복될 수 있고, 서로 다른 이벤트가 동시에 같은 Reservation을 변경하면 마지막 커밋이 앞선 변경을 덮어쓸 수 있다.

이번 단계는 별도 Inbox 테이블을 두지 않는다. Consumer가 기존 BookingService를 호출하고, Reservation 상태 머신과 JPA 낙관적 락으로 비즈니스 상태의 멱등성과 동시 변경 충돌을 처리한다.

Payment Outbox Relay는 `PENDING`과 `FAILED` 이벤트를 재시도하되, 같은 topic·messageKey의 아직 발행되지 않은 이벤트 중 가장 오래된 한 건만 발행 대상으로 선택한다. 따라서 가상계좌 발급 `CONFIRMED`가 실패하면 뒤의 `DEPOSIT_RECEIVED`는 선행 이벤트가 `PUBLISHED`가 될 때까지 선택되지 않는다.

## 처리 흐름

```text
Kafka payment.booking
  -> PaymentConsumer payload 변환
  -> BookingService @Transactional
  -> Reservation 상태 전이 검증
  -> Ticket 상태 전이
  -> Reservation @Version 확인 후 DB commit
```

상태 전이는 다음과 같다.

```text
카드 결제: PENDING_PAYMENT -> CONFIRMED, Ticket PENDING -> ISSUED
가상계좌 발급: PENDING_PAYMENT -> PENDING_DEPOSIT, Ticket PENDING 유지
입금 완료: PENDING_DEPOSIT -> CONFIRMED, Ticket PENDING -> ISSUED
```

이미 목표 상태에 도달한 이벤트가 다시 들어오면 현재 Ticket 상태가 목표 상태와 일치하는지 확인한 뒤 `ALREADY_APPLIED`로 종료한다. 이때 Ticket을 다시 발급하거나 `SOLD_CONFIRMED`를 다시 발행하지 않는다. 취소·부분 취소·완료 예약에는 늦은 결제 이벤트가 상태를 덮어쓰지 않으며 경고 로그를 남긴다.

## 낙관적 락과 재처리

Reservation의 `@Version`은 두 트랜잭션이 같은 버전을 읽고 동시에 변경할 때 하나의 커밋만 허용한다. 패자는 `OptimisticLockException` 계열 예외로 롤백된다. Kafka가 해당 레코드를 다시 전달하면 최신 Reservation 상태를 읽고 상태 머신이 이미 반영된 이벤트를 `ALREADY_APPLIED`로 종료한다.

이 구조가 보장하는 것은 이벤트 ID 단위의 exactly-once가 아니라 Reservation의 최종 비즈니스 상태에 대한 멱등성이다. 별도 Inbox가 없으므로 어떤 eventId가 처리됐는지 이력을 저장하거나, 같은 비즈니스 결과를 가진 서로 다른 메시지를 구분하지 않는다.

## Outbox 재시도와 실패 격리

- 신규 `PENDING` 선두와 재시도할 `FAILED` 선두를 각각 최대 100건 조회하고 PENDING 배치를 먼저 발행해 영구 실패 이벤트가 신규 이벤트의 선정과 실행을 지연시키지 않게 한다.
- 같은 topic·messageKey에서는 ID가 더 낮은 `PENDING` 또는 `FAILED` 이벤트가 남아 있으면 후속 이벤트를 조회하지 않는다.
- 한 이벤트 발행이 실패하면 현재 실행에서 같은 topic·messageKey의 후속 이벤트만 건너뛰고 다른 예약·토픽 이벤트는 계속 발행한다.
- `FAILED` 배치는 retryCount가 낮은 순서로 조회해 한 영구 실패 이벤트만 반복 선택되는 현상을 줄인다.
- 선행 이벤트가 성공해 `PUBLISHED`로 커밋된 다음 실행부터 후속 이벤트가 새로운 선두로 선택된다.

## 검증 범위

- 카드 결제 이벤트를 한 트랜잭션씩 순차 재호출할 때 Ticket과 SOLD 이벤트가 한 번만 변경·발행되는지 검증한다.
- 가상계좌 발급 이벤트를 순차 재호출할 때 Ticket이 PENDING으로 유지되는지 검증한다.
- 입금 완료 이벤트를 순차 재호출할 때 Ticket과 SOLD 이벤트가 한 번만 변경·발행되는지 검증한다.
- 커밋된 카드 결제를 실제 MySQL의 새 트랜잭션에서 다시 읽어 처리할 때 `ALREADY_APPLIED`로 종료하고 Reservation 버전을 올리지 않는지 검증한다.
- 취소된 Reservation에 늦은 결제 완료 이벤트가 와도 상태를 복구하지 않는지 검증한다.
- 실제 MySQL에서 Reservation 엔티티를 동시에 확정할 때 하나만 커밋되고 다른 트랜잭션은 낙관적 락으로 실패하는지 검증한다. 이 테스트는 Consumer 재전달이나 Kafka 발행 횟수를 검증하지 않는다.
- 실패한 Outbox 선행 이벤트가 같은 예약의 후속 이벤트를 차단하지만 다른 예약·토픽은 차단하지 않는지 검증한다.
- 선행 Outbox 재시도가 성공한 뒤 후속 이벤트가 ID 순서로 발행되는지 검증한다.
- FAILED 이벤트가 100건 있어도 신규 PENDING 배치를 별도로 조회하는지 검증한다.

## 이번 범위의 한계

- Kafka retry topic·DLT·redrive 정책과 실제 Kafka 재전달 통합 테스트가 아직 없다. 따라서 낙관적 락 패자가 실제 운영에서 최종 성공할 때까지 재전달된다고 아직 주장할 수 없다.
- 현재 Ticketing의 `SOLD_CONFIRMED`는 DB 커밋 전에 Kafka로 직접 발행된다. 동시 중복 요청은 양쪽 모두 SOLD를 발행한 뒤 낙관적 락 패자의 DB만 롤백될 수 있으므로, 동시 상황에서 SOLD 한 번 발행은 아직 보장하지 않는다. DB 변경과 후속 이벤트 발행의 원자성은 다음 Ticketing Transactional Outbox 단계에서 보강한다.
- Payment Outbox Relay는 DB 기준의 예약별 선두와 실패 재시도를 보강했지만, 다중 인스턴스의 배타적 claim은 아직 없어 같은 이벤트가 중복 발행될 수 있다. Consumer 상태 머신이 의미상 중복을 방어하지만 Relay 자체의 exactly-once는 보장하지 않는다.
- FAILED 이벤트의 시간 기반 backoff·최대 재시도·운영자 재처리 정책은 아직 없다.
- 취소된 예약에 실제 결제가 완료된 상황은 상태를 되돌리지는 않지만 자동 환불이나 운영 알림까지 해결하지 않는다.

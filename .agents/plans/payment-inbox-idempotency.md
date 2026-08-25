# 결제 이벤트 Inbox 멱등 처리 구현 계획

- 상태: 구현 및 검증 완료
- 기준 브랜치: `dev` (`ae81916`, PR #5 포함)
- 관련 TRD: `docs/trd/payment-event-inbox.md`

## 목표와 완료 조건

- Payment Outbox 이벤트가 재발행되어도 동일한 `eventId`를 Kafka 헤더로 전달한다.
- Ticketing은 Inbox의 `eventId` UNIQUE 제약으로 동일 메시지를 한 번만 반영한다.
- Inbox 기록, Reservation 상태 전이, Ticket 발급은 하나의 DB 트랜잭션으로 커밋하거나 롤백한다.
- Reservation의 낙관적 락과 상태 머신으로 서로 다른 이벤트의 동시 변경과 의미상 중복을 방어한다.
- 카드 결제와 가상계좌 입금 완료 이후에만 Ticket을 `ISSUED`로 전환하고 `SOLD_CONFIRMED`를 발행한다.

## 범위

### 포함

- Payment Outbox `eventId`와 `event-id` Kafka 헤더
- Payment Outbox의 PENDING·FAILED 최소 재시도
- Ticketing Inbox 엔티티·Repository·트랜잭션 Handler
- `Reservation.@Version`
- 카드 결제, 가상계좌 발급, 입금 완료 상태 전이 검증
- 동일 eventId, 의미상 중복, 롤백, 동시 처리 테스트

### 제외

- Ticketing Transactional Outbox
- Outbox Relay 다중 인스턴스 claim, backoff 및 최대 재시도
- Kafka retry topic·DLT·redrive 정책과 실제 재전달 통합 테스트
- 결제 취소의 외부 호출·락 경계 재설계
- 기존 운영 데이터 백필과 롤링 배포 호환성

## 구현 순서

- [x] 최신 dev와 기준선 테스트 확인
- [x] 실패하는 상태 전이·중복 소비 테스트 작성
- [x] Payment Outbox eventId 전파 구현
- [x] Ticketing Inbox와 낙관적 상태 전이 구현
- [x] 실제 DB 동시성·롤백 검증
- [x] 전체 영향 모듈 테스트
- [x] 독립 리뷰 반영
- [ ] PR 작성

## 검증

- `./gradlew :payment:test :ticketing:test`
- 동일 eventId를 순차·동시에 처리해 Inbox 한 건과 Ticket 한 번 발급 확인
- 상태 전이 실패 시 Inbox와 Reservation 변경이 함께 롤백되는지 확인
- 카드·가상계좌 발급·입금 완료별 Ticket 및 SOLD 이벤트 발행 시점 확인

## 알려진 후속 작업

- 현재 Ticketing의 `SOLD_CONFIRMED`는 Kafka 직접 발행이므로 DB 변경과 발행의 원자성은 다음 Outbox PR에서 완성한다.
- Payment Outbox는 ID 순서로 최대 100건을 재조회하고 같은 예약의 선행 실패 시 후속 발행을 건너뛴다. 다만 backoff·최대 재시도·다중 Pod claim이 없어 별도 보강이 필요하다.

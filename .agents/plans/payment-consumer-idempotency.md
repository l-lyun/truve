# 결제 이벤트 소비와 예약 상태 전이 멱등성 구현 계획

- 상태: 구현 수정 및 검증 완료
- 기준 브랜치: `dev` (`ae81916`, PR #5 포함)
- 관련 TRD: `docs/trd/payment-consumer-idempotency.md`

## 목표와 완료 조건

- 별도 Inbox 테이블 없이 기존 PaymentConsumer와 BookingService 흐름을 유지한다.
- Reservation 상태 머신으로 같은 결제 결과의 재처리를 멱등하게 종료한다.
- Reservation의 낙관적 락으로 동시에 실행되는 서로 다른 상태 변경의 덮어쓰기를 막는다.
- 카드 결제와 가상계좌 입금 완료 이후에만 Ticket을 `ISSUED`로 전환하고 `SOLD_CONFIRMED`를 발행한다.

## 범위

### 포함

- PaymentConsumer에서 BookingService 직접 호출
- `Reservation.@Version`
- 카드 결제, 가상계좌 발급, 입금 완료 상태 전이 검증
- 의미상 중복 이벤트와 취소 후 지연 이벤트 방어
- 실제 MySQL 낙관적 락 동시성 테스트
- Payment Outbox의 FAILED 재시도와 topic·messageKey별 선행 이벤트 순서 보장
- 신규 PENDING·FAILED 재시도 배치 분리 및 예약별 실패 격리

### 제외

- Inbox 테이블과 eventId 처리 이력
- Ticketing Transactional Outbox
- Kafka retry topic·DLT·redrive 정책과 실제 재전달 통합 테스트
- Payment Outbox Relay 다중 인스턴스 배타적 claim
- FAILED 재시도의 backoff·최대 횟수·운영자 재처리
- 결제 취소의 외부 호출·락 경계 재설계

## 구현 순서

- [x] 기존 결제 Consumer·Reservation·Ticket 흐름 확인
- [x] 실패하는 상태 전이·중복 처리 테스트 작성
- [x] Reservation 낙관적 락과 상태 전이 구현
- [x] Consumer를 BookingService 직접 호출 구조로 정리
- [x] Inbox·eventId 관련 코드 제거
- [x] 실제 MySQL 낙관적 락 검증
- [x] 전체 영향 모듈 테스트와 독립 리뷰
- [x] PR #6 제목·본문 수정
- [x] 결제 리뷰: 선행 이벤트 실패 재시도와 후속 이벤트 차단
- [x] 결제 리뷰: 영구 실패 이벤트와 신규 배치 격리

## 검증

- `./gradlew :payment:test :ticketing:test`
- 같은 카드 결제·가상계좌·입금 완료 이벤트를 순차 호출해 Ticket과 SOLD 후속 처리가 한 번만 수행되는지 확인
- 실제 MySQL에서 Reservation 엔티티를 동시에 변경해 하나의 커밋과 하나의 낙관적 락 실패가 발생하는지 확인
- 취소 후 지연된 결제 이벤트가 Reservation을 CONFIRMED로 되돌리지 않는지 확인
- 실패한 선행 Outbox가 같은 topic·messageKey의 후속 이벤트만 차단하는지 확인
- FAILED 100건과 신규 PENDING 배치를 분리해 신규 이벤트가 고갈되지 않는지 확인

## 알려진 후속 작업

- `SOLD_CONFIRMED`의 DB 변경·Kafka 발행 원자성은 다음 Ticketing Outbox PR에서 완성한다.
- 낙관적 락 충돌 이후 최종 재처리 보장은 Kafka retry·DLT 정책과 실제 Kafka 통합 테스트로 보강한다.

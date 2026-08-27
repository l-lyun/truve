# HOLD_REQUESTED Consumer 구현 계획

## 기준 브랜치

- `codex/seat-hold-saga-orchestrator`의 `e57db3e`에서 분기
- 작업 브랜치: `codex/hold-requested-consumer`
- 선행 PR #11은 열린 상태를 유지한다.

## 성공 경로

1. Kafka listener가 `HOLD_REQUESTED`를 역직렬화해 비트랜잭션 handler에 전달한다.
2. handler가 만료 시각과 Redis `holdId` 전체 좌석 소유권을 확인한다.
3. 별도 DB 트랜잭션이 Reservation과 이벤트 계약을 검증한다.
4. 좌석을 비관적 락 없이 조회하고 `@Version` 낙관적 락으로 전체 `AVAILABLE -> HOLD`를 반영한다.
5. Ticket snapshot을 만들고 Reservation을 `HOLD_PENDING -> PAYMENT_READY`로 전이한다.
6. `booking.payment`의 `CREATE` 이벤트를 Ticketing Outbox에 같은 트랜잭션으로 저장한다.
7. flush/커밋이 완료된 뒤 Consumer가 성공 반환한다.

## 실패 경로

- 만료: 새 트랜잭션에서 `EXPIRED`, `blockBooking=null` 저장 후 Redis 조건부 보상
- 좌석 충돌·소유권 상실: 새 트랜잭션에서 `HOLD_FAILED`, `blockBooking=null` 저장 후 Redis 조건부 보상
- 낙관적 충돌: 재조회해 같은 예약의 `PAYMENT_READY`면 멱등 성공, 다른 소유면 실패 확정
- DB/Redis 일시 오류 또는 커밋 여부 불확실: 상태 변경과 Redis 보상 없이 예외를 전파해 Kafka 재시도
- Redis 보상 실패: DB terminal 상태는 유지하고 Redis TTL로 수렴

## 커밋 계획

1. `feat(ticketing): HOLD_REQUESTED 성공 트랜잭션 구현`
2. `feat(ticketing): HOLD 실패 확정과 Redis 보상 연결`
3. `test(ticketing): HOLD Consumer 장애·동시성 검증`
4. `fix(ticketing): 진행된 HOLD 이벤트를 멱등 처리`
5. `feat(ticketing): HOLD Consumer 재시도와 만료 복구 추가`
6. `docs(ticketing): 비동기 DB HOLD 구현 상태 반영`

## 검증 결과

- Consumer 라우팅, 만료·Redis 충돌·인프라 오류 분류 단위 테스트
- 좌석 HOLD, Ticket, PAYMENT_READY, Payment Outbox 동시 커밋 통합 테스트
- 동일 이벤트 재처리 시 Ticket·Outbox 중복 방지 통합 테스트
- 좌석 하나 충돌 시 나머지 좌석·Ticket·Reservation·Outbox 전체 롤백 통합 테스트
- 실제 Redis에서 meta·fingerprint·세션 Set·전체 좌석 소유권 원자 검증
- 결제 이후 상태로 진행된 주문의 늦은 HOLD 성공·실패 이벤트 멱등 처리
- 알 수 없는 이벤트의 DLT 분류와 만료된 HOLD_PENDING bulk 정리

## 남은 검증과 후속 작업

- 관련 Consumer·DB 통합 테스트는 통과했다.
- `:ticketing:test` 전체 검증은 다른 PC로 이동하기 직전 사용자 요청으로 중단됐다.
- 실제 Kafka 컨테이너 기반 retry·DLT와 실제 MySQL 동시 낙관락 검증은 후속 작업이다.
- Redis 검증과 DB 트랜잭션 사이 TOCTOU의 강한 해결은 fencing token 도입 시 함께 다룬다.

# 좌석 HOLD Saga 오케스트레이터 구현 계획

## 범위

- Redis Lua의 holdId lease와 신규/멱등/충돌/한도 결과 구분
- `Reservation(HOLD_PENDING)`과 `HOLD_REQUESTED` Outbox의 단일 DB 트랜잭션
- 비트랜잭션 `hold()` Saga에서 Redis 선점, DB 접수, 조건부 보상 조율
- `Idempotency-Key` 기반 주문 복원과 같은 키·다른 좌석 요청 거절
- hold API가 `reservationNumber`, `status`, `expiresAt` 반환

## 커밋 단위

1. `feat(ticketing): holdId 기반 Redis 좌석 lease 추가`
2. `feat(ticketing): HOLD_PENDING 주문 접수 트랜잭션 추가`
3. `feat(ticketing): 좌석 hold Saga를 주문 접수에 연결`
4. `docs(ticketing): 좌석 hold Saga 구현 결정 반영`

## 후속 범위

- `HOLD_REQUESTED` Consumer의 낙관적 DB HOLD, Ticket 생성, PAYMENT_READY 전이
- holdId 기반 취소·만료 Saga와 기존 API 전환
- 결제 상태 조회 및 재결제 UX 연결

PR 3 Consumer가 완성되기 전에는 PR 2의 Producer 실행 경로를 배포하지 않는다.

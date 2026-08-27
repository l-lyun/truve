# 좌석 선점 Saga 기반 PR 1 구현 계획

- 상태: Completed
- 관련 TRD: `docs/trd/seat-hold-order-saga.md`
- 기준 브랜치: `dev`
- 작업 브랜치: `codex/seat-hold-saga-foundation`

## 목표

- 현재 좌석 선점·예매 생성 동작과 공개 API를 변경하지 않는다.
- 후속 `hold()` Saga가 사용할 도메인 상태, JPA 컬럼, 이벤트 계약과 Outbox message key 확장 지점을 마련한다.
- 실제 DB migration은 추가하지 않고 JPA 엔티티 정의만 변경한다.

## 포함 범위

- `ScheduledSeat.version` 낙관적 락 컬럼
- `Reservation.holdId`, `Reservation.expiresAt` nullable 컬럼
- 기존 `Reservation.create()`와 분리된 `createHoldPending()` factory
- `HOLD_PENDING`, `PAYMENT_READY`, `HOLD_FAILED`, `EXPIRED` 예약 상태
- `HOLD_REQUESTED` 이벤트 계약
- 이벤트별 Outbox message key 선택 지원
- 엔티티·Repository·Outbox 단위 테스트
- 목표 Saga TRD

## 제외 범위

- Redis 키 또는 Lua 변경
- `hold()` 오케스트레이터와 보상 처리
- 실제 `HOLD_REQUESTED` Outbox 생성 경로
- Kafka Consumer와 DB 좌석 상태 변경
- 기존 비관적 좌석 조회 제거
- 결제 상태 조회, 재결제, 만료 Scheduler
- Flyway/Liquibase migration

## 구현 순서

- [x] 최신 `dev` 기준 독립 브랜치 생성
- [x] 도메인·이벤트 기반 구현
- [x] 관련 단위·JPA 테스트 보강
- [x] `:ticketing:test` 검증
- [x] 독립 코드 리뷰 및 발견 사항 반영
- [x] 커밋·푸시·PR 생성

## 완료 조건

- 기존 생성 흐름은 계속 `CREATED`에서 시작한다.
- Saga용 factory는 `holdId`, `expiresAt`, `HOLD_PENDING`을 보존한다.
- nullable `holdId`는 기존 예약 여러 건을 허용하고 non-null 중복 값은 DB unique 제약으로 차단한다.
- 기존 Outbox 이벤트의 message key 계약은 유지되고 `HOLD_REQUESTED`는 `holdId`를 사용할 수 있다.
- Ticketing 전체 테스트가 성공한다.

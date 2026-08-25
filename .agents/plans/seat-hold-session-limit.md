# 세션 단위 좌석 선점 보강 구현 계획

- 상태: Completed
- 관련 PRD: 사용자 요청
- 관련 TRD: `docs/trd/seat-hold-concurrency.md`

## 목표와 완료 조건

- 동일 사용자·회차의 좌석 선점 요청을 짧은 Redis 락으로 직렬화한다.
- 여러 좌석 선점과 세션별 최대 4석 제한을 Redis Lua로 원자적으로 보장한다.
- 좌석 소유권 키와 세션별 좌석 Set을 선점·취소·예매 claim·퇴장 흐름에서 함께 유지한다.
- 실제 Redis를 사용하는 동시성 테스트로 핵심 실패 시나리오를 검증한다.

## 범위

### 포함

- `seat:hold:lock:{showScheduleId}:{userId}` 요청 락
- `seat:hold:{showScheduleId}:{scheduledSeatId}` 좌석 소유권 키
- `seat:holds:{showScheduleId}:{sessionToken}` 세션별 좌석 Set
- 최초 선점, 선택 취소, 예매 claim 성공·복구, 세션 퇴장 정리

### 제외

- 사용자·회차별 활성 sessionToken 단일화
- Redis Cluster hash slot 호환 키 마이그레이션
- Kafka Inbox/Outbox와 결제·취소 낙관적 락

## 영향 범위

- `ticketing` Redis 좌석 선점 및 예매 생성 흐름
- `common` Ticketing 오류 코드
- Ticketing 단위·Redis 통합 테스트

## 구현 순서

- [x] 현재 동작과 테스트 확인
- [x] 최소 범위 구현
- [x] 관련 테스트 실행
- [x] 변경 사항과 후속 작업 정리

## 검증

- `./gradlew :ticketing:test`
- 실제 Redis에서 동시 사용자 락, 다중 좌석 원자성, 세션 누적 4석, stale 해제 방어 검증

검증 결과: Ticketing 테스트 115개 성공, 실패·오류·건너뜀 0개.

## 결정 사항과 차단 요소

- 현재 로컬·배포 구성은 standalone Redis이므로 여러 키 Lua를 같은 hash slot에 배치하는 키 변경은 이번 범위에서 제외한다.
- 좌석 lease TTL은 연장하지 않고, 세션 Set은 stale 멤버를 다음 연산에서 소유권 키와 대조해 정리한다.

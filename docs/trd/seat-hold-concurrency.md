# 세션 단위 좌석 선점 동시성

## 문제

기존 최초 좌석 선점은 좌석별 `SET NX`를 반복했다. 요청 좌석 중 뒤쪽 좌석이 이미 선점된 경우 앞쪽 좌석만 남는 부분 성공이 가능했고, 여러 탭의 요청을 합산한 세션별 최대 4석 제한과 세션 종료 시 일괄 정리를 지원하지 못했다.

## Redis 모델

```text
seat:hold:lock:{showScheduleId}:{userId} -> requestUUID
seat:hold:{showScheduleId}:{scheduledSeatId} -> sessionToken
seat:holds:{showScheduleId}:{sessionToken} -> Set<scheduledSeatId>
```

- 사용자·회차 락은 동일 사용자의 동시에 실행되는 좌석 연산을 빠르게 거절하는 완충 장치다.
- 좌석 소유권 키는 서로 다른 사용자 사이의 좌석 상호 배제를 보장한다.
- 세션 Set은 여러 요청을 합산한 최대 4석 제한과 퇴장 정리를 지원한다.
- 최종 정합성은 Lua가 좌석 소유권 키와 세션 Set을 한 번에 검사·변경하여 보장한다.

## 원자적 상태 변경

최초 선점 Lua는 stale Set 멤버를 좌석 소유권 키와 대조해 제거하고, 기존 좌석과 신규 좌석의 합집합이 4개 이하인지 확인한다. 같은 세션의 진행 중 예매 claim은 `booking:{sessionToken}:` 접두사로 식별해 한도에 계속 포함한다. 요청 좌석 중 하나라도 다른 세션 소유라면 어떤 키도 새로 만들지 않는다.

선택 취소는 모든 요청 좌석이 현재 세션 소유일 때 좌석 키 삭제와 Set 제거를 함께 수행한다. 예매 claim은 Set 멤버십과 좌석 소유권을 함께 확인한 뒤 좌석 값만 예매 claim으로 전환한다. 예매 성공 정리 또는 실패 복구가 끝날 때 Set도 같은 Lua에서 맞춘다.

사용자 락과 좌석 락 해제는 현재 값이 요청별 UUID 또는 sessionToken과 일치할 때만 수행한다.

## 트랜잭션 경계

Redis 사용자 락은 외부 결제 호출이나 DB 트랜잭션과 함께 유지하지 않는다. 좌석 선점·취소에서는 DB 입력 검증 후 Redis Lua 전후에만 유지한다. 예매 생성에서도 좌석 값을 예매 claim으로 바꾸는 Lua 실행 동안만 같은 사용자 락을 사용한다. DB 예매 생성은 별도의 사용자·회차 예매 락과 비관적 좌석 행 락으로 보호한다.

## 범위와 제약

- 현재 구성은 standalone Redis다. Redis Cluster로 전환할 때는 여러 키 Lua가 같은 slot을 사용하도록 회차 hash tag를 포함한 키 마이그레이션이 필요하다.
- 사용자·회차당 sessionToken 하나라는 정상 대기열 흐름을 전제로 한다. 복수 활성 세션을 서버에서 강제하는 기능은 별도 작업이다.
- 결제 완료·취소 경쟁, Kafka 중복 소비, Inbox/Outbox는 후속 작업이다.

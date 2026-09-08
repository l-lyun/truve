# Outbox Relay 기준선 측정

이 실험은 실제 MySQL → Ticketing Relay → Kafka 발행 확인 → PUBLISHED 반영 경로를 측정한다.
독립된 key와 작은 `{}` payload를 DB에 직접 적재한다. 예약 API의 처리량, Consumer 업무 완료 시간,
중복 비즈니스 변경, 같은 예약 내 순서, Kafka 장애 복구는 이번 정상 상태 실험의 검증 범위가 아니다.

## 추가한 계측

- `ticketing_outbox_events.published_at`: Kafka 응답 확인 뒤 소유권 조건부 UPDATE 직전에 생성한 애플리케이션 시각.
  해당 트랜잭션이 커밋돼야 보인다. 정확한 커밋 시각이 아니며 기존 행을 소급해 채우지 않는다.
- `ticketing_outbox_completion_total{outcome="published|failed|stale"}`: 완료 결과의 커밋 후 카운터.
- `ticketing_outbox_claims_recovered_total`: 만료 claim 회수 커밋 후 카운터.
- `ticketing_outbox_relay_batch_seconds_*`: claim, Kafka 응답, 상태 커밋을 포함한 Relay 실행 시간.
  빈 실행과 실패 실행도 포함하며 실행 사이 3초 대기는 포함하지 않는다.

카운터는 프로세스 재시작 시 초기화된다. DB 커밋 직후 프로세스가 종료되면 콜백/메트릭 기록이
누락될 수 있어 메트릭 자체를 영속 이벤트 원장으로 사용하지 않는다.

## 실행 조건

- 열린 PR #12의 `codex/hold-requested-consumer` 브랜치 + 미커밋 계측 변경
- 전용 컨테이너: `truve-pr12-mysql`, `truve-pr12-redis`, `truve-pr12-kafka`
- MySQL 23306, Redis 26379, Kafka 29094, Ticketing HTTP 18084
- Relay 1개, PENDING 배치 최대 100건, fixedDelay 3,000ms
- JVM timezone Asia/Seoul, SQL seed session timezone +09:00
- 기존 local 프로필의 SQL 로그는 `SPRING_JPA_SHOW_SQL=false`로 비활성화
- 별도 CPU/메모리 제한 없는 로컬 탐색 측정. 다른 프로젝트도 Docker 자원을 공유한다.

배치마다 최소 3초를 기다리므로 대량 backlog의 처리량은 이 스케줄에 크게 좌우된다.
이 결과는 애플리케이션 최대 처리 능력을 나타내지 않는다.

## 재실행

계측 버전 앱과 위 컨테이너가 실행 중인 상태에서, 전용 DB 비밀번호를 환경변수 `MYSQL_PWD`로 설정한다.
앱 JVM 시간대는 `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Seoul`로 명시한다.

```sh
python3 performance/outbox/run_baseline.py --events 1000 --repeats 3
```

스크립트는 전용 DB에 매 실행마다 새로운 토픽의 데이터를 추가한다. 기존 행을 삭제하지 않는다.
결과는 `results/<실행시각>-<식별자>/`에 남는다.

## 결과 읽기

- `summary.json`: 실행별 전체 소진 시간, 관측 처리량, 이벤트 지연 p50/p95/p99
- `run-N-events.csv`: 이벤트별 생성 시각·발행 기록 시각·재시도 횟수
- `run-N-polling.json`: 1초 간격 상태 관측값
- `run-N-before.prom`, `run-N-after.prom`: Prometheus 원본과 성공 카운터 비교 근거
- `metadata.json`, `source.diff`: 실행 조건과 계측 소스 차이

`observed_drain_seconds`는 seed 실행 직전부터 최종 PUBLISHED를 관측할 때까지다.
1초 polling 및 Docker 명령 실행 시간이 포함된다. `observed_events_per_second`는 건수를 이 시간으로 나눈 값이다.
`publication_record_lag_*`는 `published_at - created_at`으로 구한 발행 기록 지연이다.
정확한 DB commit latency가 아니며 분위수는 nearest-rank 방식으로 계산한다.

각 실행은 이벤트 1,000건 PUBLISHED, 미처리 0건, 성공 카운터 증가량 1,000건의 일치를 검사한다.
재시도 횟수도 보고한다. 이것만으로 Kafka 물리적 중복 0건이나 업무 정합성을 주장하지 않는다.

## 다음 비교

현재 설정의 반복 결과를 보존한 뒤 batch/poll 설정 또는 재시도 정책 중 하나만 바꿔 비교한다.
Relay 2개 비교, Kafka 장애 주입, Backoff/Jitter 개선은 각각 별도 실험으로 수행한다.

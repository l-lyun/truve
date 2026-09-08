# Relay 종료 후 작업 회수 — 실제 실행 PASS

2026-09-08, 열린 PR #12 브랜치에 실험 전용 claim 정지 지점을 추가해 실행했다.
Kubernetes 없이 동일 JAR를 사용하는 두 로컬 JVM과 실제 MySQL·Kafka를 사용했다.

| 항목 | 실제 결과 |
|---|---|
| A claim 결과 | 20건 PROCESSING, 표식과 DB의 ID·claimToken 일치 |
| A 정지 시 Kafka 상태 | 토픽 end offset 0, 아직 미발행 |
| B 상태 | A 종료 전에 Ready 확인 |
| A 종료 | SIGKILL, 프로세스 return code -9 |
| B 회수 카운터 증가 | 20 |
| B 커밋 후 발행 성공 카운터 증가 | 20 |
| 최종 DB 상태 | 20건 PUBLISHED, 미처리 0 |
| 최종 retry_count | 각 1, timeout 회수 때문에 증가 |
| 실제 Kafka 읽기 | 20건, 고유 key 20개, 입력 key와 일치 |
| A 종료 → 전체 PUBLISHED 관측 | 35.230초 |
| claim 표식 → 전체 PUBLISHED 관측 | 70.196초 |

## 해석

A는 claim 트랜잭션을 커밋한 직후, Kafka 발행 전에 멈췄다.
B가 Ready가 된 것을 확인한 다음 A 프로세스를 실제로 종료했다.
A를 재시작하지 않아도 B가 lease가 만료된 이벤트를 회수해 발행을 완료했다.

claim timeout은 **60초**, 회수 검사 주기는 **1초**, Relay 실행 후 대기는 **1초**로 지정했다.
각 기본값 300초·30초·3초와 다르다. timeout은 A 종료가 아니라 **claimedAt부터** 계산된다.
B 기동 중에도 lease 시간이 흐르므로 종료 후 관측 시간은 60초보다 짧다.
관측 시간에는 Kafka 연결·발행·DB 결과 반영 및 polling/명령 지연이 포함된다.
단 한 번의 통제된 실행 결과이며 운영 복구 시간 보장이나 반복 측정 평균이 아니다.

이벤트 key는 서로 독립적이고 payload는 `{}`다.
이번에 확인한 것은 발행 전 종료에서의 작업 회수와 실제 메시지 전달이다.
발행 성공 후 DB 반영 전 종료에 따른 중복 전달, 업무 Consumer 멱등성,
같은 예약의 이벤트 순서는 별도 검증 대상이다.

## 근거 파일

- metadata.json: HEAD, JAR SHA256, 토픽, timeout·실행 조건
- claimed.csv, claimed-db.tsv: A의 커밋된 소유권 표식과 DB 대조
- kafka-end.txt: A 정지 시 Kafka offset 0
- timeline.json: A/B 준비, SIGKILL, 복구 관측 타임라인
- final-db.tsv: 모든 이벤트의 PUBLISHED, retry_count, published_at
- kafka-read.txt: 실제 Kafka offset·key·payload
- b-before.prom, b-after.prom: B의 회수 및 완료 카운터
- summary.json: 기계 판독 결과
- relay-a.log, relay-b.log: 로컬 JVM 실행 로그

## 코드 검증

Fault gate 및 scheduler 관련 9개 테스트 PASS, failures/errors/skipped 0.
`./gradlew :ticketing:bootJar --no-daemon` PASS.
fault 프로필과 marker 경로가 모두 있을 때만 정지하며, 트랜잭션 안에서는 정지를 거부한다.
이번 변경 후 전체 Ticketing 테스트는 다시 실행하지 않았다.

## 포트폴리오 표현 예시

> 로컬 다중 Relay 환경에서 claim 커밋 직후 프로세스를 강제 종료했습니다.
> 남은 Relay가 만료 작업 20건을 회수해 전량 발행하는 것을 실제 DB와 Kafka에서 검증했습니다.
> 실험 설정은 claim timeout 60초이며, 해당 실행에서 종료 후 전체 발행 완료 관측까지 35.2초가 걸렸습니다.

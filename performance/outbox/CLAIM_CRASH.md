# Relay 강제 종료 후 작업 회수 실험

Kubernetes 없이 로컬 JVM 두 개로 실행한다. 종료된 JVM을 다시 시작하는 실험이 아니라,
남은 JVM이 DB의 미완료 Outbox를 회수하는 실험이다.

## 재현 순서

1. A를 `outbox-fault-test` 프로필과 새로운 marker 경로로 실행한다.
2. 독립 key 이벤트 20건을 전용 DB에 넣는다.
3. A가 claim 트랜잭션을 커밋하고 Kafka에 보내기 전에 marker를 기록하고 멈춘다.
4. DB 20건의 PROCESSING 상태, marker의 ID·claimToken 일치, Kafka offset 0을 확인한다.
5. B를 기동해 Ready 상태를 확인한다. 이벤트가 아직 PROCESSING인 동안 A 자식 프로세스를 SIGKILL한다.
6. B가 claim timeout 후 FAILED로 회수하고 다시 claim해 Kafka에 보낸 뒤 PUBLISHED를 기록한다.
7. DB 상태, B의 회수·성공 카운터, 실제 Kafka에서 읽은 key를 대조한다.

기본 경로에는 정지가 없으며 프로필과 `ticketing.outbox.fault.marker-file` 설정이 모두 있어야 gate가 활성화된다.
gate는 claim 커밋 뒤 실행하므로 정지 중 DB 트랜잭션이나 행 락을 유지하지 않는다.

## 실행

전용 컨테이너 `truve-pr12-mysql`, `truve-pr12-redis`, `truve-pr12-kafka`가 필요하다.
기존에 띄운 Truve 앱의 정확한 PID를 확인해 정상 종료한 뒤 18084·18085가 비어 있는 상태에서 실행한다.
스크립트는 임의 PID를 받지 않으며 자신이 생성한 자식 JVM만 종료한다.

```sh
./gradlew :ticketing:bootJar --no-daemon
# MYSQL_PWD에 전용 로컬 DB 비밀번호를 설정한다.
python3 performance/outbox/run_claim_crash.py
```

실험 조건은 claim timeout 60초, 회수 검사 1초, relay fixedDelay 1초다.
각 기본값 300초·30초·3초와 구분해야 한다. JVM과 seed 시간대는 Asia/Seoul로 맞춘다.
검증이 끝나면 스크립트가 생성한 A/B를 정리한다. 일상 개발로 돌아갈 때는 기본 설정으로 앱을 다시 실행한다.
실패한 경우에도 원본과 실패 시각은 결과 폴더에 보존한다.

## 판단 기준

- A는 SIGKILL로 종료되어 return code -9를 보인다.
- A를 재기동하지 않은 상태에서 B가 생존하고 20건 모두 PUBLISHED가 된다.
- 각 이벤트 retry_count는 timeout 회수 때문에 1 증가하고, 완료 시 claimToken은 해제된다.
- B의 recovered와 published 카운터가 각각 20 증가한다.
- Kafka에서 읽은 20개 key가 DB 입력과 일치한다.

결과의 kill→완료 시간은 timeout 전체와 같지 않다. timeout은 종료 시각이 아닌 claimedAt에서 계산되기 때문이다.
1초 DB 관측 주기와 명령 실행 지연도 포함된다. 결과에는 claim 표식→완료 시간도 함께 남긴다.

이번 시나리오는 발행 전 종료이므로 중복 발행을 의도적으로 만들지 않는다.
발행 성공 직후·DB 완료 기록 전 종료, Consumer 업무 멱등성, 같은 예약의 순서 검증은 별도 시나리오다.
따라서 결과로 exactly-once 또는 운영 환경의 복구 시간 보장을 주장하지 않는다.

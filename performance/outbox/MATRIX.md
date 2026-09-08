# Outbox 폴링·Relay 확장 비교

## 목적

발행 지연을 줄이는 데 드는 추가 DB 조회와 JVM 자원을 확인한다. 실제 Kafka 레코드와 DB를 대조해 정상 다중 Relay 처리에서 누락·중복 전달 여부도 확인한다. 운영 서비스 전체 신뢰성이나 exactly-once를 보장하는 실험은 아니다.

## 실행 범위

- 전용 `truve-pr12-mysql`, `truve-pr12-kafka`, `truve-pr12-redis`만 사용.
- 기존 미완료 Outbox가 있거나 18084/18085 포트가 사용 중이면 중단.
- 별도 신규 Kafka 토픽과 `MATRIX` 이벤트만 생성. 기존 데이터 삭제·전역 DB 계측 카운터 초기화 없음.
- 자체 생성한 JVM만 finally에서 종료. 기본 애플리케이션 재실행은 실험 종료 후 별도 수행.
- 인프라/유료 서비스 생성 없음. 기본 애플리케이션 설정 수정 없음.
- 이번 첫 실행에서 호스트 과부하를 발견한 후, 시작/매 회차 전에 1분 load가 논리 CPU 수의 2배를 넘으면 중단하도록 보완했다. 이는 보수적 휴리스틱이지 환경 격리를 보장하는 기준은 아니다. 첫 실행 원본 `harness.py.txt`에는 이 후속 보호 장치가 없다.

## 조건

| 폴링 fixedDelay | Relay | 본측정 |
|---|---:|---|
| 3,000ms | 1 | 1,000건 × 3회 |
| 1,000ms | 1 | 1,000건 × 3회 |
| 300ms | 1 | 1,000건 × 3회 |
| 1,000ms | 2 | 1,000건 × 3회 |
| 300ms | 2 | 1,000건 × 3회 |
| 3,000ms 재확인 | 1 | 1,000건 × 1회 |

배치 100건 고정. 각 JVM은 Xms256m/Xmx512m. 각 조건마다 300건 워밍업 후 모든 Relay가 실제 발행에 참여했는지 확인하고, 미참여 JVM이 있으면 최대 3번 워밍업. 유휴 30초 관찰 후 본측정한다. 토픽은 미리 생성하며 payload는 `{}`, 키는 이벤트마다 다르다. 업무 Consumer는 비활성화한다.

## 핵심 지표의 의미

- **발행 기록 지연 p99**: DB `created_at`에서 `published_at`까지의 이벤트별 차이, nearest-rank. 실제 소비 완료·정확한 DB 커밋 시각·API 지연이 아니다. 1,000건 일괄 투입이므로 뒤쪽 배치 대기시간을 포함한다.
- **관측 소진 시간**: seed 호출 시작부터 0.5초 간격 DB 관찰에서 전량 PUBLISHED를 처음 확인할 때까지. 호출/관찰 오버헤드를 포함한다. 처리량은 1,000 / 이 시간으로 산출한다.
- **유휴 선점 SELECT/s**: MySQL performance_schema에서 Relay의 `FOR UPDATE SKIP LOCKED` 쿼리 digest만 전후 차감. 관찰용 조회와 분리한다. 유휴에는 PENDING/FAILED를 각각 조회하므로 한 주기에 보통 두 SELECT다.
- **CPU 보조 지표**: MySQL 컨테이너 cgroup 누적 사용 시간 및 JVM 프로세스 누적 CPU 시간 차이. DB CPU에는 테스트 관찰·MySQL 내부 작업이 포함된다. 공유 호스트 노이즈와 창 경계 차이가 있어 비용 절감율이나 청구 금액으로 환산하지 않는다.
- **전달 검증**: Kafka 전체 레코드 키 집합과 DB 입력 키 집합의 일치 및 레코드 수를 모두 확인. 누락·예상 밖 키·중복 각각 계산. Relay별 published counter도 개별 기록한다.

## 통과 조건

모든 이벤트 PUBLISHED, claim_token/claimed_at 해제, Kafka 키와 건수 일치, published counter 합계 일치. 정상 시나리오의 failed/stale/recovered/retry와 누락·중복은 0이어야 한다. 위반하면 원시 증거를 남기고 실패 처리한다. 이 정상 시나리오의 중복 0은 장애 시 중복 불가능 또는 업무 멱등성을 뜻하지 않는다.

## 재현

현재 코드로 ticketing bootJar를 먼저 빌드한다. 실험용 서버와 포트가 비어 있는 것을 확인한 뒤 로컬 전용 DB 암호를 MYSQL_PWD 환경 변수로 전달한다. 비밀번호를 결과물에 기록하지 않는다.

```sh
python3 -m unittest discover -s performance/outbox -p 'test_matrix_helpers.py' -v
python3 performance/outbox/run_matrix.py
python3 performance/outbox/verify_matrix.py performance/outbox/results/<실행폴더>
```

`performance/outbox/results/matrix-*`에 원시 DB 행·관찰 타임라인·Kafka 레코드·Prometheus·DB 계측 전후값·JAR SHA256·조건별 요약을 저장한다. 로그는 Git ignore 적용 여부를 별도로 확인해야 한다.

## 해석의 한계

설정 순서는 고정이고, 공유 로컬 호스트이며, 완료된 DB 행은 누적된다. 마지막 3초 조건 재확인은 환경 변동 진단이지 통계적 보정이 아니다. 작은 차이는 성과로 단정하지 않는다. 3회 중앙값과 범위를 함께 제시하고, 단순 처리량 배수보다 목표 지연을 달성하는 데 필요한 쿼리·인스턴스 비용으로 설정을 선택한다. 실제 트래픽/SLO가 없으므로 선정 설정은 포트폴리오 실험의 후보이며 운영 권장값으로 일반화하지 않는다.

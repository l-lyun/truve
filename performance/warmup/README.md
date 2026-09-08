# 단일 인스턴스 JVM 웜업 비교 실행

같은 PC에서 같은 JAR를 새 JVM으로 반복 실행해 웜업 OFF/ON을 비교한다. 목표는 **readiness까지 걸리는 추가 시간과 초기 HTTP 요청 지연의 변화**를 함께 확인하는 것이다. Kubernetes와 다른 서비스 전체를 실행할 필요는 없다.

- 구현: [PR #14](https://github.com/l-lyun/truve/pull/14)
- 실험 도구와 이 안내서: [PR #15](https://github.com/l-lyun/truve/pull/15)
- 다음 작업을 넘겨받는 사람/에이전트: [진행 상황](../../.agents/handoffs/ticketing-warmup-benchmark.md)

## 1. 다른 PC 준비

필수 도구는 Git, **JDK 21**, **Python 3.10 이상**, 실행 중인 Docker Engine이다. Python 외부 패키지는 필요 없다. 처음에는 Gradle 의존성과 Docker 이미지를 다운로드할 인터넷 연결이 필요하다.

아래 명령은 macOS/Linux의 bash 또는 zsh에서 저장소 루트를 기준으로 실행한다. **Windows PowerShell에서 직접 실행하지 않는다.** 도구가 `os.getloadavg()`와 Unix `ps`를 사용하므로 Windows에서는 WSL2 Linux 터미널 안에 저장소·Java·Python을 준비하고, 해당 배포판에서 Docker를 사용할 수 있게 구성한다. 기존 실제 실행 검증은 macOS에서만 수행했다. WSL2/Linux에서는 먼저 짧은 실행으로 호환성을 확인한다. WSL의 부하 지표는 Windows 전체 부하를 완전히 나타내지 못한다.

다른 PC에 새 작업 폴더로 받는 예시:

```sh
git clone --branch test/ticketing-warmup-benchmark --single-branch https://github.com/l-lyun/truve.git truve-warmup
cd truve-warmup
git status --short
git log -1 --oneline
java -version
python3 --version
docker info
```

이미 이 브랜치를 받아 놓았다면 깨끗한 작업 디렉터리에서 `git pull --ff-only`로 갱신한다. 진행 중인 수정이 있는 다른 체크아웃은 그대로 두고 별도 폴더를 사용한다. PR #15 브랜치에는 #14 구현도 포함되어 있다.

측정 전에는 사용자가 중단해도 되는 무거운 빌드·부하 테스트를 정리하고 전원과 절전 설정을 확인한다. 다른 PC의 수치를 기존 Mac 수치와 섞어 ON/OFF 개선율을 계산하지 않는다.

## 2. 빌드와 준비 확인

```sh
bash ./gradlew :ticketing:clean :ticketing:bootJar --no-daemon
python3 -m unittest discover -s performance/warmup -p 'test_*.py'
docker pull mysql:8.4
docker pull redis:7.4-alpine
```

각 명령이 성공했는지 확인하고 다음으로 진행한다. 새 PC의 최초 빌드에는 `--offline`을 붙이지 않는다. Java 버전이 21이 아니면 `JAVA_HOME`과 `PATH`를 조정해 빌드 및 실행에 같은 JDK 21을 사용한다.

실험 도구가 전용 MySQL/Redis 컨테이너, 빈 DB, 200개 좌석 데이터, 테스트 세션을 만들고 종료 시 자신이 만든 컨테이너와 볼륨만 제거한다. 포트는 loopback의 임의 포트다. `.env`, 기존 DB, Kafka, Docker Compose 전체 실행은 필요 없다. 시험용 프로필은 Kafka listener, Outbox claim, HOLD 만료 스케줄러를 끈다.

## 3. 결과 보관 폴더와 짧은 실행

이후 명령은 **같은 터미널**에서 실행한다. PC 이름은 개인정보 대신 `pc2` 같은 별칭을 쓴다. 원시 결과는 저장소 밖에 두어 실행 중 소스 변경과 섞이지 않게 한다.

```sh
WARMUP_SESSION="pc2-$(date -u +%Y%m%dT%H%M%SZ)"
WARMUP_ROOT="$HOME/truve-warmup-results/$WARMUP_SESSION"
mkdir -p "$WARMUP_ROOT"
git rev-parse HEAD > "$WARMUP_ROOT/source-head.txt"
git status --porcelain > "$WARMUP_ROOT/source-status.txt"
java -version > "$WARMUP_ROOT/java-version.txt" 2>&1
python3 --version > "$WARMUP_ROOT/python-version.txt" 2>&1
```

`source-status.txt`가 비어 있는 상태에서 방금 빌드한 JAR를 사용한다. 비교가 끝날 때까지 소스·JAR·JVM 설정을 바꾸지 않는다.

```sh
python3 performance/warmup/run_benchmark.py \
  --jar ticketing/build/libs/ticketing-0.0.1-SNAPSHOT.jar \
  --output "$WARMUP_ROOT/smoke-01" \
  --purpose smoke --pairs 1 --initial-requests 10 --steady-seconds 2
```

성공하면 다음으로 검증한다:

```sh
python3 performance/warmup/verify_benchmark.py "$WARMUP_ROOT/smoke-01" \
  > "$WARMUP_ROOT/smoke-01-verification.json"
cat "$WARMUP_ROOT/smoke-01-verification.json"
```

정상 결과는 `purpose: smoke`, `verified_runs: 2`다. OFF/ON 2회, 총 60건의 HTTP 응답을 확인하는 기능 점검이며 성능 개선 근거로 사용하지 않는다. 실패하면 5쌍 비교에 들어가기 전에 아래 중단 안내를 따른다.

**검증 출력 파일은 반드시 개별 실행 폴더 바깥에 쓴다.** 실행 폴더 안에 파일을 추가하면 manifest의 파일 목록과 달라져 다음 검증이 실패한다.

## 4. 정식 비교: OFF/ON 5쌍

```sh
python3 performance/warmup/run_benchmark.py \
  --jar ticketing/build/libs/ticketing-0.0.1-SNAPSHOT.jar \
  --output "$WARMUP_ROOT/comparison-01"
```

기본 조건:

| 항목 | 조건 |
| --- | --- |
| 실행 순서 | OFF→ON, ON→OFF를 번갈아 5쌍, 총 10회 |
| JVM | 매번 새 프로세스, `-Xms256m -Xmx512m -XX:ActiveProcessorCount=2` |
| 웜업 ON | 좌석 조회·DTO 변환·JSON 직렬화 100회 |
| 트래픽 허용 | OFF/ON 모두 readiness 이후 |
| 초기 요청 | readiness 직후 100건, 목표 10 RPS |
| 정상 상태 | 이어서 60초, 목표 10 RPS |
| 클라이언트 | 요청을 동시에 보내지 않는 단일 연결 기반 순차 실행 |
| 사전 점검 | 업무 HTTP 호출 없이 웜업 전후 DB와 Redis 상태 비교 |

준비가 끝난 PC에서 약 15~20분을 예상하되, 이는 보장된 실행 시간이 아니다. HTTP 측정 구간만 총 약 700초이고 JVM 기동·인프라 준비 시간이 추가된다. `ActiveProcessorCount=2`는 JVM이 인식하는 프로세서 수이며 OS CPU 사용량 제한이 아니다.

완료 후:

```sh
python3 performance/warmup/verify_benchmark.py "$WARMUP_ROOT/comparison-01" \
  > "$WARMUP_ROOT/comparison-01-verification.json"
cat "$WARMUP_ROOT/comparison-01-verification.json"
```

정상 결과는 `purpose: comparison`, `verified_runs: 10`이다. 검증 명령의 종료 코드가 0이어야 한다. 중간에 실패한 실행에서 일부 쌍만 골라 비교를 완성하지 않는다.

## 5. 중단·오류 처리

- `Host load exceeds 2 x logical CPUs`: 1분 부하가 논리 CPU 수 × 2를 넘었다. 중단 기록을 유지하고 PC가 안정된 뒤 **전체 비교를 새 폴더** `comparison-02`로 다시 실행한다. 중단 기준을 완화하지 않는다.
- `Request start is over 1 second behind schedule`: 순차 클라이언트가 요청 일정에서 1초 이상 밀렸다. 원시 기록을 남기고 원인을 확인한다. 실패 요청을 지우거나 자동 재시도로 덮지 않는다.
- readiness 실패/120초 초과: 해당 실행의 `.log`, `*-readiness.json`, `failure.json`을 확인한다. smoke 단계에서 먼저 해결한다.
- 출력 폴더가 이미 존재함: 덮어쓰기를 막기 위한 동작이다. `smoke-02`, `comparison-02`처럼 새 이름을 사용한다.
- 검증 실패: `failure.json`, `cleanup-failure.json`, checksum/파일 목록 오류를 확인한다. 실패한 검증의 stdout 파일은 비어 있을 수 있으며 성공 증거가 아니다. 원본을 고쳐 검증을 통과시키지 않는다.
- 강제 종료/정리 실패: `docker ps -a --filter label=truve.experiment=warmup`으로 남은 컨테이너를 확인한다. 무관한 컨테이너나 모든 Docker 볼륨을 일괄 삭제하지 않는다.

재시도 시 검증 출력 이름도 `comparison-02-verification.json`처럼 바꾼다. 성공·실패 폴더를 모두 보관한다. 폴더를 수동 수정하거나 실패 표식을 지우지 않는다.

## 6. GitHub에 결과 기록 올리기

실행이 모두 종료된 뒤 저장소 루트에서 진행한다. 아래 절차는 새 결과 브랜치에 해당 세션 기록만 올린다. `WARMUP_SESSION`, `WARMUP_ROOT`는 3단계에서 설정한 값을 그대로 사용한다.

먼저 기록 폴더의 로그·경로·PC 식별 정보를 확인하고, 공개하면 안 되는 정보가 있으면 업로드 전에 별도로 처리 방법을 정한다. 제공된 도구는 전용 가짜 세션과 DB만 사용하지만 로그에 로컬 경로나 호스트 정보가 포함될 수 있다. 원본 측정 파일을 편집하면 checksum 검증은 깨진다.

```sh
git switch -c "test/warmup-results-$WARMUP_SESSION"
WARMUP_ARCHIVE="performance/warmup/results/$WARMUP_SESSION.tar.gz"
tar -czf "$WARMUP_ARCHIVE" -C "$(dirname "$WARMUP_ROOT")" "$WARMUP_SESSION"
tar -tzf "$WARMUP_ARCHIVE"
ls -lh "$WARMUP_ARCHIVE"
git add -- "$WARMUP_ARCHIVE"
git diff --cached --stat
git commit -m "test(ticketing): 다른 PC 웜업 비교 기록 추가"
git push -u origin "test/warmup-results-$WARMUP_SESSION"
```

압축 파일에는 smoke, 정식 비교, 중단·재시도 기록, 검증 출력, 소스 SHA가 함께 들어간다. JAR, `.env`, 빌드 폴더는 추가하지 않는다. `git add .` 대신 위의 파일 지정 명령을 사용한다. 큰 파일로 업로드가 거절되면 원시 기록을 버려 크기를 맞추지 말고 별도 첨부 방법을 정한다.

GitHub에서 결과 브랜치의 PR을 만들 때 **base를 `test/ticketing-warmup-benchmark`**로 지정한다. CLI를 사용한다면 GitHub CLI 로그인 후 다음처럼 실행할 수 있다:

```sh
gh pr create \
  --base test/ticketing-warmup-benchmark \
  --head "test/warmup-results-$WARMUP_SESSION" \
  --title "test(ticketing): 다른 PC 웜업 비교 결과" \
  --body "단일 인스턴스 웜업 실험의 원시 기록과 검증 출력을 첨부합니다. 성공 및 중단 기록을 함께 보관했으며, 성능 해석은 전체 쌍과 readiness 비용을 확인한 뒤 정리합니다."
```

그때 기준 브랜치가 이미 병합·삭제되었다면 현재 병합 상태를 먼저 확인해 PR base를 정한다. 업로드한 **브랜치명 또는 PR 링크**를 다음 대화에 전달하면 분석을 이어갈 수 있다.

## 7. 결과 해석 기준

`summary.json`과 각 `pair-XX-off/on-summary.json`에 readiness, 초기/정상 상태 p95·p99, 오류 수, 완료 RPS가 있다. `*-requests.jsonl`에는 각 요청의 원시 시간, `*-resources.json`에는 약 1초 간격 CPU/RSS 표본이 있다.

- 각 쌍의 OFF/ON 값과 전체 중앙값·변동을 함께 확인한다. 빠른 한 번만 선택하지 않는다.
- HTTP `latency_ms`와 요청 대기까지 포함한 `schedule_to_finish_ms`/`scheduled_p95_ms`를 함께 읽는다.
- readiness가 늦어지는 비용도 공개한다. 이번 구현의 목표를 “애플리케이션 기동 시간 단축”으로 바꾸지 않는다.
- 새 JVM이지만 DB/OS 캐시는 재사용된다. 게이트웨이 JWT, 실제 예약·좌석 점유, Kafka 처리까지 검증한 실험은 아니다.
- 첫 100건 p99는 표본이 작다. C2 컴파일 보장 또는 순수 JIT 효과라고 단정하지 않는다.
- CPU는 `ps`의 프로세스 수명 평균, RSS 최대는 표본 중 최대다. 순간 최대 CPU·메모리를 정확히 측정했다는 표현은 피한다.
- 검증 통과는 원시 기록의 일관성 확인이다. 개선 여부와 재현성은 전체 결과를 읽고 판단한다. 개선이 없거나 변동이 크면 그 사실을 그대로 남긴다.

# JVM 웜업 실험 인계 — 2026-09-09

## 다음 작업

다른 PC에서 [실행 안내서](../../performance/warmup/README.md)를 따라 smoke를 통과한 뒤 OFF/ON 5쌍을 측정한다. 성공·중단 원본과 검증 출력을 결과 브랜치에 올리고 전체 결과로 초기 지연과 readiness 비용을 분석한다. Kubernetes 작업은 아직 시작하지 않는다.

## 구현·PR 상태

작성 시점 기준 두 PR 모두 OPEN이며 병합하지 않았다. 이후 작업 전에 GitHub 상태를 다시 확인한다.

- [PR #14](https://github.com/l-lyun/truve/pull/14): `feat/ticketing-warmup`, 좌석 읽기 경로 분리, 시작 시 웜업, readiness 전 API 503 처리. base는 `codex/hold-requested-consumer`.
- [PR #15](https://github.com/l-lyun/truve/pull/15): `test/ticketing-warmup-benchmark`, 전용 실험 프로필·측정·검증 도구. base는 `feat/ticketing-warmup`.
- 문서 추가 직전 #15 코드 커밋: `48e03eb653335b9ce2895a4b355d2f46ca8ca36b`.
- 이 인계 문서와 실행 안내서는 사용자의 문서 푸시 요청에 따라 추가했다. 이전의 Markdown 업로드 제외 요청은 이번 재현 문서에는 적용하지 않는다.

## 확인된 것과 아직 남은 것

- 기존 Mac에서 Ticketing 테스트 255개와 Python 테스트 13개 통과, bootJar 빌드 성공.
- 실제 MySQL/Redis에서 웜업 100회 전후 DB·Redis 부작용 점검 통과.
- `ActiveProcessorCount=2` 조건의 짧은 OFF/ON 2회, HTTP 총 60건 실행·원시값 검증 통과.
- 정식 5쌍 비교는 호스트 부하 초과로 사전 점검에서 중단되어 **정식 비교 표본은 0개**다.
- 이전 성공 1회와 중단 4회는 [검증 요약](../../performance/warmup/results/validation-summary.json), [전체 기록 압축](../../performance/warmup/results/validation-20260909.tar.gz)에 보관했다.
- 개선율, 웜업 시간 감소, C2 컴파일 보장은 주장하지 않는다. 짧은 실행의 관측값을 포트폴리오 성능 수치로 사용하지 않는다.

## 이어받을 때 지킬 조건

1. 새 PC에서 깨끗한 소스를 받아 JDK 21로 새 JAR를 빌드하고 해당 실행의 소스 SHA·JAR hash를 보관한다. 측정 사이에는 JAR를 다시 빌드하지 않는다.
2. 기본 5쌍·10 RPS·첫 100건·정상 60초·웜업 100회·JVM 프로세서 수 2 조건을 유지한다. 설정을 바꾸면 별도 실험으로 기록한다.
3. 1분 부하가 논리 CPU 수 × 2를 초과하면 중단한다. 임계치를 낮추거나 높여 기존 실패를 우회하지 않는다. 실패·중단 기록은 보존한다.
4. 기존 DB·Redis·무관한 Docker 컨테이너를 건드리지 않는다. 웜업은 실제 예약이나 좌석 점유 API를 실행하지 않는다. 측정용 조회 HTTP의 기존 heartbeat 동작과 웜업 자체의 부작용 점검은 구분한다.
5. 검증 출력은 실행 폴더 바깥에 기록한다. manifest에 포함된 원본 파일을 변경하지 않는다.
6. 다른 PC 결과는 같은 PC 안의 OFF/ON으로 비교한다. 과거 Mac의 수치와 합치지 않는다.
7. 사용자가 올린 결과 브랜치/PR을 받으면 압축을 별도 폴더에 풀어 검증 도구를 다시 실행하고, 모든 쌍·중단 이유·readiness 비용·HTTP 및 예정 시각 기준 지연을 함께 읽는다.
8. 포트폴리오 문장은 실제 결과에 따라 작성한다. 적합한 주제는 “사전 웜업으로 초기 요청 지연 완화”이며, 반복 측정이 뒷받침되지 않으면 구현 및 실험 사실까지만 표현한다.

사용자는 읽기 쉬운 브랜치명과 적절한 크기의 PR을 선호한다. 이번 요청은 재현 문서 커밋·푸시까지이며, PR 병합은 요청하지 않았다.

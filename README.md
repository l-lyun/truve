# Truve | 뮤지컬 티켓팅 BE 프로젝트

> 팀 프로젝트로 만든 뮤지컬 예매 서비스를 개인적으로 개선하고 있습니다.
> 좌석 선점의 실패 처리, 이벤트 발행 복구, 초기 응답 지연에 집중했습니다.

[원본 팀 저장소](https://github.com/pain22value/back)

대기열부터 좌석 선택·예약·결제까지 이어지는 예매 백엔드입니다. 전체 기능과 협업 기록은 팀 저장소에서 확인할 수 있습니다.

## 서비스 구조

![Truve 서비스 구조와 개인 작업 영역](docs/images/readme/architecture.png)

①~④는 개인 작업 영역이며, Kafka 흐름은 주요 구간만 표시했습니다.

<table align="center">
  <thead><tr><th align="center">모듈</th><th align="center">역할</th></tr></thead>
  <tbody>
    <tr><td align="center"><code>api-gateway</code></td><td align="center">요청 라우팅, JWT 인증 필터</td></tr>
    <tr><td align="center"><code>auth-server</code></td><td align="center">회원·인증·토큰 관리</td></tr>
    <tr><td align="center"><code>queue</code></td><td align="center">대기 순번, 입장 토큰, 입장 대상 선정</td></tr>
    <tr><td align="center"><code>ticketing</code></td><td align="center">티켓팅 세션, 좌석 선점, 예약 상태, Outbox 발행</td></tr>
    <tr><td align="center"><code>payment</code></td><td align="center">결제 승인·취소, 결제 이벤트 처리</td></tr>
    <tr><td align="center"><code>musical</code></td><td align="center">공연·캐스팅·아티스트 정보</td></tr>
    <tr><td align="center"><code>common</code> / <code>common-observability</code></td><td align="center">공통 코드, 로깅·메트릭 설정</td></tr>
  </tbody>
</table>

**주요 기술:** Java 21, Spring Boot, Spring Cloud Gateway, JPA, MySQL, Redis, Kafka, Docker Compose
<br>

## 개인 핵심 작업

### ① Saga 기반 좌석 선점과 보상

**예약 저장이 실패했을 때, 해당 요청이 선점한 좌석만 해제하도록 했습니다.**
DB 저장 여부를 먼저 확인해 이미 예약된 좌석이 잘못 풀리지 않게 했습니다.

![좌석 선점 Saga와 실패 시 보상 흐름](docs/images/readme/seat-hold-saga.png)

- 예약과 선점 요청 이벤트를 같은 트랜잭션에 저장합니다.
- 실패가 확인되면 Lua로 요청 소유권을 대조한 뒤 선점을 해제합니다. 커밋 여부가 불확실하면 재시도·TTL로 처리합니다.
- `Idempotency-Key`로 재요청을 식별하고, 같은 키로 좌석을 바꾸는 요청은 거절합니다.

**코드:** [좌석 선점 Saga](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/ticketing/src/main/java/org/truve/platform/ticketing/service/ticketing/service/SeatHoldSagaService.java) · [후속 이벤트 처리](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/ticketing/src/main/java/org/truve/platform/ticketing/service/ticketing/service/HoldRequestedEventHandler.java)

<br>

### ② Outbox Relay의 이벤트 발행과 장애 복구

**예약 변경과 이벤트를 함께 저장하고, Kafka 발행은 별도 Relay가 맡도록 했습니다.**
발행 중 Relay가 멈추면 다른 Relay가 남은 작업을 이어받습니다.

![Outbox Relay의 이벤트 발행과 중단 작업 복구](docs/images/readme/outbox-relay-recovery.png)

- `SKIP LOCKED`로 작업을 나눠 가져오고, DB 락을 해제한 뒤 Kafka에 발행합니다.
- 작업 소유권이 일치할 때만 완료를 기록해 이전 Relay의 늦은 응답을 막습니다.
- 만료된 작업은 회수·재발행하며, 소비 측에서는 중복 수신에 대비합니다.

**로컬 검증:** 1,000건씩 3회 발행해 누락·중복 0건을 확인했습니다. 선점 후 Relay를 종료한 실험에서도 남은 20건을 다른 Relay가 모두 발행했습니다.

<sub>이벤트 전달·복구 실험이며, 예매 처리량이나 exactly-once 보장 검증은 아닙니다.</sub>

<br>

### ③ JVM 웜업으로 초기 요청 지연 완화

**첫 요청이 느린 구간을 줄이기 위해 실제 API를 반복 호출하며 웜업 효과를 비교했습니다.**
응답시간뿐 아니라 웜업 준비에 드는 시간도 함께 측정했습니다.

<p align="center">
  <img src="docs/images/readme/jvm-warmup-http-flow.png" width="900" alt="HTTP 처리, 세션 검증과 Heartbeat, 좌석 조회, JSON 직렬화로 이어지는 웜업 경로" />
</p>

- **첫 응답:** 로컬 실험에서 500회 호출 후 57.04ms → 9.48ms로 감소했습니다.
- **횟수 선택:** 2,000회는 응답 개선이 거의 없고 준비 시간이 5.40초 → 16.05초로 늘어, 500회를 선택했습니다.

<p align="center">
  <img src="docs/images/readme/jvm-warmup-comparison.png" width="460" alt="HTTP 호출 횟수별 컴파일 기록, 첫 응답과 준비 시간 비교" />
</p>

<sub>localhost 실험 관측값입니다. C1·C2는 JVM 전체 컴파일 결과 생성 건수(nmethod)이며, 환경별 재현성은 별도 확인이 필요합니다.</sub>

별도 기동 웜업은 **좌석 조회·JSON 직렬화**에 적용하고 준비 완료 전 요청을 차단했습니다. 위 HTTP 실험과 달리 세션 heartbeat는 포함하지 않습니다.

**관련 작업:** [기동 웜업 구현 PR #14](https://github.com/l-lyun/truve/pull/14) · [ON/OFF 비교 도구 PR #15](https://github.com/l-lyun/truve/pull/15)
<br>

### ④ Redis 대기열과 진입 제어

**대기열에서 순서를 관리하고, 활성 사용자 수에 맞춰 입장 대상을 선정합니다.**

- Redis ZSET으로 공연별 대기 순번을 관리합니다.
- 입장 대상에게 유효시간이 있는 토큰을 발급합니다.
- 앞쪽은 짧게, 뒤쪽은 길게 조회하도록 순번별 폴링 간격을 안내합니다.

**코드:** [대기열 처리](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/queue/src/main/java/org/truve/platform/queue/service/queue/service/QueueService.java) · [폴링 정책](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/queue/src/main/java/org/truve/platform/queue/service/queue/service/QueuePollingPolicy.java)

## 코드와 문서 살펴보기

- [아키텍처 개요](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/docs/architecture/overview.md)
- [기술 설계 문서](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/docs/trd/README.md)
- [Outbox 측정·재현 안내](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/performance/outbox/README.md)
- [원본 팀 프로젝트](https://github.com/pain22value/back)

<details>
<summary>로컬 실행과 테스트</summary>

Java 21과 Docker Compose가 필요합니다. 실행 전 Compose 파일과 각 모듈의 `application*.yml`에서 필요한 환경변수·프로필을 설정합니다.

```bash
# 로컬 인프라
docker compose -f docker-compose.infra.yml up -d

# 전체 서비스
docker compose up -d --build

# 모듈별 테스트
./gradlew :ticketing:test :queue:test
```

Gateway 기본 포트는 `8080`이며, 실행 후 [로컬 Swagger UI](http://localhost:8080/swagger-ui/index.html)에서 API를 확인할 수 있습니다. 상세 설정은 [전체 서비스 Compose](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/docker-compose.yml)와 [인프라 Compose](https://github.com/l-lyun/truve/blob/278590dd1aec4ed75ef4dfcceac9039ce15ed498/docker-compose.infra.yml)를 참고하세요.

</details>

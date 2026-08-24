# Repository agent instructions

## 프로젝트 개요

- Java 21, Spring Boot 4 기반의 멀티 모듈 Gradle 프로젝트입니다.
- 실행 서비스는 `api-gateway`, `auth-server`, `musical`, `payment`, `queue`, `ticketing`입니다.
- 여러 서비스가 함께 사용하는 코드는 `common`, 공통 로깅과 메트릭 설정은 `common-observability`에 둡니다.
- 로컬 인프라는 `docker-compose.infra.yml`, 전체 애플리케이션은 `docker-compose.yml`을 기준으로 실행합니다.

## 작업 원칙

- 동작을 변경하기 전에 대상 모듈의 `build.gradle`과 관련 `application*.yml`을 확인합니다.
- 서비스 경계를 유지하고 다른 서비스의 데이터베이스나 Repository에 직접 접근하지 않습니다.
- 작업에서 명시하지 않은 공개 API, Kafka 이벤트 계약, 설정 키는 유지합니다.
- 두 개 이상의 모듈에서 실제로 공유하는 코드만 `common`에 추가합니다.
- 자격 증명, 토큰, 개인 키, 실제 환경별 비밀값은 커밋하지 않습니다.
- 사용자의 기존 변경을 보존하고 요청 범위 밖의 파일은 수정하지 않습니다.

## 검증

- 가장 작은 관련 테스트부터 실행합니다.
  - Windows: `.\gradlew.bat :<module>:test`
  - Unix 계열: `./gradlew :<module>:test`
- `common`, 공통 빌드 설정, 서비스 간 계약을 변경하면 영향받는 모든 모듈을 검증합니다.
- 영향 범위가 넓으면 `.\gradlew.bat test` 또는 `./gradlew test`로 전체 테스트를 실행합니다.
- 검증에 필요한 경우가 아니면 Docker 인프라를 실행하지 않습니다.
- 작업 완료 시 변경 내용, 실행한 검증, 실행하지 못한 검증을 함께 보고합니다.

## 문서와 작업 기록

- 장기적으로 유지할 시스템 구조는 `docs/architecture/`에 기록합니다.
- 기능별 기술 설계는 `docs/trd/`, 중요한 기술 결정은 `docs/adr/`에 기록합니다.
- 현재 작업의 구현 계획은 `.agents/plans/`, 다른 스레드로 넘길 진행 상황은 `.agents/handoffs/`에 기록합니다.
- Codex가 자동으로 읽는 저장소 지침은 이 `AGENTS.md`입니다. 다른 문서는 작업에 필요할 때 명시적으로 읽습니다.

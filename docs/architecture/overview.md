# 백엔드 아키텍처 개요

## 기술 구성

- Java 21
- Spring Boot 4
- Gradle 멀티 모듈
- MySQL, Redis, Kafka, LocalStack
- Docker Compose

## 모듈 책임

| 모듈 | 책임 |
| --- | --- |
| `api-gateway` | 외부 요청 진입점, 서비스 라우팅, 인증 필터 |
| `auth-server` | 인증, 계정, 이메일 인증, OAuth |
| `musical` | 공연, 아티스트, 일정, 게시판, 리뷰 |
| `payment` | 결제 승인, 조회, 취소, 웹훅 |
| `queue` | 대기열 진입, 순번 조회, 입장 처리 |
| `ticketing` | 티켓팅 세션, 좌석, 예약, 예매 |
| `common` | 공통 응답, 예외, 영속성, 이벤트 유틸리티 |
| `common-observability` | 공통 로깅과 메트릭 설정 |

## 주요 진입점

- 모듈 구성: `/settings.gradle`
- 공통 빌드 설정: `/build.gradle`
- 로컬 인프라: `/docker-compose.infra.yml`
- 전체 로컬 실행: `/docker-compose.yml`
- CI: `/.github/workflows/`

모듈의 책임이나 서비스 간 통신 구조가 바뀔 때 이 문서를 갱신합니다. 세부 구현처럼 자주 바뀌는 내용은 기록하지 않습니다.

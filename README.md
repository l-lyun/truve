# TRUVE Backend

> 뮤지컬 예매 서비스의 인증, 대기열, 티켓팅, 결제, 공연 정보 조회를 담당하는 백엔드 레포지토리입니다.
> <br>
> 프론트, 보안, AI, 인프라 다양한 파트원들과 원활한 협업 아래 요구사항을 만족시키려 노력중입니다😃

<br>

## 👩‍💻 Developers

|     김도현 <br> [@l-lyun](https://github.com/l-lyun)     |     김예은 <br> [@YeKim1](https://github.com/YeKim1)     |    양승희 <br> [@seungh22](https://github.com/seungh22)    |
| :------------------------------------------------------: | :------------------------------------------------------: | :--------------------------------------------------------: |
| <img height="280" src="https://github.com/l-lyun.png" /> | <img height="280" src="https://github.com/YeKim1.png" /> | <img height="280" src="https://github.com/seungh22.png" /> |

<br>

## 🛠️ 서비스 구조

`truve-backend`는 Spring Boot 기반 MSA 프로젝트입니다.  
인증, 공연 정보, 대기열, 티켓팅, 결제를 서비스 단위로 분리해 예매 트래픽이 몰리는 구간을 독립적으로 제어할 수 있도록 구성했습니다.

```text
back
├── api-gateway   # 외부 요청 진입점, 서비스 라우팅, JWT 필터 처리
├── auth          # 회원가입, 로그인, 토큰 재발급, 이메일 인증, OAuth
├── common        # 공통 응답 포맷, 예외 처리, 공통 설정, 이벤트 유틸
├── musical       # 공연 상세, 캐스팅 일정, 아티스트 좋아요, 공연 정보 조회
├── payment       # 결제 승인, 결제 조회, 결제 취소, 웹훅 처리
├── queue         # 대기열 진입, 순번 조회, 입장 가능 상태 관리
├── ticketing     # 티켓팅 입장, 세션 유지, 좌석 조회, 좌석 선점, 예약 생성
├── docker-compose.yml
└── docker-compose.infra.yml
```

<div align="center">
  <img width="900" alt="image" src="https://github.com/user-attachments/assets/8e180d78-6e7d-4800-8d99-19f22dbd7ea1" />
</div>
<br>
Truve 백엔드는 api-gateway를 요청 진입점으로 사용하여 두고 인증, 공연 정보, 대기열, 티켓팅, 결제 요청을 각 서비스에 라우팅합니다.<br>
  특히 대기열 진입, 티켓팅 입장, 좌석 선점, 결제까지 이어지는 흐름을 단계별로 분리해, 과부하를 안정적으로 제어하는 데 초점을 맞췄습니다.
<br><br>

<details>
<summary><strong>📌 기술 스택 상세 보기</strong></summary>

  ### 💻 Language & Framework

<div align="left">
  <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring Boot-6DB33F?style=for-the-badge&logo=Spring-Boot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring Cloud Gateway-0A3D62?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring Data JPA-59666C?style=for-the-badge&logo=spring&logoColor=white"/>
</div>

### 🗄️ Database & Messaging

<div align="left">
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white"/>
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/Apache Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white"/>
</div>

### ☁️ Infra & DevOps

<div align="left">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker Compose-000000?style=for-the-badge&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS EC2-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS RDS-527FFF?style=for-the-badge&logo=amazonaws&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS S3-569A31?style=for-the-badge&logo=amazonaws&logoColor=white"/>
  <img src="https://img.shields.io/badge/LocalStack-6B7280?style=for-the-badge&logoColor=white"/>
</div>

</details>

<br>

## 🧱 Database

### [ERD](https://www.erdcloud.com/d/67pFvAE2MChLb8Yqu)

- 사용자, 공연, 티켓팅, 결제 도메인을 기준으로 DB를 분리했습니다.
- 서비스 간 직접 결합을 줄이고, 각 도메인의 책임을 명확히 나누는 방향으로 설계했습니다.
- 예매 시점의 읽기/쓰기 부하를 분산할 수 있도록 대기열과 세션 상태는 Redis로 분리했습니다.


## 🔄 핵심 비즈니스 로직

### 1. 티켓팅 트래픽 관리

[대기열 -> 티켓팅 설계 문서](https://www.notion.so/3109e3e335cc80088bc1d9632261e29c?source=copy_link)

### 2. 결제 후 예매 상태
<img width="963" height="571" alt="image" src="https://github.com/user-attachments/assets/580e58a2-3e84-430c-a201-f5a6c177606b" />

<br>

## 📋 주요 기능

### 1. 인증 / 사용자

- 이메일 인증 코드 발송 및 검증
- 자체 회원가입 / 로그인 / 로그아웃
- Access Token / Refresh Token 재발급
- 카카오, 네이버 OAuth 로그인

### 2. 공연 정보

- 공연 상세 조회
- 공연별 캐스팅 일정 조회
- 아티스트 좋아요 등록 / 해제

### 3. 대기열

- 공연별 대기열 진입
- 현재 대기 순번 및 입장 가능 상태 조회
- 활성 티켓팅 인원 기준 입장 대상 선별

### 4. 티켓팅

- 입장 토큰 기반 티켓팅 세션 생성
- heartbeat 기반 세션 유지
- 좌석 조회 및 좌석 임시 선점
- 예약 생성 및 결제 준비 상태 전환

### 5. 결제

- 결제 승인
- 주문 기준 결제 정보 조회
- 결제 취소
- 가상계좌 / 입금 웹훅 처리

<br>

## 📋 Swagger

API-Gateway 기준 Swagger UI:

- [개발 서버](http://api.truve.site/swagger-ui/index.html?urls.primaryName=01-Auth+Service)
- [로컬 실행 시](http://localhost:8080/swagger-ui/index.html)

<br>

## 🚀 실행 방법

### 인프라 컨테이너 실행

```bash
docker compose -f docker-compose.infra.yml up -d
```

### 전체 서비스 실행

```bash
docker compose up -d --build
```

```

### 기본 포트

- Gateway: `8080`
- Auth: `8081`
- Payment: `8082`
- Queue: `8083`
- Ticketing: `8084`
- Musical: `8085`
- MySQL: `3306`
- Redis: `6379`
- Kafka: `29092`
- LocalStack: `4566`

```

<br>

## 🌿 브랜치 전략

### 1. Git Flow

- `main`: 현재 배포 버전
- `dev`: 다음 배포 버전 개발 브랜치
- `feat`: 기능 개발 브랜치
- `hotfix`: 운영 버그 수정 브랜치

모든 작업 브랜치는 최신 `dev` 브랜치에서 생성하고, 작업 완료 후 `dev`로 병합합니다.

### 2. 브랜치 네이밍 규칙

Notion 티켓 ID를 기준으로 아래 형식을 사용합니다.

```text
TYPE/#ID-작업내용
```

예시:

```text
feat/#123-queue-enter-api
fix/#148-ticketing-session-expire
hotfix/#201-payment-cancel-bug
```

### 3. 협업 규칙

- 기능 브랜치 간 직접 merge / rebase는 지양합니다.
- 다른 기능이 필요하면 해당 작업을 먼저 `dev`에 반영한 뒤 가져옵니다.
- PR에는 변경 목적, 영향 범위, 테스트 결과를 함께 기록합니다.

<br>

## 📌 참고 사항

- 인프라와 애플리케이션 설정은 `docker-compose.yml`, `docker-compose.infra.yml`, <br>각 모듈의 `application.yml`을 기준으로 관리합니다.

# Codex 설정

이 디렉터리는 저장소에 공통으로 적용할 Codex 설정을 관리합니다.

- `config.toml`: 프로젝트 범위의 Codex 설정
- `agents/`: 프로젝트 전용 백엔드 서브에이전트
- `/AGENTS.md`: 프로젝트 구조, 작업 원칙, 검증 기준

## 프로젝트 에이전트

| 에이전트 | 역할 | 권한 |
| --- | --- | --- |
| `backend_architect` | JPA, 트랜잭션, MSA, Kafka 중심의 백엔드 설계 | 읽기 전용 |
| `backend_implementer` | 설계와 서비스 경계를 지키는 백엔드 구현 | 쓰기 가능 |
| `backend_test_engineer` | 단위·통합·동시성·장애 시나리오 테스트 | 쓰기 가능 |
| `platform_engineer` | 인프라, 로깅, 메트릭, 운영 설정 | 쓰기 가능 |
| `distributed_system_reviewer` | 장애 전파와 서비스 결합도를 포함한 심층 리뷰 | 읽기 전용 |

에이전트는 부모 작업의 모델을 상속합니다. 개인별 모델 선택, 자격 증명, 장비별 설정은 커밋하지 않습니다.

큰 기능을 구현할 때는 다음처럼 역할과 순서를 명시합니다.

```text
backend_architect가 설계를 검토하고, backend_implementer가 구현한 뒤,
backend_test_engineer가 테스트 공백을 보완해줘. 서비스 간 계약 변경은
distributed_system_reviewer도 검토하고 최종 결과를 하나로 정리해줘.
```

인프라나 관측성 변경은 `platform_engineer`를 별도로 지정합니다. 여러 쓰기 에이전트가 같은 파일을 동시에 수정하지 않도록 구현 순서를 분리합니다.

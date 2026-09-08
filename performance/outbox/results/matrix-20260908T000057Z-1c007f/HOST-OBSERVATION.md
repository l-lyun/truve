# 실험 중 호스트 관찰 — 2026-09-08 KST

자동 메타데이터 수집이 아닌, 실험 중 실행한 읽기 전용 도구 결과의 전사다. 원시 실행 스크립트 `harness.py.txt`는 보존했고, 호스트 load 사전 차단은 이 관찰 이후 현재 스크립트에 추가했다.

09:07 `uptime`:

```text
load averages: 267.90 187.26 156.33
```

`memory_pressure -Q`: 물리 메모리 17,179,869,184 bytes, system-wide memory free 37%.

동시 프로세스 CPU 스냅샷 일부:

| 프로세스 | CPU % |
|---|---:|
| XprotectService | 99.2 |
| diskimagesiod | 81.8 |
| WindowServer | 55.1 |
| Virtualization.VirtualMachine | 38.3 |
| iOS Simulator SpringBoard | 23.2 |

09:08 재확인: load averages 247.05 / 189.14 / 157.92.

이 스냅샷으로 특정 프로세스 하나를 지연의 원인으로 단정하지 않는다. 다만 공유 호스트가 매우 혼잡했고, DB 관찰 시간과 발행 기록 지연 사이에도 큰 차이가 발생해 설정 간 성능 비교를 채택하지 않았다.

식별한 실험 Python PID 62853에 SIGINT만 전달했고 finally가 자식 Relay를 정리했다. 다른 앱·시스템 프로세스와 다른 프로젝트 컨테이너는 종료하지 않았다. 중단 직후 DB는 MATRIX PUBLISHED 6,000 / PENDING 600 / PROCESSING 0이었다. 기존 4,020건은 그대로 보존했다.

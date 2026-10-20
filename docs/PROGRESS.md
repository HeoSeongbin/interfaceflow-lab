# 개발 진행 상태

## 현재 단계

**Phase 7 — 로컬 통합 검증 완료 / 원격 CI 미검증 (2026-10-20).** Phase 6 커밋 9e8b135를 기준으로 문서·실제 코드·검증 경로를 대조했다. Docker 환경에서 Testcontainers 및 fresh Compose/Nginx/브라우저/컨테이너 장애 복구를 실행했다. GitHub Actions 구성은 갱신했지만 이번 작업은 push/원격 workflow 실행을 하지 않았다. 원격 CI까지 완료한 것으로 처리하지 않는다.

## 변경 및 해결한 문제

- Docker Engine 29.8.2에서 Testcontainers 1.21.3이 Docker API 400으로 실패했다. root pom의 Testcontainers를 호환 패치 1.21.4로 명시하여 API override 없이 기본 경로를 통과했다. Spring Boot/Java/MyBatis/업무 로직과 migration 내용은 변경하지 않았다.
- 기존 외부 PostgreSQL/Vite 검증과 별도로 Test-Compose.mjs를 추가했다. 고유 프로젝트명/동적 loopback 포트/빈 volume/임시 암호·키로 기존 Compose 파일을 빌드하고 실제 Nginx·두 Spring JVM·PostgreSQL을 연결한다. 성공/실패 모두 자신의 컨테이너/네트워크/volume만 정리한다. 기존 .env와 사용자 .idea는 보존한다.
- Compose host port를 환경 변수로 선택할 수 있게 했다. 기본 5432/8081/8080/5173과 내부 서비스 주소는 유지한다. 사용자 서비스와 충돌할 때 다른 host port를 지정할 수 있다.
- fresh Integration V1~V7/Target V1~V3 migration 이력과 인덱스 대체를 확인한다. Target 커밋 직후 Integration 컨테이너만 SIGKILL/재시작하여 실제 60초 lease 만료 후 복구를 검증한다. 시간 조작 테스트와 구분한다.
- 동시 HTTP E2E 추가: 지연 처리 중 같은 논리 키 중복 409 REQUEST_IN_PROGRESS, 완료 뒤 최초 UUID/Trace/Attempt의 결과 재사용, 변경 본문 409 PAYLOAD_CONFLICT. Source delivery/Attempt 및 Target business result/receipt 각 한 건을 DB로 대조한다.
- CI backend는 실제 Testcontainers, frontend는 단위/빌드, compose-e2e는 같은 전체 스택/장애 복구 스크립트를 사용한다. Surefire·브라우저·Compose 로그/결과 artifact를 보존한다. 임시 키/암호는 env로 전달하며 결과 파일에 기록하지 않는다.
- README 실행 절차/기능/환경 변수/검증·한계와 현행 아키텍처·상태 모델·복구 sequence를 갱신했다. DEMO.md에 약 7분 촬영 대본/장애 재현/문제 해결을 추가했다. 실제 영상 제작은 하지 않았다.
- 검증 도구의 pg URL/옵션 우선순위로 DB 암호가 누락된 문제를 명시적 host/port/user/password로 수정했다. Windows npm 호출은 고정 cmd 명령으로 실행한다. 성공 JSON은 cleanup까지 끝나야 생성되며 실패 시 이전 성공 파일을 재사용하지 않는다.

## 실제 검증 결과

환경: Windows, Java 21.0.12.1/Maven 3.9.11, 호스트 Node 24.12.0, Docker Desktop 4.95.0/Engine 29.8.2/Compose 5.6.0. 컨테이너 frontend 빌드는 Node 22, backend는 Java 21, DB는 PostgreSQL 17.11이다. 테스트 포트는 동적 loopback 할당이며 사용자 5173/8080/8081 프로세스는 변경하지 않았다.

- **Maven verify 79건 통과**, 실패/오류/skip 0. TEST_DB_URL 미설정, RUN_DB_TESTS=true, Testcontainers 1.21.4가 실제 Docker PostgreSQL 17을 생성·종료한 기본 경로다. Integration 63(DB 26, HTTP 계약 3, Monitoring 2, 엔진 17, 신뢰성 3, Phase 6 단위 2, TraceFilter 3, 전송 7), Target 16(DB 7, HTTP 계약 6, Simulation 3).
- **Vitest 13건 통과**, TypeScript/Vite 배포 빌드와 Playwright 타입 검사 통과.
- **Compose config 및 전체 이미지 build/up 성공**. 빈 전용 volume의 Flyway 양쪽 이력, 두 health UP, Nginx 화면 응답 확인.
- **Chrome desktop 1440px/mobile 390px E2E 34건 통과**, 기존 32 + 동시 접수/본문 충돌/결과 재사용(각 viewport). 실제 Nginx 경유 HTTP·양쪽 DB 대조이며 정상 응답을 mock하지 않는다. 네트워크 중단/HTML 502 피드백만 별도 브라우저 경계 주입이다.
- **실제 컨테이너 crash 복구 통과**: Target 업무 커밋 후 Integration SIGKILL/같은 키·DB 재시작, 실제 60초 lease 만료 뒤 같은 Request/Trace·새 Attempt, 이전 UNKNOWN/최종 SUCCESS, Target 업무 결과 한 건/replay receipt 한 건. 자신이 만든 스택과 volume 정리 완료를 확인했다.
- 새 스크립트의 최초 DB 접속 실패 시에도 own stack/volume 정리가 실행됨을 확인했다. 수정 뒤 정상 경로를 재검증했다. Docker build cache/images는 유지하며 전역 prune/다른 프로젝트 down은 하지 않았다.
- 화면 캡처를 docs/screenshots/phase7-*에 보존했다. 로그/결과는 .tools/phase7-testcontainers-final.log, phase7-frontend.log, phase7-compose.log, phase7-compose-result.json; 브라우저 보고서는 frontend/playwright-report다. 로컬 생성 로그·결과는 Git 제외다.
- actionlint 1.7.12 공식 Docker 이미지로 workflow 정적 검사 통과(exit 0). 정적 검사는 원격 job 실행이 아니다.
- 원격 GitHub Actions, Linux Chromium, 실제 휴대폰/다른 브라우저/스크린리더는 **미검증**이다. 로컬 성공을 해당 환경 통과로 바꾸지 않는다.

## 변경 파일 및 결정

- pom.xml: Docker 29 호환 Testcontainers 패치.
- docker-compose.yml/.env.example: 선택 가능한 loopback host ports.
- scripts/Test-Compose.mjs: 격리 전체 스택·E2E·실제 컨테이너 복구/정리/근거.
- frontend/e2e/console.spec.ts: 동시 요청·저장 결과/충돌/DB 정합성 시나리오.
- .github/workflows/ci.yml: 공통 Compose 검증·backend 결과·artifact, 제한 시간/읽기 권한.
- PROJECT/README 갱신, 기존 API 계약은 보존. docs/ARCHITECTURE/DECISIONS/TEST-SCENARIOS/PROGRESS, DEMO 및 Phase 7 캡처 갱신. API.md는 변경할 계약이 없어 보존했다.
- **ADR-015**: Docker 호환 패치/기본 Testcontainers, 개발 환경과 격리한 실제 Compose 검증, 로컬·원격 근거 분리. ADR-007~014의 짧은 DB 트랜잭션·세 식별자·불변 설정·마스킹·UNKNOWN·lease/fencing 원칙 유지.
- 이번 작업은 커밋·푸시하지 않았다.

## 남은 작업과 완료 경계

- Phase 7 잔여: 원격 CI 실행 결과 확인. 사용자가 push를 요청한 뒤 GitHub Actions 결과를 실제 확인하고 실패가 있다면 해결한다. 새 Phase 번호를 임의로 추가하지 않는다.
- 인증/권한·자동 키 회전/보관 삭제·원본 편집·중앙 로그 수집·세부 단계별 시간·다중 인스턴스/전체 부하·실제 영상 제작은 구현/검증하지 않았다. 다음 기능 범위는 별도 결정한다.
- 가상 Target 프로토콜과 키/기록 보관 범위의 멱등성이다. HTTP exactly-once나 임의 외부 업무 시스템을 보장하지 않는다. 같은 REPLAY_KEY와 기록을 보존해야 하며 임의 삭제 시 보장이 사라진다. 수동 execute는 독립 실험이다.
- 60초 lease 회수/heartbeat 없음, 기본 자동 1회·수동 포함 총 10회, UNKNOWN/DEAD_LETTER 공존, cursor 비snapshot/substring scan 가능성·기존 매핑 한도를 유지한다. Phase 6 SQL 성능은 PERFORMANCE.md의 단일 로컬 측정이며 운영 SLA가 아니다.

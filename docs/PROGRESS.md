# 개발 진행 상황

기록일: 2026-10-16 (Asia/Seoul)

## 현재 Phase

Phase 0 설계 완료. Phase 1 구현과 로컬 Java/HTTP/PostgreSQL/Chrome 검증 완료. 이번 후속 작업에서는 Phase 1 HTTP 오류 경계와 실제 브라우저·DB 검증을 보강했다. Docker Compose/Testcontainers 실행 검증은 여전히 남아 있으므로 Phase 1 전체 검증 완료로 표시하지 않는다. Phase 2 이후 기능은 구현하지 않았다.

## 완료한 작업

- 빈 작업 폴더와 Git 미초기화 상태 확인. 기존 코드·변경 사항 없음.
- 원본 최종 요구사항 PROJECT.md 보존, AGENTS/README/PROGRESS/DECISIONS와 아키텍처·API·테스트 문서 작성.
- Java 21 Maven 멀티모듈(common DTO, integration-server, target-simulator), Spring Boot 3.5.7.
- React TypeScript Vite 전송 콘솔. 샘플·JSON 객체 검사·요청 크기 검사·로딩·오류·결과·다크/라이트·반응형 기본 레이아웃.
- 고정 IF-USER-001 대상 라우팅, RestClient 실제 JSON HTTP 송수신, 기본 응답 계약 검사.
- Trace/Request/Attempt HTTP 헤더, MDC 정리, JSON 로그. 요청 전문은 로그에 출력하지 않음.
- PostgreSQL 서비스별 스키마와 Flyway migration, MyBatis XML Mapper, 요청 결과·수신 메타데이터 영속화.
- 64KB 입력 제한 (Content-Length 및 실제 읽은 크기 모두 검사), 로컬 포트 바인딩, env 기반 암호.
- Actuator health, OpenAPI, Dockerfiles/Compose/Nginx, GitHub Actions, 별도 JVM smoke 스크립트.
- 누락 헤더·잘못된 UUID는 400으로 차단, 표준 MVC 오류의 HTTP 상태/Allow 헤더 보존, 413 JSON 오류와 Trace ID 일관화.
- HTML 게이트웨이 오류와 파싱 불가 JSON의 HTTP 상태·사용자 피드백. 원문 오류 응답은 화면에 그대로 출력하지 않음.
- Playwright 타입 검사 및 실제 두 JVM/DB 기반 전송·식별자 대조, 데스크톱/모바일 너비·테마·키보드 검증. smoke -BrowserTests와 선택한 화면 캡처 보존.
- Docker Compose 전체 스택의 Chromium/DB 검증 CI 작업 구성. Docker backend 빌드도 동일한 Maven Central 설정 사용.

## 테스트 결과

실행 도구: 프로젝트 무시 경로 .tools에 임시 Java 21/Maven/PostgreSQL 17.6을 준비했다. 시스템 서비스 설치는 하지 않았다. 시스템 기본 Java는 26이며 Maven/Docker는 PATH에 없었다.

- Java 21 Maven verify: **22건 성공, 실패/오류/skip 0**. 핵심 서비스 테스트 7건(HTTP 왕복·오류·Timeout·DB 접수 실패·DTO), 양쪽 HTTP 계약 9건, Content-Length 없는 크기 경계/MDC 테스트 2건, 실제 PostgreSQL 통합 4건.
- PostgreSQL 통합: Integration HTTP/실제 DB 상태·미등록 인터페이스 차단, 64KB 제한, Target HTTP/실제 수신 이력 저장, 잘못된 Attempt ID 400 및 수신 기록 0건. TEST_DB_URL 외부 임시 PostgreSQL 경로 사용.
- 별도 JVM smoke: 두 실행 JAR와 실제 PostgreSQL로 SUCCESS/HTTP 200, Request/Trace/Attempt 식별자, 동일 Trace 응답 헤더, Actuator DB UP, OpenAPI 확인 성공.
- frontend Vitest: **5건 성공**. JSON 객체 검사와 JSON/HTML/파싱 오류 응답 처리.
- frontend TypeScript/Vite 배포 빌드: 성공.
- Playwright E2E: **12건 성공**, 별도 TypeScript 검사 통과. 6개 시나리오 × Chrome 데스크톱 1440px / 모바일 너비 390px. 정상 전송의 세 식별자가 Integration/Target DB 기록과 일치. 전송 중 버튼 비활성화·JSON 오류·샘플·네트워크/HTML 오류·테마·키보드·가로 넘침 확인.
- 데스크톱 다크, 모바일 너비 다크/라이트 캡처 직접 시각 확인. 선택 이미지 docs/screenshots 보존. 실제 모바일 기기/별도 모바일 엔진 검증은 아님.
- Testcontainers Docker 경로: 코드 구성, 미실행. CI에서 RUN_DB_TESTS=true로 실행하도록 구성했으나 원격 Actions는 미실행.
- Docker Compose 실행: Docker 미설치로 미실행. 새 compose-e2e CI 작업도 원격 실행하지 않았으므로 성공으로 표시하지 않음.

## 해결한 문제와 결정

- 네트워크 샌드박스의 npm/Maven DNS 차단: 승인받은 다운로드·빌드로 검증.
- 네이티브 Rollup 실행 차단: 승인받은 외부 빌드 성공.
- 로컬 Maven/JDK 21 부재: .tools 내부에 검증용 도구 준비.
- DB 테스트 생성 중 잘못된 문자열 치환: 수정 후 실제 컴파일·DB 테스트 통과.
- 샌드박스 PostgreSQL 초기화 정체: 해당 프로세스를 중단하고 승인된 별도 임시 데이터 폴더로 검증.
- ADR-001~004: 호환 버전, RestClient/트랜잭션 경계, UNKNOWN 상태, 식별자 분리, 서비스별 DB·전문 로그 제외.
- 포괄 예외 handler가 HTTP 415를 500으로 바꾸고 413 JSON을 누락하는 문제를 테스트로 재현한 뒤 수정. Target UUID 구문 검증으로 잘못된 값의 DB 캐스팅·수신 기록을 차단.
- E2E 최초 실행의 DB 연결 사용자 옵션 문제(10건 성공/2건 실패): pg의 connectionString 파싱으로 별도 옵션이 덮이는 문제를 URL 구성으로 수정. 최종 12건 모두 성공.
- ADR-005~006: 표준 HTTP 오류 계약, 실제 브라우저·DB 대조와 오류 주입 검증 범위. Request/Trace/Attempt 의미와 재시도/멱등성 단계 범위는 변경하지 않음.

## 미완료 기능과 제약

고정 인터페이스 외 CRUD, 필드 매핑·Source/Target 필드 스키마, 전문 원문 저장, 설정 버전, 이력 조회·Trace Explorer·대시보드, 장애 시나리오, 멱등성·재시도·복구, 인증은 후속 Phase다. 현재 반복 Request ID는 중복 실행되며 SENDING 중 종료 시 자동 복구되지 않는다. 원본 요구를 삭제하거나 축소한 것이 아니라 단계 범위상 대기 상태다.

## 이번 후속 작업 변경 파일

- 양쪽 서버: `ApiErrors.java`, `TraceFilter.java`, 새 `HttpContractTest.java`.
- Integration: 새 `src/test/java/lab/interfaceflow/integration/TraceFilterTest.java`.
- Target: `ReceiveController.java`, `DatabaseIntegrationTest.java`.
- Frontend: `src/main.tsx`, 새 `src/http.ts`, `src/http.test.ts`, `e2e/console.spec.ts`, `playwright.config.ts`, `tsconfig.e2e.json`, `vite.config.ts`, `package.json`, `package-lock.json`.
- 실행/CI: `scripts/Test-Smoke.ps1`, `docker/backend.Dockerfile`, `.github/workflows/ci.yml`, `.gitignore`, `.dockerignore`.
- 문서: `README.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`, `docs/API.md`, `docs/TEST-SCENARIOS.md`, `docs/screenshots/phase1-desktop.png`, `phase1-mobile.png`, `phase1-mobile-light.png`.
- 최종 요구사항 PROJECT.md와 기존 상태/재시도/멱등성 정책은 변경하지 않았다. Git 저장소가 아직 없어 Git diff 대신 작업 파일과 실행 결과를 직접 대조했다.

## 다음 작업

1. Docker가 있는 환경에서 Compose config/build/up, Testcontainers 경로, Compose 기반 E2E 검증. 필요 시 새 compose-e2e CI를 실행하고 실제 결과 기록. Docker 설치/엔진이 없는 현재 환경에서 이 항목을 완료로 처리하지 않음.
2. Phase 1 미검증 항목을 해소한 뒤 Phase 2: 인터페이스/버전/매핑/검증 스키마와 API 계약 확정, CRUD와 전문 변환·이력 구현.

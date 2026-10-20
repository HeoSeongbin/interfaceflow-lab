# InterfaceFlow Lab

두 독립 Spring Boot 서버의 실제 HTTP 통신으로 전문 검증·매핑·장애·재시도·중복 처리·복구를 재현하고 추적하는 개발용 플랫폼입니다. 가상 데이터만 사용합니다.

현재 **Phase 7 — 통합 검증**을 진행했습니다. 로컬 Testcontainers와 전체 Compose 스택 검증 결과는 [PROGRESS](docs/PROGRESS.md)에 기록합니다. GitHub Actions는 구성했으며 원격 실행은 별도 미검증입니다.

```mermaid
flowchart LR
    UI[React Console :5173] --> N[Nginx /api proxy]
    N --> API[Integration :8080]
    API -->|실제 HTTP + 세 식별자| TARGET[Target :8081]
    API --> I[(PostgreSQL integration)]
    TARGET --> T[(PostgreSQL target)]
```

Java 21 / Spring Boot 3.5.7 / MyBatis XML 3.0.5 / PostgreSQL 17 / Flyway / React 19.2 / TypeScript 5.9 / Vite 7.1.12를 사용합니다. 설계 이유와 범위는 [ADR](docs/DECISIONS.md), 실제 DB 모델과 복구 흐름은 [아키텍처](docs/ARCHITECTURE.md), 전체 요구사항은 [PROJECT](PROJECT.md)를 참고하세요. PROJECT의 전체 요구사항이 구현 완료 목록은 아닙니다.

## 빠른 실행 · Docker Compose

Docker Desktop/Engine을 실행하고 Compose v2 이상을 준비합니다. 아래는 PowerShell 예시입니다. 기존 .env가 있다면 보존하고 값을 확인합니다.

```powershell
Copy-Item .env.example .env
# .env의 DB_PASSWORD를 로컬용 값으로 변경
# 한 번 생성한 키는 DB와 함께 보존. 실제 .env는 커밋하지 않음.
$replayKey = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
(Get-Content .env -Raw).Replace('replace-with-a-generated-base64-key', $replayKey) | Set-Content .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs -f integration target
```

DB 마이그레이션과 애플리케이션 시작이 끝난 뒤 다음 주소를 확인합니다.

| 용도 | 기본 주소 |
| --- | --- |
| 콘솔 | http://localhost:5173 |
| Integration health | http://localhost:8080/actuator/health |
| Target health | http://localhost:8081/actuator/health |
| Integration OpenAPI | http://localhost:8080/swagger-ui/index.html |
| Target OpenAPI | http://localhost:8081/swagger-ui/index.html |

두 health의 status가 UP이면 콘솔에서 IF-USER-001 샘플을 전송합니다. `docker compose down`은 DB volume을 유지합니다. 포트 충돌 시 .env의 FRONTEND_PORT/INTEGRATION_PORT/TARGET_PORT/DB_PORT를 바꾸세요. 기본은 5173/8080/8081/5432이며 컨테이너 사이 주소는 유지됩니다. 포트에 맞게 위 URL도 바꿉니다.

## 개별 개발 실행

Java 21, Maven 3.9+, Node 22.12+, PostgreSQL 17이 필요합니다. `.env` 설정 후 `docker compose up -d db`로 DB만 시작할 수도 있습니다. 각 Java 터미널에서 동일 DB 설정을 사용하고 Integration은 같은 REPLAY_KEY를 유지합니다.

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/interfaceflow'
$env:DB_USER='interfaceflow'
$env:DB_PASSWORD='로컬 설정 값'
$env:REPLAY_KEY=(Get-Content .env | Where-Object { $_ -match '^REPLAY_KEY=' }) -replace '^REPLAY_KEY=', ''
mvn -B -s .mvn/settings.xml clean verify
java -jar backend/target-simulator/target/target-simulator-0.1.0-SNAPSHOT.jar
# 같은 DB 환경 변수를 설정한 별도 터미널
java -jar backend/integration-server/target/integration-server-0.1.0-SNAPSHOT.jar
# 별도 터미널
cd frontend
npm ci
npm run dev
```

Integration TARGET_URL 기본은 http://localhost:8081, Vite API_PROXY_TARGET 기본은 http://localhost:8080입니다. DB host port를 바꿨다면 DB_URL을 수정합니다. `RETRY_CONCURRENCY`는 background 작업자 수(기본 2, 허용 1~4)이며 최초 API 전송의 전체 동시성 제한은 아닙니다. 개발 프록시와 Nginx 모두 same-origin /api를 사용합니다.

## 콘솔에서 확인할 기능

- **전문 전송:** Source 검증 → 필드 매핑 → Target 검증 → 실제 HTTP. 미리보기는 HTTP/이력을 만들지 않습니다. userId/userName 필수 문자열을 employee_id/employee_name으로 매핑하며 선택 email은 진단 이력에서 마스킹됩니다.
- **인터페이스 관리:** CRUD·비활성화·버전, Source 등록/Target 허용 목록, 규칙 편집. 수정은 현재 version을 요구하고 충돌은 409입니다. 요청은 최초 불변 버전을 사용합니다.
- **전송 이력:** 검색·상태·기간·이전/다음 커서, 내부 UUID 상세, 마스킹된 변환 전후 JSON Pointer leaf 비교. ∅는 필드 부재이며 비교 한도 초과는 안내합니다. 커서는 페이지 간 snapshot이 아니므로 상태 변경은 반영될 수 있습니다.
- **Trace Explorer:** 같은 Trace의 요청·단계별 이벤트·개별 Attempt를 조회합니다. Target 내부 단계는 구조화 로그에 있으며 Explorer에 중앙 수집하지 않습니다.
- **대시보드:** 실제 DB KPI/기간 필터/UTC 시간별 차트. UNKNOWN은 실패와 구분합니다. 평균은 최근 처리 기록이며 전체 재시도 경과시간과 다릅니다. 통계 기간은 시작 포함/종료 제외·최대 31일입니다.
- **Simulation Lab:** SUCCESS, HTTP_400/500, DELAY/TIMEOUT, INVALID_RESPONSE, FAIL_THEN_SUCCESS, DUPLICATE, CONNECTION_FAILURE. 요청 범위/유효기간/수신 한도가 있습니다. 일반 수동 execute는 독립 실험, **재시도 정책으로 실행**은 같은 요청의 신뢰성 실험입니다.
- **Retry Center:** 실행 상태·마지막 결과·시도 수·예약 시각, 허용 오류의 수동 추가 예약. 자동 한도는 최초 포함 1~5(샘플 기본 1), 수동 포함 총 10회입니다. 400/409/검증·매핑 실패는 새 전문/새 Request ID로 전송합니다.

객체 점 경로/배열 값 전체 복사, COPY/STRING/NUMBER/BOOLEAN/DATE, 기본값/Null 정책, 엄격한 LocalDate/LocalDateTime/RE2 검증을 지원합니다. 배열 인덱스·와일드카드·사용자 스크립트·시간대 변환은 지원하지 않습니다. 상세 계약은 [API](docs/API.md)에 있습니다.

## 재시도·멱등성·불확실성

Request ID는 `(interfaceId, sourceSystem, scope)` 안의 논리 키, Trace ID는 상관관계, Attempt ID는 실제 HTTP 시도입니다. 일반 scope는 normal, 신뢰성 실험 scope는 시나리오 UUID입니다. 같은 키/같은 전문의 완료 요청은 최초 UUID/Trace/저장 결과를 재사용하고 진행 중 중복은 409, 다른 전문도 409입니다. 재시도는 같은 Request/Trace/버전과 새 Attempt를 사용합니다.

Target은 업무 결과를 지연/응답 손실 전에 짧은 트랜잭션으로 커밋하고 고유키/hash가 같은 재전달에는 저장된 OK 결과를 재사용합니다. Integration은 Target의 interfaceflow-v1 capability를 먼저 확인합니다. 외부 HTTP는 DB 트랜잭션 밖에서 실행합니다. I/O·5xx·응답 계약 오류는 UNKNOWN이며 확정 실패로 바꾸지 않습니다. 실행 state WAIT/LEASED/DONE/DEAD_LETTER와 마지막 요청 결과는 별개입니다.

원본/고정 설정은 외부 32-byte base64 REPLAY_KEY로 AES-256-GCM 보호하고 공개 상세에는 마스킹 snapshot만 제공합니다. 키 누락은 신규 접수 503, 키 손실은 원본 복구 불가입니다. 서버 중단은 실제 60초 lease 만료 후 예산 안에서 복구하고 소유 token/만료 검사로 늦은 완료를 차단합니다. HTTP exactly-once나 임의 외부 업무 시스템의 멱등성을 보장하지 않습니다.

## 자동 검증

| 검증 | 명령/조건 | 로컬 실행 근거 |
| --- | --- | --- |
| 단위·HTTP 계약 | `mvn -B -s .mvn/settings.xml verify` | DB 제외 기본 경로 |
| 실제 Testcontainers PostgreSQL | 아래 RUN_DB_TESTS 설정, TEST_DB_URL 미설정 | Maven 79건, 실패/오류/skip 0 |
| Frontend | frontend에서 `npm ci`, `npm test`, `npm run build` | Vitest 13건·타입/배포 빌드 |
| 전체 Compose·Nginx·DB·E2E·복구 | 루트 `node scripts/Test-Compose.mjs` | desktop/mobile 34건 + 컨테이너 강제 종료 복구 |
| SQL 성능 | `ALLOW_PERF_TEST=true` 후 Test-QueryPerformance.mjs | [10만 가상 행 측정](docs/PERFORMANCE.md), 운영 SLA 아님 |
| GitHub Actions | push/PR 시 실행하도록 구성 | 원격 미실행 |

```powershell
$env:RUN_DB_TESTS='true'
$env:REPLAY_KEY=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
mvn -B -s .mvn/settings.xml verify
# frontend 의존성과 Chrome이 설치된 상태에서 저장소 루트
node scripts/Test-Compose.mjs
```

Testcontainers 기본 경로는 Docker PostgreSQL 17을 생성·종료하며 Testcontainers 1.21.4로 Docker 29를 검증했습니다. Docker 없이 폐기 가능한 외부 테스트 DB를 사용하려면 TEST_DB_URL을 지정합니다. 이 경로는 사용자 interfaceflow/암호 local-test를 기대합니다. 실제 운영 DB에 실행하지 마세요.

Test-Compose는 기본 .env를 수정하지 않고 고유 프로젝트·동적 loopback 포트·새 DB volume·임시 암호/키를 사용합니다. 빌드·fresh migration·34개 브라우저 테스트 후 Target 커밋 직후 Integration을 SIGKILL/재시작하여 실제 lease 복구와 DB 정합성을 확인합니다. 자신이 만든 자원만 정리합니다. 최초 이미지/의존성 다운로드에는 네트워크와 시간이 필요합니다. 성공 결과는 정리까지 끝난 뒤 `.tools/phase7-compose-result.json`, 로그는 `.tools/phase7-compose.log`, 브라우저 보고서는 frontend/playwright-report에 남습니다.

기본 브라우저는 설치된 Chrome입니다. Edge는 PLAYWRIGHT_CHANNEL=msedge, `npx playwright install chromium` 후 Chromium은 PLAYWRIGHT_CHANNEL=chromium을 사용합니다. CI는 Linux Chromium이므로 로컬 Windows Chrome 성공과 구분합니다. 테스트 DB 자격증명은 브라우저 번들에 포함하지 않습니다.

기존 JVM 검증도 유지합니다. 빌드한 JAR와 DB_URL/DB_USER/DB_PASSWORD/REPLAY_KEY를 준비한 뒤 `./scripts/Test-Smoke.ps1 -BrowserTests` 또는 `node scripts/Test-Recovery.mjs`를 실행합니다. Java 21 경로는 각각 -Java/RECOVERY_JAVA로 지정합니다. 테스트 포트와 같은 키를 확인하세요. 상세 절차는 [검증 시나리오](docs/TEST-SCENARIOS.md), [실행·장애 데모 및 문제 해결](docs/DEMO.md)에 있습니다.

## 화면과 남은 범위

[대시보드](docs/screenshots/phase7-dashboard-desktop.png) · [모바일 대시보드](docs/screenshots/phase7-dashboard-mobile.png) · [이력·전문 비교](docs/screenshots/phase7-history-desktop.png) · [Retry Center](docs/screenshots/phase7-retry-mobile.png). Compose E2E에서 생성한 가상 데이터 화면입니다.

인증/권한·자동 키 회전/보관 삭제·재처리 원본 편집·중앙 로그 수집·세부 단계 실행시간·다중 인스턴스 부하/전체 처리량·실제 휴대폰/스크린리더 검증은 남아 있습니다. 고유키/업무 결과 삭제나 키 손실은 재처리·중복 방지 보장에 영향을 줍니다. 현재 localhost 개발용이며 전체 운영 완성으로 간주하지 않습니다. Phase 7 이후 새 Phase를 임의로 시작하지 않습니다. 원격 CI 결과 확인과 후속 범위는 사용자와 정합니다.

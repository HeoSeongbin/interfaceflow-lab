# 검증 계획

## Phase 1

1. `mvn -B -s .mvn/settings.xml verify`: DTO 유효성, 실제 TCP HTTP 왕복과 Trace 헤더 전달, 대상 HTTP/네트워크 오류 분류. `RUN_DB_TESTS=true`를 지정하면 MyBatis XML/migration PostgreSQL 통합 테스트를 포함한다. 기본 DB 경로는 Testcontainers이고 TEST_DB_URL을 지정하면 테스트 전용 외부 DB를 사용한다.
2. `cd frontend; npm ci; npm test; npm run build`: JSON 객체 검사와 정적 타입/배포 번들.
3. `docker compose up --build -d`: 세 서비스와 DB를 실행한다. 두 health endpoint가 UP인지 확인한다.
4. 콘솔에서 샘플 전송 → SUCCESS, 응답 Trace ID 확인. 같은 trace_id로 integration.if_request와 target.if_target_receipt를 조회한다.
5. 미등록 Interface ID → 400, target 수신 기록 증가 없음. 잘못된 JSON → 400.
6. target 중단 → 요청 UNKNOWN / TARGET_IO_ERROR. target 재시작 후 새 요청 → SUCCESS.
7. 동일 Request ID 재전송은 현재 별도 실행된다는 제약을 확인한다. 멱등성이 구현된 것으로 해석하지 않는다.
8. UI 로딩/오류 상태, 키보드 포커스, 좁은 화면, 다크/라이트 표시를 확인한다.

## Phase 1 후속 자동 검증

- `HttpContractTest`(양쪽 서버): 잘못된 JSON·누락 헤더·Attempt UUID·미지원 Content-Type/메서드, 413 JSON, Trace ID, DB/서비스 접근 차단.
- `TraceFilterTest`: Content-Length가 없는 65537바이트 요청은 413, 정확히 65536바이트는 downstream에 그대로 전달, MDC 정리.
- Target PostgreSQL 통합 테스트: 잘못된 Attempt ID는 HTTP 400이며 해당 Request ID 수신 기록은 0건.
- `frontend/src/http.test.ts`: JSON 성공/오류 보존, HTML 502 및 파싱 불가 JSON의 안전한 사용자 피드백.
- `scripts/Test-Smoke.ps1 -BrowserTests`: 두 실행 JAR + 실제 PostgreSQL + Vite 프록시 + Chrome을 연결한다. DB_URL/DB_USER/DB_PASSWORD와 Java 21 필요.
- Playwright 6개 시나리오 × 2개 뷰포트: 실제 전송과 양쪽 DB 식별자 대조, 로컬 JSON 오류/샘플, 전송 중 버튼 비활성화, 네트워크 실패, HTML 게이트웨이 오류, 테마/키보드/가로 넘침 검사.
- 정상 응답은 실제 서버 결과다. 네트워크 실패와 HTML 502만 브라우저 경계에서 주입한다. 모바일 뷰포트 검증이며 실제 모바일 기기/모바일 브라우저 엔진 검증은 아니다.
- 캡처 이미지는 `frontend/test-results`, 실패 trace와 HTML 보고서는 `frontend/playwright-report`에 저장한다. 선택한 검증 화면은 `docs/screenshots`에 보존한다.
- `compose-e2e` CI는 Compose 스택과 별도 Chromium/DB 검증을 실행하도록 구성했다. 구성 자체와 실제 원격 실행 성공을 구분한다.

## Phase 2 검증 (실행 완료)

- PayloadEngineTest: 입력 불변·결정성, 중첩 매핑/기본값/필드 제거, 스칼라 변환, 누락/타입/날짜/Null, Source/Target 단계, 문자열/숫자/Enum/pattern, 잘못된 경로/충돌/미지원 RE2, 마스킹과 이름 변경, 직렬화된 Null, 출력 크기.
- 실제 PostgreSQL: V2 업그레이드, 미리보기 부작용 없음, 매핑된 HTTP 본문, 단계별 실패 저장/미전송, 불변 버전/stale 충돌/동시 변경, HTTP 대기 중 설정 업데이트, 삭제 정책, SOURCE 등록/TARGET 제한, 전문 마스킹, 논리 ID 반복의 별도 기록/페이지, 정밀 소수 범위.
- E2E: 기존 6개 + 미리보기/Source 실패/인터페이스 관리 전체 흐름의 3개 = 9개 시나리오 × 2개 viewport, 총 18건.
- 인터페이스 관리 흐름은 실제 UI로 등록 → v2 수정 → 해당 정의 선택 → 매핑 전송 → 이력 상세 → 삭제 대신 비활성화를 수행한다.
- Maven 51, Vitest 5, Playwright 18 통과. Docker/원격 CI는 별도 미검증 상태다.

## Phase 3 이후

PROJECT.md 18절의 12개 시나리오를 순서대로 자동화한다. 특히 timeout 후 target 처리 성공, 동일 키/다른 본문 충돌, 동시 중복 요청, 재시도 3번째 성공(Trace 동일/Attempt 상이), 처리 중 서버 종료 복구는 실제 DB/HTTP 기반으로 검증한다. 장애 API와 재시도는 현재 미구현이다.

## Phase 3 검증 항목

- 실제 PostgreSQL V3 migration과 정상 HTTP 전송의 8개 순서 이벤트/Attempt 1/식별자 일치/종료시간.
- Source 실패: 접수·설정 조회·실패 이벤트만, HTTP/Attempt 없음.
- 완료 요청의 잘못된 SENDING 전이 거절 후 추가 Attempt/이벤트 없음.
- 공유 Trace의 복수 내부 UUID, 기간 시작 포함/종료 제외, DB 집계 상태 합계.
- 잘못된 Trace/미존재/역전 기간 오류, 단위 테스트의 기본 24시간과 최대 31일.
- 실제 Chrome desktop/mobile Trace 검색·시도 표시·대시보드 DB 통계와 화면 가로 넘침 검사.

실제 실행 여부와 결과는 PROGRESS에 기록한다.

## Phase 4 검증 항목

- 단위: 실패 예산/중복 순번 결과, 만료/관련 옵션의 제한, 비활성 서비스의 DB 미접근.
- 실제 PostgreSQL: Target V2/Integration V4 migration, scope/만료/20회 수신 한도 거절 후 counter/receipt 미변경, 병렬 6회에서 지정 2회만 500과 고유 순번, 시나리오 간 counter 격리, 실제 연결 실패 UNKNOWN 기록, 잘못된 TIMEOUT 접수 롤백.
- 실제 두 JVM/Chrome: 9가지 시나리오의 HTTP/결과/식별자/Trace 연결, FAIL_THEN_SUCCESS와 DUPLICATE 반복 실행, Target receipt 확인, Timeout은 receipt 존재·연결 실패는 미존재, 동일 논리 ID 일반 전송 성공, scope 불일치 409. Simulation Lab 등록/실행/목록/결과와 desktop/mobile 가로 넘침 검사.
- 기존 매핑·버전·동시 수정·마스킹·Trace·대시보드 및 HTTP 오류·입력 제한 회귀 유지.

실행 결과와 미검증 환경은 PROGRESS에 기록한다.

## Phase 5 신뢰성 검증

- Canonical JSON: 객체 키/동등 숫자 표기 정규화, 배열 순서·타입 차이 보존. Replay: 임의 nonce, UUID AAD 인증, 키 누락/다른 UUID 거절. 오류 분류/backoff 한도 단위 검사.
- 실제 DB: 동일 논리 키의 동시 접수 409, 최종 결과 UUID/Trace/헤더 재사용, 다른 전문 409, HTTP 대기 중 잠금 비유지. 암호문/조회 정보의 원문 미노출.
- lease 만료 복구: 이전 Attempt UNKNOWN, 새 UUID/같은 Trace, 오래된 token의 완료 거절. 자동 한도 소진, 병렬 수동 예약 하나만 성공, 성공 후 추가 재시도 거절.
- Target: 동일 키 병렬 6회에서 업무 결과 한 건/추가 replay receipt, 다른 본문 409, 다른 출처의 같은 Request ID는 독립 결과.
- Capability 미확인 시 업무 POST 없음·DEAD_LETTER, 프로토콜 복구 후 수동 실행 성공.
- 실제 두 JVM·Chrome desktop/mobile: FAIL_THEN_SUCCESS 자동 재시도, TIMEOUT/INVALID_RESPONSE의 저장된 업무 결과 재사용, UUID/Trace 유지·Attempt 증가·Target 업무 한 건. Retry Center의 수동 추가 예약·성공 후 비활성·모바일 화면 확인. 기존 수동 실험과 정상 전송·매핑·이력·Trace·통계 회귀 유지.
- `node scripts/Test-Recovery.mjs`: Target 업무 커밋 직후 Integration JVM 강제 종료, 재시작·실제 60초 lease 만료 후 성공, Attempt UNKNOWN→SUCCESS, 같은 Trace와 업무 결과 한 건을 실제 DB로 확인. 자신이 만든 JVM만 종료.

Docker/Testcontainers/원격 CI 실행 여부는 PROGRESS와 구분한다. DB lease 시간을 앞당기는 통합 테스트와 실제 프로세스 재시작 검증은 별도 근거다.

## Phase 6 회귀 검증

- 커서: 동일 timestamp 12건을 5건씩 조회, 중복/누락 없음, 조회 중 최신 삽입의 페이지 이동 없음. 잘못된 커서·필터 변경·size 초과 400.
- 실제 DB due: 만료 lease 포함/유효 lease 제외, claim과 완료 후 정리. 제한 풀은 latch로 peak 2/모든 작업 완료와 설정 0/5 거절 검증.
- JSON 비교: 부재/null/false/0/타입/객체 순서/배열/JSON Pointer escape/절단. UTC 차트의 빈 시간/반개구간/최대 기간 검증. API 오류의 HTTP/code/Trace 보존 검증.
- Chrome: 실제 12건 전송 후 커서 앞뒤 이동·마스킹 비교, 키보드 본문 이동·실제 차트·라이트 테마·모바일 페이지 overflow 검증. 기존 HTTP/DB/재시도/복구 회귀 유지.
- SQL 성능 재현과 해석 범위: PERFORMANCE.md. Docker/Testcontainers/원격 CI는 실행 전까지 미검증으로 둔다.

## Phase 7 통합 검증 경로

1. Docker 엔진 실행 후 RUN_DB_TESTS=true/REPLAY_KEY를 설정하고 Maven verify. TEST_DB_URL을 설정하지 않아 실제 PostgreSQL Testcontainers 기본 경로를 사용한다. 양쪽 DB·HTTP 계약·상태/멱등성/동시성·마이그레이션을 실행한다.
2. frontend npm ci/test/build, Playwright 타입 검사. 정상 응답을 모의하지 않는 실제 E2E 17개 시나리오 × desktop/mobile = 34건이다. 새 동시성 시나리오는 신뢰성 DELAY 처리 중 중복 409, 완료 뒤 같은 UUID/Trace/Attempt의 결과 재사용, 변경 전문 409, Source 요청/Attempt와 Target 업무/receipt 각 한 건을 확인한다.
3. 루트 `node scripts/Test-Compose.mjs`: 새 Docker volume·독립 두 JVM·실제 Nginx 프록시에서 위 E2E, fresh Integration V1~V7/Target V1~V3 migration, 실제 컨테이너 SIGKILL/restart·60초 lease 만료·이전 UNKNOWN/새 SUCCESS Attempt·Target 업무 결과 한 건 검증. 로그/브라우저 보고서와 성공 JSON을 생성하고 자신의 자원을 정리한다.
4. GitHub Actions는 같은 명령을 실행하도록 구성했다. 로컬 통과와 원격 워크플로 실행은 별개다. 실제 결과는 PROGRESS.md, 재현 절차/기술적 문제/데모 대본은 DEMO.md를 따른다.

이 문서의 Phase 1~6 절은 당시의 기록이다. 동일 Request 반복이 독립 실행이었다는 초기 설명은 현재 일반/신뢰성 경로에 적용되지 않는다. 수동 execute만 독립 실험을 유지한다. Docker/장애 기능 미구현이라는 과거 문구의 현행 여부는 PROGRESS를 기준으로 판단한다.

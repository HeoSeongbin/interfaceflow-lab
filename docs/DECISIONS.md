# 기술적 결정

## ADR-001 · 2026-10-16 · 런타임과 의존성

상황: Java 21 요구와 안정적인 Spring/MyBatis 조합이 필요하다. 선택지: Boot 4 / MyBatis 4 또는 Boot 3.5 / MyBatis 3.0. 결정: Java 21, Boot 3.5.7, MyBatis starter 3.0.5, PostgreSQL 17, springdoc 2.8.14. Boot 3.5는 Java 21을 지원하고 MyBatis 3.0은 Boot 3.2~3.5 호환 범위다. 최신 메이저 도입보다 요구사항과 호환성 범위를 우선한다. 패치 업데이트는 별도 빌드 검증을 거친다.

Frontend: React 19.2, TypeScript 5.9, Vite 7.1, Vitest 3.2. Node 22.12+를 사용한다. 초기 한 화면에는 Router/전역 상태/Query 라이브러리가 필요하지 않아 도입하지 않는다. Tailwind·폼·차트는 Phase 2~6 실제 용도에 맞춰 도입한다. 관련 코드: pom.xml, frontend/package.json.

근거: [Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [MyBatis compatibility](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/), [Vite requirements](https://vite.dev/guide/).

## ADR-002 · 2026-10-16 · HTTP와 트랜잭션

선택지: WebClient 비동기 / RestClient 동기. 결정: 단순 동기 요청 흐름에 RestClient + JDK HttpClient를 사용한다. 서비스 singleton에서 클라이언트를 재사용해 JDK 연결 풀을 이용한다. 연결·읽기 Timeout 기본 3초, 자동 재시도 없음. DB insert 커밋 → HTTP → 결과 update 커밋으로 분리한다. 장점은 트랜잭션 잠금 시간을 줄이는 점이며, 중간 프로세스 종료 시 SENDING이 남는 단점이 있다. Phase 5에서 lease 기반 복구를 구현한다. 관련 코드: TransmissionService.

## ADR-003 · 2026-10-16 · 식별자·불확실성·단계 범위

결정: 요청 UUID/논리 Request ID/Trace ID/Attempt ID를 분리한다. I/O 오류·5xx·응답 계약 오류는 UNKNOWN으로 보수적으로 분류한다. Phase 1에서 멱등성·재시도를 구현하지 않으며 UI와 README에 명시한다. 향후 복합 UNIQUE(interface, source, requestId), canonical JSON hash, target 저장 결과로 중복 처리를 막는다. Exactly-once 전달을 보장한다고 주장하지 않는다. 관련 코드: Contracts, migrations, TransmissionService.

## ADR-004 · 2026-10-16 · DB 소유권과 로그

선택지: 공용 schema / 서비스별 schema. 결정: 한 PostgreSQL 인스턴스 내 integration/target schema와 독립 Flyway 이력을 사용한다. 포트폴리오 실행 비용은 줄이고 테이블 소유권은 분리한다. common은 DTO만 공유한다. 전문·응답 원문은 초기 DB와 로그에 저장하지 않아 민감정보 노출을 최소화한다. Phase 2 저장 도입 시 마스킹·크기·보관기간을 함께 구현한다. MDC는 필터에서 정리하고 구조화된 JSON 로그에 traceId를 포함한다. 비동기는 아직 없으며 향후 도입 시 TaskDecorator로 context 전파가 필요하다.

## ADR-005 · 2026-10-16 · HTTP 오류와 식별자 검증 경계

상황: 기존 포괄 Exception handler가 누락 헤더와 미지원 미디어 타입 같은 요청 오류도 500으로 바꾸었다. 413은 컨테이너 오류 페이지에 의존해 JSON 오류 계약이 깨졌고, Attempt ID 정규식은 36개의 하이픈도 허용했다.

선택지: 예외별 수동 목록 확대 / Spring MVC 표준 예외 처리 확장. 결정: 양쪽 ApiErrors가 ResponseEntityExceptionHandler를 확장하여 원래 HTTP 상태·Allow 등 헤더를 보존하고 code/traceId를 제공한다. 필터는 413 JSON을 직접 작성한다. Target은 DB 접근 전 정규 UUID 구문을 검증한다. Request/Trace/Attempt ID의 의미와 전송 상태 정책은 변경하지 않는다.

이유·영향: 호출자 오류를 서버 장애로 오인하지 않고 잘못된 식별자로 인한 DB 오류를 방지한다. common은 DTO만 유지하여 웹 계층 공유 결합을 피한다. 두 서버의 오류 처리 구현이 중복되는 단점은 각 서버 HTTP 계약 테스트로 관리한다. 관련 코드: 양쪽 ApiErrors/TraceFilter, ReceiveController, HttpContractTest, 실제 PostgreSQL invalidAttempt 테스트.

## ADR-006 · 2026-10-16 · Phase 1 브라우저·DB 검증

상황: Java/DB 왕복은 검증했지만 React의 실제 전송과 좁은 화면은 미검증이었다. 이 PC에는 Docker가 없다.

선택지: 화면 목업만 검사 / 실제 두 JVM·DB에 브라우저 연결. 결정: Playwright 1.56.1을 테스트 전용으로 추가하고 설치된 Chrome에서 데스크톱(1440px)·모바일 너비(390px)를 검사한다. pg 8.16.3도 테스트 전용으로 사용하여 정상 전송의 Request/Trace/Attempt ID와 SUCCESS를 양쪽 테이블에서 직접 대조한다. 정상 성공 응답은 대체하지 않는다. 네트워크 중단·HTML 502 표시만 별도 브라우저 경계 테스트에서 주입한다.

이유·장단점: 실제 통신과 영속 기록을 함께 검증하며 UI 오류 상태도 결정적으로 검사할 수 있다. DB 접속 권한이 있는 폐기 가능한 테스트 환경이 필요하므로 공개 운영 DB에는 실행하지 않는다. Vite API_PROXY_TARGET은 개발 프록시 설정이며 브라우저에서 임의 대상 URL을 지정하는 API가 아니다. smoke -BrowserTests는 프로세스와 임시 환경 변수를 정리한다.

Docker Compose E2E CI 작업은 별도로 구성했으며, 로컬 Chrome 검증을 Docker/Testcontainers/원격 CI 성공으로 간주하지 않는다. Docker 검증이 남아 있으므로 Phase 2로 진행하지 않는다. 관련 코드: frontend/e2e, playwright.config.ts, scripts/Test-Smoke.ps1, ci.yml.

근거: [Playwright configuration](https://playwright.dev/docs/test-configuration), [Chrome/Edge channels](https://playwright.dev/docs/browsers#google-chrome--microsoft-edge).

## ADR-007 · 2026-10-16 · Phase 2 진행과 버전 일관성

상황: 사용자가 다음 Phase 진행을 명시적으로 요청했다. 로컬 HTTP/DB/브라우저 검증은 통과했으며 Docker 검증만 남아 있다. ADR-006의 Phase 2 보류 결정을 이번 요청에 따라 대체하되 Docker 검증을 완료로 바꾸지 않는다.

선택지: 변경 가능한 설정만 보관 / 불변 버전 스냅샷과 요청 버전 참조. 결정: IF_SYSTEM/IF_INTERFACE/IF_INTERFACE_VERSION과 매핑·검증 테이블을 V2 migration으로 추가한다. 버전 snapshot JSONB가 실행 시 읽는 전체 정의이며, 개별 규칙 테이블도 같은 트랜잭션으로 저장한다. 수정은 현재 version 비교와 행 잠금을 함께 사용하고 stale 변경은 409로 거절한다.

요청 접수는 짧은 트랜잭션 안에서 Interface 행의 FOR SHARE 잠금, 활성 확인, 설정 snapshot 읽기, 요청 저장을 수행한다. 설정 변경/삭제는 FOR UPDATE를 사용한다. HTTP는 접수 트랜잭션 커밋 후 실행하며, 변경 중인 설정이 전송 내용에 섞이지 않는다. 이력이 있으면 삭제 대신 새 비활성 버전을 저장하고 기존 버전은 FK로 보존한다. 장점은 재현성과 삭제/접수 경쟁의 안전성이며, snapshot과 규칙 projection을 함께 관리하는 저장 비용이 있다. 관련 코드: InterfaceService/InterfaceMapper, RequestLifecycle, V2 migration.

실제 PostgreSQL 테스트로 동시 수정 200/409, 과거 snapshot 보존, HTTP 대기 중 설정 변경 완료를 검증한다. Phase 1 기록의 version/payload는 null로 보존한다.

## ADR-008 · 2026-10-16 · 결정적 매핑과 단계별 검증

선택지: 사용자 스크립트/범용 JSONPath / 제한된 객체 점 경로와 명시적 변환. 결정: 객체 점 경로(최대 16단계), COPY/STRING/NUMBER/BOOLEAN/DATE, 기본값과 Null 정책을 제공한다. 빈 매핑은 deep copy, 규칙이 있으면 명시한 대상 필드만 생성하여 필드 제거를 표현한다. 중복·상하위 대상 경로는 거절한다. 배열 인덱스/와일드카드/스크립트는 지원하지 않고 API 문서에 범위를 명시한다.

Source Validation → Mapping → Target Validation을 분리한다. 실패 단계/필드/규칙을 저장하며 HTTP를 호출하지 않는다. Attempt ID는 검증 통과 후 실제 전송 직전에 발급한다. 입력과 변환 결과는 각각 64KiB로 제한하고 입력은 변경하지 않는다. JSON으로 직렬화된 defaultValue:null은 누락된 필수 source를 숨기지 않는다. 날짜는 Locale.ROOT, 엄격한 uuuu 패턴의 LocalDate/LocalDateTime을 사용한다. 숫자 Enum은 표현 대신 수치로 비교한다.

사용자가 설정한 pattern에는 backtracking 정규식 대신 RE2/J 1.8을 사용한다. lookaround/역참조 등 미지원 문법은 설정 저장 단계에서 거절한다. 장점은 결정적이고 테스트 가능한 동작과 패턴 실행 시간의 예측 가능성이며, 범용 변환보다 표현력이 제한되는 단점이 있다. 관련 코드: PayloadEngine, InterfaceModels, PayloadEngineTest. [RE2/J 실행 모델](https://github.com/google/re2j).

## ADR-009 · 2026-10-16 · 전문 이력과 데이터 최소화

선택지: 원문 평문 저장 / 마스킹된 진단 snapshot 저장. 결정: Phase 2는 원본·변환·응답을 재귀 마스킹하여 JSONB로 저장한다. password/token/authorization/secret/api_key/email/ssn 등의 표준 키와 민감 source에서 이름이 바뀐 target 경로를 처리한다. 실제 HTTP 데이터는 변경하지 않는다. 필드명으로 식별할 수 없는 임의 문자열은 자동 판정할 수 없으므로 가상 데이터만 사용하고 설정 기본값에 비밀을 넣지 않는다.

수동 재처리는 아직 없으므로 마스킹된 전문을 원본처럼 재전송하지 않는다. Phase 5에서 보호된 재처리 원본 보관 방식을 별도로 설계해야 한다. 권장 진단 이력 보관은 30일이며 이번 단계에서는 자동 삭제를 구현하지 않는다. 운영자가 필요 시 ARCHITECTURE의 terminal 이력 정리 SQL을 검토해 적용한다.

조회는 내부 UUID를 사용하여 논리 Request ID의 고유성 범위와 혼동하지 않는다. 목록은 최대 100건/page이며 목록과 count는 짧은 REPEATABLE_READ 트랜잭션으로 읽는다. 검증/매핑 실패는 Attempt ID가 null이고 SENDING 이전 실패로 저장한다. 자동 재시도·멱등성·UNKNOWN 재처리 정책은 변경하지 않는다. 관련 코드: RequestLifecycle, RequestMapper, PayloadRedactor, TransmissionService.

## ADR-010 · 2026-10-19 · Phase 3 추적의 원자성과 통계 의미

상황: 요청의 마지막 상태만으로 이전 단계 및 각 HTTP 시도를 확인할 수 없었다. 선택지: 로그 파일 파싱 / DB 이벤트와 Attempt 모델. 결정: V3 IF_TRACE_EVENT와 IF_ATTEMPT를 추가한다. 접수·설정 조회·검증 결과·전송 시작·응답 처리·완료는 각각 짧은 트랜잭션에서 기존 요청 CAS 상태 변경과 함께 저장한다. HTTP 호출은 트랜잭션 밖에 유지한다. Attempt 1은 실제 HTTP 호출 직전에 생성하고 요청/Trace ID를 유지한다. Phase 3에서는 재시도하지 않는다.

검증 엔진은 결정적 순서로 실행되므로 반환된 실패 단계까지의 결과를 순서대로 저장한다. 이벤트 recorded_at은 DB 기록 시각이며 세부 단계의 실행 시작/소요시간을 의미하지 않는다. 전체 요청 시간은 단조 시계, Attempt 시간은 DB 시작/완료 시각 차이이며 HTTP 외 기록 처리 비용도 포함한다. 기존 기록에 존재하지 않는 이벤트나 시간을 역산하지 않는다. 조회는 내부 UUID별로 구분하여 같은 Trace ID의 복수 요청을 허용한다. Trace 조회는 100개 요청 한도이며 초과 시 422를 반환한다.

통계는 접수 시각의 시작 이상·종료 미만, 기본 최근 24시간, 최대 31일이다. 시간별 통계는 UTC이고 빈 구간은 생략한다. UNKNOWN/진행 중/명확한 실패를 분리하고 평균 시간은 종료된 요청만 계산한다. 두 통계 endpoint는 각 호출 시점의 DB 집계이므로 동시에 전송 중이면 호출 간 수치가 달라질 수 있다. 전문은 기존 마스킹 snapshot만 읽고 이벤트에는 전문/예외 원문을 저장하지 않는다. 대상 DB를 직접 조회하는 서비스 간 결합은 추가하지 않는다. 대상 수신은 기존 헤더 전파·응답 계약과 별도 Target 수신 기록으로 검증한다.

제약: DB 저장에 실패하거나 프로세스가 종료되면 미완료 상태가 남을 수 있다. 이 경우 UNKNOWN/실패를 임의 확정하지 않으며 lease 복구는 Phase 5다. V3는 과거 Attempt를 합성하지 않는다. 장점은 상태와 관측 기록의 일관성이고 비용은 이벤트 저장 증가다. 관련 코드: RequestLifecycle, MonitoringMapper/XML, MonitoringController.

## ADR-011 · 2026-10-19 · 요청 범위 장애와 실제 통신

상황: 전역 장애 플래그는 다른 사용자의 정상 전송을 바꾸고 재현 횟수도 경쟁 상태에 놓인다. 선택지: 프로세스 전역 상태 / Target DB 소유의 불변 시나리오. 결정: Target V2 IF_SIMULATION에 UUID·유형·Interface ID·Request ID·옵션·만료·수신 횟수를 저장한다. Integration은 실제 HTTP로 등록/조회하고 명시적인 execute API에서만 UUID 헤더를 전달한다. 일반 전송은 시나리오를 조회하거나 헤더를 전달하지 않는다. Integration V4 요청에는 simulation_id만 저장하며 서비스 간 FK는 만들지 않는다.

Target은 scope/만료/최대 20회 수신을 조건으로 한 UPDATE RETURNING에서 순번을 원자적으로 배정하고 수신 기록과 함께 짧은 트랜잭션으로 커밋한다. 지연·응답 작성은 커밋 후 수행한다. FAIL_THEN_SUCCESS는 Target 도착 순번 중 처음 N회 500, DUPLICATE는 같은 시나리오의 두 번째 수신부터 409 SIMULATED_DUPLICATE다. 두 동작은 실험용이며 업무 멱등성이나 자동 재시도가 아니다. 매 execute는 별도 내부 요청 UUID와 Attempt이고 Request ID는 동일하다. Trace는 상위 헤더가 없으면 실행마다 새로 발급된다.

수명은 생성 후 최대 1시간, UI 기본 15분이다. 지연 최대 15초, 실패 횟수 최대 5다. TIMEOUT 지연은 실제 접수한 불변 인터페이스 버전의 timeoutMs보다 길어야 한다. 조건이 잘못되면 접수와 이벤트를 함께 롤백한다. Target에서 만료/범위/한도 확인에 실패하면 409이며 일반 정상 동작으로 조용히 바꾸지 않는다. 설정은 수정·삭제하지 않으며 새 실험은 새 UUID로 만든다.

연결 실패는 응답 코드로 흉내 내지 않는다. CONNECTION_FAILURE만 서버 설정 CONNECTION_FAILURE_URL(기본 loopback port 1)로 실제 HTTP를 시도한다. UI/API에 URL 입력은 없다. 해당 주소가 닫혀 있어야 실제 접속 실패가 재현되며, Target 제어 API는 먼저 정상 접근 가능해야 한다. Target에 도달하지 않아 수신 counter/receipt가 없고 Integration Attempt는 UNKNOWN/TARGET_IO_ERROR다. 이 경로의 실행 횟수는 Target 수신 한도에 포함되지 않는다. Target에서 이 유형을 직접 수신하면 400으로 거절한다.

HTTP 400/중복 409는 FAILED, HTTP 500/Timeout/I/O/잘못된 응답은 UNKNOWN을 유지한다. Timeout이어도 Target 수신 기록은 존재할 수 있으며 자동 성공/실패 보정은 하지 않는다. SIMULATIONS_ENABLED=false로 Target의 제어·실행을 비활성화할 수 있다. 동기 지연은 bounded servlet thread를 점유하며 작업 큐·인증·재시도/멱등성·종료 복구는 후속 범위다. 장점은 다른 전송과의 격리 및 재현성이고 비용은 제어용 HTTP 왕복과 시나리오/수신 보관이다. 관련 코드: 양쪽 SimulationController, Target SimulationService/Mapper, RequestLifecycle, TransmissionService, Target ReceiveController.

## ADR-012 · 2026-10-19 · 업무 멱등성과 보호된 재처리 원본

상황: 기존 전문은 마스킹되어 재처리 원본으로 사용할 수 없고, 단순 HTTP 재시도는 Timeout 이후 중복 처리를 유발한다. 선택지: 평문 재전송 원본 / 마스킹 값 재사용 / 외부 키로 암호화한 원본. 결정: Integration V5 IF_DELIVERY의 UNIQUE(interface_id, source_system, request_id, scope)를 논리 등록으로 사용한다. 일반 전송 scope는 normal, 신뢰성 실험은 불변 시나리오 UUID다. Phase 4 수동 execute는 계속 독립 실험이고 이 등록을 우회한다. 과거 IF_REQUEST의 중복 데이터는 추정 병합하거나 재처리 가능하게 바꾸지 않는다.

Source는 객체 키 정렬·숫자 표기 정규화·배열 순서 보존의 SHA-256으로 원본 전문을 비교한다. 같은 키/같은 전문은 기존 요청/Trace/결과를 반환하고 진행/대기 중이면 409, 다른 전문은 PAYLOAD_CONFLICT 409다. 설정 행 공유 잠금과 짧은 등록 트랜잭션 안에서 고유키·요청·설정 버전·검증 결과·암호화 원본을 함께 저장한다. 외부 HTTP는 없다. 최초 유효 요청은 등록 트랜잭션에서 첫 lease/Attempt까지 배정해 poll worker가 먼저 실행하는 경쟁을 방지한다. 요청의 논리 출처는 인터페이스 정의의 sourceSystem이며 호출자가 임의 출처 헤더로 바꾸지 않는다.

Replay envelope는 원본 요청·불변 Definition·불변 Scenario다. AES-256-GCM, 매 암호화의 임의 96-bit nonce, 내부 요청 UUID AAD를 적용한다. 키는 DB/브라우저/로그가 아닌 REPLAY_KEY 환경 변수의 base64 32바이트다. 누락 시 신규 일반 전송은 503으로 롤백하고 잘못된 형식은 시작 시 거절한다. 키를 재시작 사이에 유지해야 하며 자동 키 회전은 구현하지 않는다. 원본은 암호화 컬럼에서만 읽고 공개 조회는 기존 마스킹 snapshot만 제공한다. 키 복구 전 원본 인증에 실패하면 UNKNOWN/DEAD_LETTER로 정지하며 마스킹 값을 재전송하지 않는다.

Target V3 IF_BUSINESS_RESULT는 같은 복합 키의 UNIQUE와 변환 후 canonical hash, 저장된 OK 업무 결과를 가진다. 짧은 DB 트랜잭션의 키별 advisory lock과 PK로 업무 결과/receipt를 원자적으로 기록한다. 동일 hash면 저장 결과를 재사용하고 replay receipt만 추가하며 다른 hash는 409다. 정상 결과는 지연·잘못된 응답을 보내기 전에 커밋되므로 Timeout 이후 재전달은 업무를 다시 처리하지 않는다. 500/400 실패에는 성공 업무 결과를 만들지 않는다. Trace는 현재 상관 헤더로 응답하며 업무 결과를 재사용한다. 업무 모델은 가상 OK 결과 기록이며 실제 외부 업무 시스템까지 exactly-once를 보장한다고 주장하지 않는다.

Source 작업자는 매 dispatch 전 /api/capabilities의 interfaceflow-v1 프로토콜을 확인하고 X-Idempotency-Scope/X-Source-System을 전파한다. 미확인/미지원이면 업무 POST 없이 UNKNOWN/DEAD_LETTER로 정지한다. 제어 접속 오류는 제한된 재시도 대상으로 둔다. 캐시된 최종 Source 결과 조회는 Target 접속을 요구하지 않는다. Target scope 헤더가 없는 Phase 4 수동 실험은 업무 멱등성을 활성화하지 않는다. 모든 대상은 서버 허용 목록과 운영자가 지정한 주소이며 인증은 여전히 미구현이다.

## ADR-013 · 2026-10-19 · 영속 재시도·lease·fencing과 상태 의미

선택지: HTTP 요청 안의 sleep loop / DB due time과 lease 작업자. 결정: IF_DELIVERY의 WAIT/LEASED/DONE/DEAD_LETTER가 실행 상태이고 IF_REQUEST.status는 마지막 처리 결과(UNKNOWN을 확정 FAILED로 바꾸지 않음)다. retryMaxAttempts는 최초 시도를 포함한 총 자동 시도 한도 1~5다. 500ms부터 지수 증가(최대 30초), 구간 50~100% jitter로 next_at을 저장한다. 400/409/검증·매핑 실패는 재시도하지 않고 408/429/5xx/I/O/응답 계약 오류는 멱등성 프로토콜을 전제로 제한적으로 재시도한다.

짧은 트랜잭션에서 delivery 행을 잠그고 60초 lease/token, 단조 증가 시도 순번과 새 Attempt UUID, SENDING을 저장한다. HTTP 및 지연은 밖에서 실행한다. 완료는 token·상태·미만료 lease를 다시 확인해 오래된 작업자의 결과 덮어쓰기를 차단한다. worker는 최초 Request ID/Trace ID/정의 버전을 유지하며 설정 변경을 섞지 않는다. MDC는 저장된 Trace/Request/Attempt로 설정하고 이전 컨텍스트를 복원한다. 매 poll 최근 due 20개를 순차 처리하므로 대량 처리 용량 최적화는 Phase 6다.

프로세스가 죽으면 만료 lease의 이전 Attempt를 UNKNOWN/LEASE_EXPIRED로 닫고 예산 내에서 새 Attempt를 배정한다. 예산이 소진되면 DEAD_LETTER로 두며 마지막 요청 결과는 UNKNOWN일 수 있다. 복구에 lease가 실제로 만료되어야 하고 즉시 재시작으로 이전 작업을 빼앗지 않는다. 본 구현의 네트워크 timeout/지연 상한보다 60초가 길며 별도 heartbeat 연장은 없다. 늦은 Target 수신은 Target 고유키로 보호한다.

수동 retry는 DONE/DEAD_LETTER의 재시도 가능한 오류만, 원본 복호화 확인 후, 동일 행 잠금으로 WAIT를 한 번 예약한다(동시 호출 하나만 성공). 자동 소진 후 한 번씩 추가 허용하되 절대 총 10회다. 성공/영구 오류/검증 오류/실행 중/대기 중/기존 원본 없는 이력은 거절한다. 원본 키 장애나 프로토콜 미확인은 자동 전송 없이 DEAD_LETTER에 두고 운영 조치 후 수동 예약한다. 설정/원본 편집 재처리는 없고 수정 전문은 새 Request ID로 접수한다.

보관: 현재 자동 삭제는 없으며 idempotency 기록과 보호 키 보관 범위 내에서 보장한다. 기존 수동 요청 정리 SQL은 IF_DELIVERY도 FK cascade로 제거하므로 실행 전에 키/재처리 보관 정책을 확인해야 한다. Target 업무 결과를 임의 삭제하면 중복 방지 보장도 사라진다. 관련 코드: DeliveryStore/Mapper, ReliableDelivery, ReplayCipher, RetryController, BusinessService/Mapper, Test-Recovery.mjs.

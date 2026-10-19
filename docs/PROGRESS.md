# 개발 진행 상황

기록일: 2026-10-16 (Asia/Seoul)

## 현재 Phase

**Phase 2 — 인터페이스 및 전문 관리 구현·로컬 검증 완료.** 사용자의 다음 Phase 진행 요청에 따라 진행했다. Phase 1의 Docker Compose/Testcontainers/원격 CI 미검증 항목은 그대로 남기며 성공으로 바꾸지 않는다. Phase 3 이상의 타임라인·통계·장애·재시도·멱등성은 구현하지 않았다.

## 완료 기능

- V2 Flyway: IF_SYSTEM, IF_INTERFACE, IF_INTERFACE_VERSION, IF_FIELD_MAPPING, IF_VALIDATION_RULE, 요청 전문/설정 버전/검증 오류/소요시간 확장. 기존 V1 요청 보존.
- 인터페이스 등록/검색/조회/전체 수정/활성 상태/삭제 정책, SOURCE 시스템 등록과 대상 허용 목록. 고정 샘플 라우팅을 DB 정의 기반으로 교체.
- 버전 스냅샷과 규칙의 원자적 저장, stale version 409, 동시 수정 제어. 요청 접수 시 불변 정의 버전을 고정한다.
- Source 검증 → 필드 매핑 → Target 검증. 필드명/중첩 객체/기본값/추가·제거/숫자·문자·Boolean·날짜/Null 정책. 입력 불변, 결정적 결과, 경로 충돌 검사.
- 필수·타입·문자열 길이·숫자 범위·Enum·날짜·RE2 pattern·Null·중첩 경로 검사. HTTP JSON 소수는 BigDecimal로 읽어 경계값 정밀도를 보존.
- 실제 전송 전에 검증/매핑 실패를 저장하고 대상 호출을 차단. Attempt ID는 실제 전송 직전에만 생성한다. Request/Trace/Attempt 역할은 유지한다.
- 미리보기 API는 DB 현재 버전으로 검증·매핑만 수행하며 HTTP/요청/Attempt/이력을 만들지 않는다.
- 원본·변환·응답 진단 JSON 재귀 마스킹. 민감 source의 이름이 바뀐 target도 마스킹하며 실제 HTTP 값은 변경하지 않는다. 입력/변환 결과 각각 64KiB 제한.
- Request/Interface/Trace 검색·상태·페이지 기반 이력과 내부 UUID 상세, 접수 당시 설정 snapshot. Phase 1의 null 전문/버전은 추정값으로 덮어쓰지 않는다.
- React 인터페이스 관리/규칙 JSON 편집/버전 조회/출발 시스템 등록/미리보기/전송 이력 화면, 모바일 메뉴와 표 내부 가로 스크롤.

## 테스트 결과

기존 .tools의 Java 21/Maven/PostgreSQL 17.6을 사용했다. 사용자 서비스의 5173/8080/8081은 종료하지 않고 별도 임시 DB 55432와 smoke 18080/18081, Vite 15173을 사용했다.

- **Maven verify: 51건 성공**, 실패/오류/skip 0. Integration 43건 + Target 8건.
- Integration 구성: 실제 PostgreSQL 통합 14, 엔진/마스킹 17, 전송 HTTP 서비스 7, HTTP 계약 3, 입력 크기/MDC 경계 2.
- Target 구성: 실제 PostgreSQL 통합 2 + HTTP 계약 6.
- DB 검증: V1→V2 migration, CRUD·버전·규칙 endpoint, Source/Mapping/Target 실패 구분과 대상 미호출, 마스킹/실제 HTTP 값 분리, 삭제/비활성화, 검색/페이지, 동시 수정 200/409, HTTP 대기 중 설정 변경과 이전 버전 전송, 반복 논리 ID의 별도 요청 기록, JSON 소수 경계 정밀도.
- **프론트엔드 Vitest 5건 성공**, TypeScript/Vite 배포 빌드 성공.
- **Playwright 타입 검사 및 Chrome E2E 18건 성공**: 9개 시나리오 × desktop 1440px/mobile 너비 390px. 정상 전송/양쪽 DB 식별자·전문 대조, 미리보기, 검증 실패 미수신, UI 등록·v2 수정·매핑 전송·이력·비활성화, 기존 JSON/로딩/오류/테마/키보드 검증.
- 캡처를 직접 확인하고 모바일 표의 글자 단위 줄바꿈을 수정했다. phase2-sender/interfaces/history-mobile PNG 보존. 실제 모바일 기기/다른 브라우저 엔진 검증은 아님.
- Docker Compose/Testcontainers 경로·원격 GitHub Actions: **미실행**. 환경에 Docker 실행 파일이 없으며 이 검증을 로컬 JVM/DB 성공으로 대체하지 않는다.

## 해결한 문제

- 신규 migration의 dollar 인용부와 JSON 시작 문자가 Flyway placeholder로 오인됨: 신규 V2 적용 전 SQL 단일 인용으로 수정, 실제 migration 성공.
- 직렬화된 defaultValue:null이 누락된 필수 매핑을 생략시킴: 유효한 기본값과 NullNode를 구분, MAPPING_FAILED 회귀 검증.
- 매핑으로 원본 필드를 복제해 64KiB를 초과할 수 있음: 출력 크기도 검사하고 전송/과대 전문 저장 차단.
- 숫자 Enum의 노드 표현 차이와 JSON Double 반올림: BigDecimal 수치 비교 및 HTTP 소수 보존, 실제 경계 테스트.
- 모바일 이력 표의 극단적인 줄바꿈: 내부 가로 스크롤과 최소 열 폭, 페이지 자체 가로 넘침 없음 확인.

## 주요 결정

ADR-007: 요청 접수의 공유 잠금/짧은 트랜잭션, 불변 설정 버전, 삭제/수정 일관성. ADR-008: 결정적 객체 경로 매핑·단계 검증·RE2. ADR-009: 마스킹된 진단 snapshot과 보관/재처리 한계. HTTP는 DB 트랜잭션 밖에서 실행하며 retry/멱등성을 제공한다고 주장하지 않는다.

## 변경 파일

- Backend common Contracts: 동적 Interface ID와 확장된 전송 결과/필드 오류 DTO.
- Integration: ApiController/ApiErrors/RequestMapper/TraceFilter/TransmissionService 수정. InterfaceModels/Mapper/Service/Controller, ApiProblem, PayloadEngine/Redactor, RequestLifecycle 추가.
- V2 migration, InterfaceMapper XML, 요청 Mapper XML, application.yml, integration pom.xml(RE2/J).
- 테스트: PayloadEngineTest/TestDefinitions 추가, DatabaseIntegrationTest/TransmissionServiceTest/HttpContractTest와 frontend/e2e 확장.
- Frontend: api.ts, InterfaceManagement.tsx, History.tsx 추가, main.tsx/style.css 수정.
- 문서: PROJECT 후반 현재 범위 추가, README/API/ARCHITECTURE/DECISIONS/PROGRESS/TEST-SCENARIOS 최신화, Phase 2 화면 3개 추가.
- 기존 사용자 .idea 파일과 PROJECT의 Phase 7 제목 편집을 보존했다. 커밋/사용자 실행 서비스 재시작은 이번 요청에 포함하지 않았다.

## 남은 제약

객체 점 경로만 지원하며 배열 인덱스·와일드카드·스크립트·시간대 날짜 변환은 미지원이다. 규칙 편집은 JSON 편집기이며 시각적 매핑 빌더는 없다. 응답 스키마는 기존 OK/식별자 계약이며 사용자 정의 응답 스키마는 후속 확장이다.

Source/Target 멱등성, 재시도, 서버 중단 복구, 전체 Trace 이벤트와 별도 Attempt 테이블, 대시보드/기간 차트, 장애 시나리오, 인증은 미구현이다. 동일 논리 Request ID는 현재 별도 실행된다. 진행 중 종료되면 VALIDATING/READY/SENDING이 남을 수 있다.

진단 전문은 마스킹되어 원본 재처리에 사용할 수 없다. 자동 보관기간 정리도 아직 없으며 권장 30일 수동 운영 절차만 문서화했다. 설정 기본값에 비밀을 넣지 않으며 임의 문자열의 민감성을 자동 판정하지 않는다.

## 다음 작업

1. Docker 가능한 환경에서 Compose config/build/up, Testcontainers, Compose E2E 및 원격 CI를 실제 실행하고 결과 기록.
2. 다음 개발 단계는 **Phase 3 — 추적 및 모니터링**: 단계별 Trace 이벤트, 별도 Attempt 관리, Trace Explorer, History 확장, 실제 DB 집계 대시보드. Phase 2 동작을 회귀 테스트로 유지한다.
3. Phase 4 장애와 Phase 5 멱등성/재시도/복구를 앞당겨 구현하지 않는다.

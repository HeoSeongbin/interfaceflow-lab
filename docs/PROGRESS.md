# 개발 진행 상황

기록일: 2026-10-19 (Asia/Seoul)

## 현재 Phase

**Phase 3 — 추적 및 모니터링 구현.** 실제 PostgreSQL 기반 Maven 및 독립 JVM·Chrome 로컬 검증 완료. Phase 4 장애 시나리오와 Phase 5 멱등성·재시도·복구는 진행하지 않았다. Docker Compose/Testcontainers/원격 CI 미검증 상태를 유지한다.

## 완료 기능

- Phase 0~2 기존 기능 유지: 두 독립 Spring Boot 서버의 실제 HTTP 통신, DB 인터페이스 CRUD/불변 버전, Source 검증→매핑→Target 검증, 미리보기, 마스킹된 전문 이력, React 관리 콘솔.
- V3 migration: IF_TRACE_EVENT, IF_ATTEMPT와 요청/시도 FK·조회 인덱스. V1/V2 요청은 보존하며 과거 이벤트·시도 시간을 합성하지 않는다.
- 접수/설정 조회/Source 검사/필드 매핑/Target 검사/HTTP 전송/응답 처리/최종 결과 이벤트. 실패 이후 단계는 생성하지 않는다. 검증 실패는 Attempt 없이 종료한다.
- 요청 CAS 상태 변경과 이벤트·Attempt 생성을 같은 짧은 트랜잭션에서 저장. HTTP는 트랜잭션 밖에 유지. 실제 전송 직전 Attempt 1 생성, 완료 상태/HTTP 상태/오류/시간 저장. 재시도 없음.
- Trace ID 조회 API와 Explorer: 같은 Trace에 속한 요청을 내부 UUID별로 구분하고 순서 이벤트/HTTP 시도/마스킹 전문/검증 오류/최종 결과 표시. 잘못된 ID 400, 미존재 404, 100개 요청 초과 422.
- 이력 from/to 기간 필터, 소요시간·오류 코드 표시. 기존 검색/상태/페이지 유지.
- 대시보드: 기간별 전체·성공·명확한 실패·UNKNOWN·진행 중·평균 전체 처리시간, 성공률, 최근 오류 10건, 인터페이스별 상태, UTC 시간별 건수와 막대 지표. 기본 24시간·최대 31일·시작 이상/종료 미만. 재처리 대기는 미구현으로 표시.
- Target의 TARGET_RECEIVED/TARGET_RESPONDED 구조화 로그 및 검증된 Attempt ID MDC. Trace/Request/Attempt 헤더와 기존 응답 계약 유지, 전문/인증정보 로그 없음.

## 테스트 결과

Java 21/Maven 3.9.11/임시 PostgreSQL 17.6, 테스트 DB 55432 사용. 사용자 5173/8080/8081 서비스를 종료하지 않았다.

- **Maven verify 58건 성공**, 실패/오류/skip 0. Integration 50건(실제 PostgreSQL 18, HTTP 계약 3, Monitoring 단위 2, PayloadEngine 17, TraceFilter 3, TransmissionService 7), Target 8건(DB 2, HTTP 계약 6).
- 실제 DB: V1→V2→V3 migration, 정상 8개 이벤트 순서·Attempt 식별자/시간, Source 실패의 HTTP/Attempt 미생성, 상태 전이 거절 후 추가 이력 없음, 동일 Trace의 복수 내부 UUID, 기간 경계/집계/필터, 400/404 오류, HTTP 500 UNKNOWN·400 FAILED의 요청/시도/완료 이벤트 일치. 기존 Phase 2 동시 수정·버전·마스킹·정밀 숫자 회귀 포함.
- Monitoring 단위: 기본 기간/최대 31일/역전 기간, 잘못된 Trace의 DB 미호출. MDC 식별자 전달 및 예외 후 정리 검사.
- **Vitest 5건 성공**, TypeScript/Vite 배포 빌드 및 Playwright 타입 검사 성공.
- Chrome desktop 1440px/mobile 너비 390px **최종 E2E 20건 성공**(14.3초). 별도 두 JVM의 실제 HTTP·DB smoke 성공. 기존 전송/미리보기/CRUD/이력/오류/테마 회귀 및 신규 Trace·대시보드 흐름과 가로 넘침 검증.
- Docker Compose/Testcontainers/원격 GitHub Actions **미실행**. 실제 모바일 기기·다른 브라우저 엔진 검증도 아님.

## 해결한 문제

- 기존 요청 마지막 상태만으로 이전 단계 및 HTTP 시도를 확인할 수 없음: 별도 이벤트·Attempt 모델과 조회 추가.
- 동일 Trace를 단일 요청으로 가정할 위험: UUID별 요청 그룹으로 반환하며 별도 논리 Request ID와 구분.
- 이벤트만 저장되고 요청 상태가 달라지는 위험: CAS 전이와 이벤트/Attempt를 같은 트랜잭션으로 저장.
- UNKNOWN을 실패 통계에 합산하는 오해: 별도 집계/표시, HTTP 오류 실제 DB 검증.
- 테스트 샌드박스의 로컬 DB 접속/프로세스 수명 제약 및 빈 DB: 제한 밖의 테스트 환경에서 전용 DB 생성 후 전체 migration/검증 실행.
- 반복 실행 시 과거 테스트 데이터가 기간 집계에 포함됨: 기존 건수를 기준으로 신규 증가량을 검증하도록 테스트 수정.

## 주요 결정

ADR-010: DB 추적의 원자성, 이벤트 기록 시각과 실행시간의 구분, 과거 이력 미합성, Trace 조회 한도, 기간/통계 의미. ADR-007~009의 버전 일관성·결정적 매핑·마스킹 정책 유지.

## 변경 파일

- Backend: MonitoringController/MonitoringMapper와 XML, V3 migration 추가. RequestLifecycle/RequestMapper/API/요청 XML과 양쪽 TraceFilter, Target ReceiveController 변경.
- Tests: MonitoringControllerTest 추가, DB 통합/TraceFilter 테스트와 Playwright 확장.
- Frontend: Monitoring.tsx 추가, main.tsx/History.tsx 변경.
- Docs: PROJECT/README/API/ARCHITECTURE/DECISIONS/TEST-SCENARIOS/PROGRESS 갱신.
- 사용자 .idea 파일은 변경하지 않았다. 이번 요청으로 커밋/푸시는 하지 않았다.

## 남은 제약 및 다음 작업

- 이벤트 시각은 DB 저장 시각이다. Source/Mapping/Target 단계 결과는 엔진 반환 후 순서 저장하며 세부 실행시간을 측정하지 않는다. 전체 시간과 Attempt 시간의 측정 범위가 다르다.
- Target 내부 로그를 Explorer에 중앙 수집하지 않는다. 같은 Trace/Request/Attempt의 Target 구조화 로그와 수신 DB 기록으로 별도 확인한다. TARGET_RESPONDED는 응답 생성이며 클라이언트 도달 보장이 아니다.
- 과거 기록은 events/attempts가 없을 수 있다. Source 미등록/비활성 등 접수 이전 오류에는 요청 행/Trace 이벤트가 없으며 HTTP Trace 로그로 확인한다.
- 재시도·멱등성·수동 재처리·프로세스 종료 복구·인증·자동 보관 삭제 미구현. 진행 중 종료 시 VALIDATING/READY/SENDING이 남을 수 있다. 마스킹 전문을 재처리 원본으로 사용하지 않는다.
- 규칙은 객체 점 경로와 명시적 변환만 지원하며 배열 인덱스·와일드카드·스크립트·시간대 변환은 미지원. 가상 데이터만 사용.
- Docker 가능한 환경에서 Compose/Testcontainers/원격 CI 실행 결과를 추가해야 한다.
- 다음 개발 단계: **Phase 4 — 장애 시뮬레이션**. 요청 범위·유효기간을 가진 실제 HTTP 오류/지연/Timeout/연결 실패/잘못된 응답/실패 후 성공/중복 시나리오. Phase 5 재시도/멱등성은 앞당기지 않는다.

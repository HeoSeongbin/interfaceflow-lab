# 개발 진행 상황

기록일: 2026-10-19 (Asia/Seoul)

## 현재 Phase

**Phase 4 — 장애 시뮬레이션 구현·로컬 검증 완료.** 요청별 설정으로 실제 HTTP 오류를 재현한다. 기존 Phase 0~3 기능을 유지했다. 자동/수동 재처리·업무 멱등성·복구는 Phase 5에 남긴다. Docker Compose/Testcontainers/원격 CI는 미검증 상태다.

## 완료 기능

- 기존 두 독립 JVM/HTTP/DB 통신, DB 인터페이스·불변 버전·검증·매핑·마스킹 이력, Trace/Attempt·통계·React 콘솔 유지.
- Target V2: IF_SIMULATION 및 수신 기록의 시나리오 UUID/수신 순번/예정 HTTP 상태. Integration V4: request.simulation_id와 인덱스. 서비스별 Flyway/스키마 소유권 유지.
- 시나리오 등록/최근 100개 목록, UUID 기반 명시적 실행, Interface ID·Request ID·만료·옵션 검사. 설정은 불변이며 새 실험은 새 UUID다. UI 기본 유효기간 15분, API 최대 1시간.
- SUCCESS, HTTP_400, HTTP_500, DELAY, TIMEOUT, INVALID_RESPONSE, FAIL_THEN_SUCCESS, DUPLICATE, CONNECTION_FAILURE 9종. 자동 재시도 없이 실제 전송/응답/지연/연결 오류를 발생시킨다.
- Target atomic UPDATE RETURNING으로 수신 순번 배정, scope/만료/최대 20회 검사, counter와 receipt 원자적 저장. 지연 중 DB 트랜잭션 없음. 실패 예산은 동시 수신에서도 보존한다.
- TIMEOUT 옵션은 접수된 실제 인터페이스 버전의 timeoutMs와 비교하며 잘못되면 접수·이벤트를 롤백한다. 일반 전송에는 시나리오 헤더/설정이 적용되지 않는다.
- HTTP 400/중복 409는 FAILED, 500/Timeout/잘못된 응답/I/O는 UNKNOWN 유지. Target 도달 전 연결 실패는 수신 기록 없이 Integration Attempt에 남긴다.
- Simulation Lab UI: 유형/인터페이스/옵션/가상 전문, 등록/실행/새 실험, 실행 결과 비교, 최근 시나리오 목록, Trace 연결 식별자 표시. 긴 목록은 내부 스크롤로 제한.
- Target SIMULATIONS_ENABLED=false 지원. CONNECTION_FAILURE_URL은 서버 전용 설정(기본 loopback port 1)이고 UI/API URL 입력은 없다. Compose 환경 변수 연결 및 .env.example 갱신.

## 테스트 결과

Java 21/Maven 3.9.11/PostgreSQL 17.6, 테스트 DB 55432, 두 JVM 18080/18081, Vite 15173을 사용했다. 사용자 5173/8080/8081 서비스는 종료하지 않았다.

- **Maven verify 67건 성공**, 실패/오류/skip 0. Integration 52건(DB 20, HTTP 계약 3, Monitoring 2, 엔진 17, TraceFilter 3, 전송 7). Target 15건(DB 6, HTTP 계약 6, SimulationService 단위 3).
- 실제 DB: 기존 데이터와 migration 호환, 시나리오별 실패/중복 순번·격리, scope/만료/20회 한도 거절 후 counter/receipt 미변경, 병렬 6회 중 지정 2회만 500 및 고유 순번, 실제 접속 실패 UNKNOWN, 잘못된 TIMEOUT 접수 롤백, CONNECTION_FAILURE의 Target 직접 수신 400.
- 단위: 실패 예산·중복 결과, 만료·옵션 제한, 비활성 서비스 DB 미호출. 기존 인터페이스 동시 수정/버전 보존/마스킹/소수 정밀도/HTTP·Trace 회귀 포함.
- **Vitest 5건 성공**, TypeScript/Vite 배포 빌드 및 Playwright 타입 검사 성공.
- **Chrome desktop/mobile E2E 24건 성공**: 기존 20건 + UI 등록/실행/목록 및 실제 두 JVM의 전체 9종 시나리오 비교(각 viewport). 정상 성공·매핑·이력·Trace·통계·오류·레이아웃 회귀 포함.
- 9종의 상태/HTTP/오류/식별자와 Target receipt를 실제 DB로 대조. Timeout에는 receipt가 있고 연결 실패에는 없음을 검증. 반복 실행의 별도 UUID/Attempt, 같은 논리 ID 일반 전송 성공, scope 409 확인.
- 별도 JVM smoke 실제 HTTP 왕복 SUCCESS. 최종 Simulation Lab desktop/mobile 캡처를 확인하고 보존한다. 실제 모바일 기기/다른 브라우저 엔진 검증은 아니다.
- Docker Compose config/build/up, Testcontainers 경로, 원격 GitHub Actions **미실행**. Compose 환경 변수 편집을 실제 컨테이너 검증으로 간주하지 않는다.

## 해결한 문제

- 전역 장애 설정의 사용자 간 간섭 위험: UUID·Interface·Request·만료 범위로 제한하고 일반 전송에서 분리.
- 동시 수신 시 실패 예산/순번 경쟁: DB 조건부 UPDATE RETURNING과 receipt 트랜잭션으로 제어.
- 지연 중 DB 잠금 유지 위험: 계획·수신 기록 커밋 후 sleep/응답.
- Timeout을 확정 실패로 오인할 위험: UNKNOWN 유지 및 이미 커밋된 Target receipt 대조.
- 연결 실패를 HTTP 500으로 연출할 위험: 닫힌 서버 주소로 실제 접속 시도, 수신 없음/Attempt UNKNOWN 검증.
- 시나리오 목록 증가로 매우 긴 페이지 생성: 높이 제한·내부 스크롤로 수정 후 재검증.

## 주요 결정

ADR-011: Target 소유의 불변 시나리오, scope/만료/수신 예산과 트랜잭션, 실제 연결 실패, 매 실행의 별도 요청/Attempt, 중복 실험과 업무 멱등성의 구분. ADR-007~010의 버전·마스킹·Trace·상태·HTTP 경계 유지.

## 변경 파일

- Common: Simulations DTO/유형 추가.
- Integration: SimulationController와 V4 추가. TransmissionService/RequestLifecycle/RequestMapper/XML/MonitoringMapper/XML/application 변경.
- Target: SimulationService/Controller/Mapper/XML와 V2 추가. ReceiveController/ApiErrors/application 변경.
- Frontend: SimulationLab.tsx 추가, main.tsx 메뉴/범위 갱신, Playwright 확장.
- Tests: Target SimulationServiceTest 추가, 양쪽 DB 통합 및 Target HTTP 계약 테스트 변경.
- Config/docs: .env.example/docker-compose, PROJECT/README/API/ARCHITECTURE/DECISIONS/TEST-SCENARIOS/PROGRESS 및 Phase 4 화면 캡처.
- 사용자 .idea 파일은 변경하지 않았다. 이번 요청으로 커밋·푸시하지 않았다.

## 남은 제약과 다음 작업

- DUPLICATE는 시나리오 내 두 번째 수신 409 실험이다. 일반 요청/Target 업무 멱등성, payload hash 충돌, 자동/수동 재처리, lease 복구는 미구현이다.
- 매 execute는 독립 요청 UUID/Attempt다. Request ID는 같고 Trace는 상위 헤더가 없으면 새로 발급된다. 실패 후 성공은 사용자가 여러 번 실행해야 한다.
- 연결 실패는 Target 제어 API가 정상이어야 준비할 수 있다. 고정 주소가 실제로 닫혀 있어야 재현되며 해당 실행은 Target 수신 횟수/receipt를 만들지 않는다.
- 지연은 최대 15초지만 동기 HTTP 스레드를 사용한다. 큐·전체 용량 제어·인증·자동 보관 삭제는 미구현이다. 설정은 수정/삭제하지 않고 새로 만든다.
- Target 예정 응답 상태는 클라이언트 도달 보장이 아니다. 프로세스 종료 시 미완료 상태가 남을 수 있고 자동 보정하지 않는다.
- 마스킹 전문을 재처리 원본으로 사용할 수 없다. Source/Mapping/Target 세부 실행시간·Target 로그 중앙 수집, 배열 인덱스·스크립트·시간대 매핑 등 기존 제약 유지. 가상 데이터만 사용.
- Docker 가능한 환경에서 Compose/Testcontainers/원격 CI를 실행하고 기록할 것.
- 다음 단계 **Phase 5 — 재시도 및 멱등성**: 먼저 논리 고유키·canonical hash·보호된 재처리 원본·Target 결과 보관·UNKNOWN 정책·상태 머신·lease 계약을 설계하고 동시성/복구를 검증한다.

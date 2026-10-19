# 개발 진행 상황

기록일: 2026-10-19 (Asia/Seoul)

## 현재 Phase

**Phase 5 — 재시도 및 멱등성 구현·로컬 검증 완료.** 일반 전송은 Source/Target DB 멱등성을 적용하고, 보호 원본·불변 설정·영속 스케줄·lease/fencing으로 재처리한다. Phase 4 수동 실험은 유지하고 신뢰성 실험 경로를 추가했다. Docker/Testcontainers/원격 CI는 미검증 상태로 유지한다.

## 완료 기능

- 기존 두 JVM 실제 HTTP, DB 인터페이스/버전/매핑/검증/미리보기/마스킹 이력, 단계·Attempt·Trace, 대시보드, 범위가 제한된 9종 장애 실험 유지.
- Integration V5 IF_DELIVERY: UNIQUE(Interface, sourceSystem, Request ID, scope), canonical 원본 hash, AES-GCM replay envelope, 실행 상태/next_at/lease token/시도 한도. Target V3 IF_BUSINESS_RESULT와 replay receipt. 기존 이력은 추정 병합·암호화 원본 합성하지 않는다.
- 일반 API 중복: 같은 키/같은 전문의 최종 결과 UUID/Trace/결과 재사용, 진행/대기 중 409, 다른 전문 409. 현재 정의 행의 공유 잠금 아래 등록·고정 버전·검증 결과·암호화 원본·최초 lease를 함께 커밋하여 최초 호출과 worker 경쟁을 방지.
- Replay 원본/Definition/Scenario는 AES-256-GCM(임의 nonce, 내부 UUID AAD), 키는 외부 REPLAY_KEY. 공개 조회는 기존 마스킹 snapshot만 반환. 키 누락 시 신규 등록 503/롤백. 키 복구 전 원본 인증 불가 시 UNKNOWN/DEAD_LETTER로 정지.
- Target은 업무 고유키와 변환 hash를 검사하고 정상 업무 결과/수신 기록을 짧은 트랜잭션으로 원자적 저장. 동일 전달은 저장된 OK 결과와 replay receipt를 사용하며 업무 결과는 한 건. 다른 전문 409, 다른 출처의 같은 ID는 별개.
- Source는 업무 전송 전 Target interfaceflow-v1 capability를 확인하고 전용 scope/source 헤더를 전달. 프로토콜 미확인 시 업무 POST를 하지 않고 DEAD_LETTER. Target 제어 연결 오류는 제한된 재시도 대상.
- 자동 재시도: 인터페이스 retryMaxAttempts(최초 포함 1~5), 500ms부터 지수 backoff/최대 30초/50~100% jitter. 408/429/5xx/I/O/응답 계약 오류 대상. 400/409/검증·매핑 실패는 영구 오류.
- 실행 상태 WAIT/LEASED/DONE/DEAD_LETTER와 마지막 요청 결과를 분리해 UNKNOWN을 확정 FAILED로 바꾸지 않는다. HTTP는 DB 트랜잭션 밖에서 실행하고 lease 60초·소유 token·만료 검사로 오래된 완료를 차단.
- 서버 중단 후 만료 lease의 이전 Attempt를 UNKNOWN/LEASE_EXPIRED로 닫고 예산 내 새 Attempt로 재개. Request/Trace/설정 버전 유지, Attempt UUID/순번 증가. MDC 전파·복원 및 안전한 완료 로그.
- 수동 retry는 허용 오류/보호 원본/terminal 큐 상태를 검사하고 한 번 더 예약. 동시 예약 하나만 허용, 자동 포함 총 10회 한도. 성공/영구 오류/진행/대기/과거 보호 원본 없는 요청 거절.
- Retry Center: 최근 100개 안전한 metadata/상태/시도/next time, 5초 갱신, 수동 예약, 마스킹 상세. Simulation Lab에 재시도 정책 실행 추가. 일반 수동 execute는 독립 실험으로 유지.
- Compose/.env/CI의 키 설정, 실행·복구 문서 및 실제 JVM 강제 종료·재시작 검증 스크립트 추가. CI의 고정 키는 폐기용 테스트 전용이다.

## 테스트 결과

Java 21/Maven 3.9.11/PostgreSQL 17.6, 테스트 DB 55432 사용. HTTP/Chrome 18080/18081·Vite 15173, 별도 복구 18180/18181 사용. 사용자 5173/8080/8081 서비스는 변경하지 않았다.

- **Maven verify 75건 성공**, 실패/오류/skip 0. Integration 59건(DB 24, HTTP 계약 3, Monitoring 2, 엔진 17, 신뢰성 단위 3, TraceFilter 3, 전송 7). Target 16건(DB 7, HTTP 계약 6, Simulation 단위 3).
- 단위: canonical 객체/숫자/배열/타입, 임의 nonce/UUID AAD/키 누락, 재시도 오류 분류와 jitter 상한. 기존 회귀 포함.
- 실제 DB: 동시 중복 차단과 본문 충돌, 캐시 결과 UUID/Trace/응답 헤더 유지, 암호문/마스킹 경계, lease 만료/이전 Attempt UNKNOWN/새 Attempt/fencing, 자동 한도·동시 수동 예약 하나만 성공, 성공 후 재시도 거절. Target 병렬 동일 요청의 업무 결과 한 건/replay, 다른 전문 거절/다른 출처 독립. 프로토콜 미확인 시 업무 POST 차단 및 복구 후 수동 성공.
- **Vitest 5건 성공**, TypeScript/Vite 배포 빌드 및 Playwright 타입 검사 성공.
- **Chrome desktop 1440px/mobile 390px E2E 28건 성공**. 기존 24건 + 신뢰성 자동 재시도/Target 결과 재사용/암호문·마스킹·식별자 및 Retry Center 수동 예약(각 viewport). FAIL_THEN_SUCCESS/TIMEOUT/INVALID_RESPONSE의 두 번째 Attempt 성공과 Target 업무 한 건을 실제 DB로 대조. 정상 HTTP/CRUD/매핑/이력/Trace/통계/수동 실험 회귀 유지.
- 별도 JVM 실제 HTTP smoke SUCCESS. 실제 프로세스 복구 스크립트 성공: Target 커밋 후 Integration 강제 종료·같은 키/DB로 재시작·실제 60초 lease 만료 후 SUCCESS, 같은 Trace/새 Attempt, 이전 Attempt UNKNOWN, Target 업무 한 건/replay receipt 한 건. 종료 처리도 정상 exit 0 확인.
- DB 시간만 앞당긴 lease 테스트와 실제 JVM 재시작 검증은 별도 수행했다. 데스크톱·모바일 캡처를 직접 확인하고 보존한다. 실제 휴대폰/다른 브라우저 엔진은 미검증이다.
- Docker Compose config/build/up, Testcontainers 경로, 원격 GitHub Actions **미실행**. 환경 설정/CI 편집을 해당 환경 통과로 처리하지 않는다.

## 해결한 문제

- 마스킹된 진단 값은 재전송 원본이 될 수 없음: 외부 키 AES-GCM envelope, 고정 정의/원본 보호, 공개 조회 분리.
- Timeout·응답 계약 오류 뒤 업무 중복 처리 위험: Target 업무 결과 선커밋·unique/hash·재사용 및 capability 확인.
- 동일 Request의 병렬 접수/수동 예약/재시도 경쟁: 논리 고유키·키별/행 잠금·원자적 lease/Attempt 배정.
- 최초 API 요청과 background worker가 첫 전송을 경쟁할 수 있음: 등록 트랜잭션에서 최초 lease까지 배정.
- 죽은 작업자의 늦은 완료가 최신 결과를 덮어쓸 위험: lease token과 만료를 확인하는 fencing. 원래 Claim의 Attempt ID를 보존해 늦은 작업자가 최신 Attempt를 사용하지 않음.
- 기존 Phase 4 독립 장애 실험과 멱등성 충돌: 별도 reliable-execute/namespace로 기존 반복 실험 유지.
- 복구 스크립트가 이미 signal 종료된 child를 재대기하며 JVM/JAR 잠금을 남김: signalCode도 확인해 종료를 건너뛰도록 수정. 전용 경로/테스트 포트 프로세스만 정리하고 재검증 exit 0 확인.

## 주요 결정

ADR-012: Source/Target 복합 키·canonical hash·별도 보호 원본·capability·수동 실험 격리. ADR-013: 영속 스케줄/예산·상태 의미·lease/fencing·수동 한도/보관 경계. 기존 ADR-007~011의 짧은 트랜잭션·고정 버전·마스킹·UNKNOWN 원칙 유지.

## 변경 파일

- Common: CanonicalJson 추가.
- Integration: DeliveryMapper/XML/Store, ReliableDelivery, ReplayCipher, RetryController, V5 추가. Application 스케줄링, ApiController/SimulationController, MonitoringMapper의 Attempt 순번, 설정 변경.
- Target: BusinessMapper/XML/Service, V3 추가. ReceiveController/SimulationController capability/설정 변경.
- Frontend: RetryCenter.tsx 추가, main/SimulationLab/Monitoring 수정.
- Tests: ReliabilityTest 추가, 양쪽 DB 통합/HTTP 계약/Playwright 확장. scripts/Test-Recovery.mjs 추가, Test-Smoke 키 조건 변경.
- Config/docs: .env.example/docker-compose/CI, PROJECT/README/API/ARCHITECTURE/DECISIONS/TEST-SCENARIOS/PROGRESS 및 Phase 5 캡처.
- 사용자 .idea 파일은 변경하지 않았다. 이번 요청으로 커밋·푸시하지 않았다.

## 남은 제약 및 다음 단계

- 가상 OK 업무 결과/키·이력 보관 범위의 멱등성이다. HTTP exactly-once 전달이나 임의 외부 시스템의 업무 처리를 보장하지 않는다. Target protocol이 필요하고 scope 없는 수동 장애 실험에는 적용하지 않는다.
- 인증/자동 키 회전/자동 보관 삭제/재처리 원본 편집은 미구현. 같은 REPLAY_KEY를 보관해야 하며 키 손실 시 원본을 복구할 수 없다. 과거 이력에는 보호 원본을 추정 생성하지 않는다.
- 60초 만료 후에만 중단 작업을 회수하며 예산 소진 시 UNKNOWN/DEAD_LETTER로 남을 수 있다. heartbeat 연장은 없고 배치 20개 순차 처리다. 전체 용량·조회 성능 최적화는 후속 범위다.
- 요청 status는 마지막 결과, delivery state는 실행 상태다. 기존 REQUEST_COMPLETED 이벤트 후 RETRY_SCHEDULED가 올 수 있으므로 큐 상태와 Attempt 이력을 함께 해석한다.
- 기본 인터페이스 자동 한도는 1회다. 재시도 실험은 설정을 늘려야 하고 수동 추가 포함 총 10회다. 검증/매핑/400/409 오류는 수정 전문·새 Request ID로 접수해야 한다.
- 기존 수동 30일 요청 삭제 SQL은 Source 고유키·cipher도 FK cascade로 삭제한다. 보관 정책을 확인해야 하며 Target 업무 키를 임의 삭제하면 중복 방지 보장이 사라진다.
- Target 로그 중앙 수집·세부 단계 실행시간·배열 인덱스/사용자 스크립트/시간대 변환 등 기존 제약 유지. 가상 데이터만 사용.
- Docker 가능한 환경에서 Compose/Testcontainers/원격 CI를 실제 실행하고 기록할 것.
- 다음 개발 단계: **Phase 6 — UI/UX 및 성능**. 콘솔·비교/차트·대량 조회/작업자 용량·인덱스·접근성 검증. Phase 7 통합 환경 검증은 별도로 남긴다.

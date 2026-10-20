# 개발 진행 상태

## 현재 단계

**Phase 6 — UI/UX 및 성능 구현·로컬 검증 완료(2026-10-20).** 실제 코드와 기존 문서를 대조하고 Phase 6 범위에서 콘솔·커서 조회·인덱스·재시도 작업자를 개선했다. Phase 0~5의 실제 HTTP, DB 계약, Request/Trace/Attempt 의미, 불변 설정, 마스킹·보호 원본, 멱등성·lease/fencing·UNKNOWN 정책을 유지한다. Docker/Testcontainers/원격 CI 통과를 의미하지 않는다.

## 구현 기능 및 해결한 문제

- History: COUNT/OFFSET에 따른 깊은 페이지 비용을 줄이기 위해 metadata 키셋 API와 이전/다음 커서를 적용했다. created_at 마이크로초와 UUID를 함께 정렬하므로 동일 timestamp에서도 안정적으로 탐색한다. 필터 hash/크기/형식 검증으로 다른 조회 조건의 커서 혼용을 거절한다. 기존 페이지 API는 보존했다.
- 대시보드: 실제 DB KPI·기간 필터·UTC 시간별 누적 상태 차트, 빈 시간 0 표시, 명확한 실패와 처리불확실 분리. 평균은 최근 처리 기록 기준임을 안내한다. 통계 원본 표는 앞 200개를 표시하고 API 전체 조회를 안내한다.
- 전문 진단: History/Trace에서 마스킹된 변환 전후 JSON Pointer leaf 비교, 부재/null/타입/배열 위치 구분, 변경 500개·탐색/깊이 한도와 절단 안내. Attempt별 식별자/상태/HTTP/시간/오류 표 제공. 공개 API는 보호 원문을 반환하지 않는다.
- 사용성: 상태 텍스트/배지, 라이트·다크 테마, 키보드 본문 이동/초점/접근 가능한 스크롤 영역, 좁은 화면의 표·차트 스크롤. 많은 인터페이스 통계로 페이지가 과도하게 길어지는 문제는 표 높이를 제한하고 sticky header로 보완했다. 이력 페이지/상세 조회는 취소 및 오래된 응답 반영을 방지한다.
- 오류: 공통 ApiError로 HTTP 상태·오류 코드·유효 Trace ID를 보존하여 사용자 오류 피드백을 통일했다.
- DB: V6 복합 이력/만료 lease 인덱스 및 V7 중복 인덱스 정리. WAIT/LEASED를 UNION ALL과 statement_timestamp로 조회하여 유효 lease 대량 스캔 비용을 줄였다. claim/완료는 기존 실시간 만료·fencing 검사를 유지한다.
- 재시도 작업자: 최대 20건 배치의 고정 풀. RETRY_CONCURRENCY 기본 2/허용 1~4, 실행 직전에 claim하여 풀 대기 중 lease 소모 방지. 배치 종료 후 다음 poll, 종료 시 풀 중단. 최초 API 전송 및 다중 인스턴스 전체의 동시성 상한은 아니다.

## 검증 결과

Java 21/Maven 3.9.11/PostgreSQL 17.6의 테스트 DB 55432를 사용했다. 실제 HTTP/Chrome은 18080/18081·Vite 15173, 별도 복구는 18180/18181에서 수행했다. 사용자 5173/8080/8081 프로세스는 변경하지 않았다.

- **Maven verify 79건 성공**, 실패/오류/skip 0. Integration 63건(DB 26, HTTP 계약 3, Monitoring 2, 엔진 17, 신뢰성 3, Phase 6 단위 2, TraceFilter 3, 전송 7), Target 16건(DB 7, HTTP 계약 6, Simulation 3).
- 신규 DB 통합: 동일 timestamp 12건의 3페이지 순회/중복 없음, 중간 최신 삽입으로 페이지 밀림 없음, 필터 혼용/잘못된 커서/size 거절. 만료 lease만 due 대상이고 유효 lease 제외. 기존 중복/hash/lease/fencing/Target 업무 한 건 회귀 포함.
- 단위: 커서 마이크로초·필터·형식·timestamp 범위, latch 기반 작업자 동시성 2와 전체 실행/설정 범위 검증.
- **Vitest 13건 성공**, TypeScript/Vite 배포 빌드와 Playwright 타입 검사 성공. 비교·UTC 빈 시간/반개구간·오류 metadata 및 기존 테스트 포함.
- **실제 Chrome desktop 1440px/mobile 390px E2E 32건 성공**. 기존 28건 + 커서 앞뒤 이동·마스킹 비교/키보드 본문 이동·실제 차트·라이트 테마·페이지 overflow 검증(각 viewport). 기존 실제 HTTP·DB CRUD/매핑/Trace/통계/장애 실험/자동·수동 재시도·Target 결과 한 건 검증 유지.
- 별도 JVM HTTP smoke 성공. 실제 Integration 프로세스 강제 종료·재시작 후 60초 lease 만료를 기다려 **같은 Request/Trace·새 Attempt·Target 업무 결과 한 건** 복구 성공, 스크립트 exit 0. 시간 조작 DB 테스트와 실제 프로세스 복구를 구분한다.
- 10만 가상 행 SQL 측정: 깊은 OFFSET 16.826ms/9,432 hit blocks → 커서 0.021ms/11, 기존 due 5.170ms/650 → 분리 due 0.104ms/19. 최종 V6/V7 인덱스를 공유한 warm cache 단일 SQL 비교다. API 전체 지연/운영 처리량 SLA로 해석하지 않는다. 데이터 분포·재현·제약은 PERFORMANCE.md.
- 데스크톱·모바일 캡처를 확인하여 docs/screenshots/phase6-*에 보존했다. 실제 휴대폰/다른 브라우저 엔진/스크린리더/접근성 인증은 미검증이다.
- Docker 실행 파일이 없어 Compose config/build/up·Testcontainers 경로·원격 GitHub Actions는 **미실행**이다.

## 변경 파일과 결정

- Backend: ApiController/RequestLifecycle/RequestMapper/Mapper.xml, HistoryCursor, DeliveryMapper.xml, ReliableDelivery/RetryBatch, application.yml, V6/V7 migrations.
- Tests: DatabaseIntegrationTest/Phase6Test, diagnostics.test.ts/api.test.ts, Playwright console.spec.ts, Test-QueryPerformance.mjs.
- Frontend: History/Monitoring/RetryCenter/api/main/style, RequestDiagnostics/TrafficChart/diagnostics 추가.
- Config/docs: .env.example/docker-compose, PROJECT/README/API/ARCHITECTURE/TEST-SCENARIOS/PROGRESS/DECISIONS, PERFORMANCE 및 화면 캡처.
- **ADR-014**: 커서 일관성/호환성, 실제 계획에 따른 중복 인덱스 제거, bounded background 동시성, 마스킹 비교·차트 한계. ADR-007~013의 트랜잭션·불확실성·식별자·멱등성 원칙 유지.
- 사용자 .idea는 변경하지 않았다. 이번 작업은 커밋·푸시하지 않았다.

## 제약 및 다음 단계

- 커서는 페이지 간 snapshot이 아니다. 상태 변경/삭제는 반영될 수 있으며 최신 행은 새로 조회한다. 부분 문자열 검색과 기존 OFFSET API의 광범위 조회 비용은 남는다. cursor hash는 서명이 아니다.
- 가상 OK 업무 모델과 키/이력 보관 범위의 멱등성이다. HTTP exactly-once 또는 임의 외부 시스템 업무 처리를 보장하지 않는다. Target protocol이 필요하며 수동 execute 실험은 별개다.
- 60초 만료 후에만 lease를 회수하며 heartbeat 연장은 없다. 예산 소진은 UNKNOWN/DEAD_LETTER일 수 있다. 기본 자동 한도 1회/수동 포함 총 10회다.
- 인증·자동 키 회전/보관 삭제·원본 편집·중앙 로그 수집·세부 단계별 실행시간·전체 부하/다중 인스턴스 용량 검증은 미완료다. 같은 REPLAY_KEY를 보존해야 한다. 삭제 SQL은 Source 키/cipher도 cascade 제거하며 Target 업무 키 삭제 시 중복 방지 보장이 사라진다.
- 배열 인덱스 매핑/사용자 스크립트/시간대 변환 등 기존 매핑 제약 유지. 비교는 마스킹된 진단용 leaf 비교이며 원본 복구/전문 편집기가 아니다.
- 다음 단계: **Phase 7 — 통합 테스트**. Docker 가능한 환경에서 Compose/Testcontainers/CI 실검증, 회귀 자동화·실행 문서/아키텍처/장애·데모 시나리오 정리. 실행하지 않은 검증은 완료 처리하지 않는다.

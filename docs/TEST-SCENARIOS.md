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

## Phase 2 이후

PROJECT.md 18절의 12개 시나리오를 순서대로 자동화한다. 특히 timeout 후 target 처리 성공, 동일 키/다른 본문 충돌, 동시 중복 요청, 재시도 3번째 성공(Trace 동일/Attempt 상이), 처리 중 서버 종료 복구는 실제 DB/HTTP 기반으로 검증한다. 장애 API와 재시도는 현재 미구현이다.

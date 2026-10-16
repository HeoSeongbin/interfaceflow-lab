# Phase 1 API 계약

POST /api/requests — Content-Type: application/json, 선택 X-Trace-Id (32자리 소문자 hex).

```json
{"interfaceId":"IF-USER-001","requestId":"REQ-DEMO-0001","payload":{"userId":"USER001","userName":"홍길동"}}
```

interfaceId는 현재 IF-USER-001만 허용한다. requestId는 영문·숫자·밑줄·하이픈 1~80자. payload는 JSON 객체다. 최대 요청 크기는 64KB다.

성공적인 처리 결과 응답은 HTTP 200이고 본문의 status로 대상 처리 결과를 확인한다.

```json
{"id":"uuid","requestId":"REQ-DEMO-0001","traceId":"32hex","attemptId":"uuid","status":"SUCCESS","httpStatus":200,"response":{"code":"OK","requestId":"REQ-DEMO-0001","traceId":"32hex"},"errorCode":null}
```

요청 오류는 HTTP 400 / VALIDATION_ERROR, 서버·DB 오류는 HTTP 500 / INTERNAL_ERROR이며 code, traceId를 반환한다. 누락된 필수 헤더도 400이다. 허용하지 않는 HTTP 메서드는 405 / METHOD_NOT_ALLOWED(Allow 헤더 보존), 지원하지 않는 Content-Type은 415 / UNSUPPORTED_MEDIA_TYPE, 알 수 없는 경로는 404 / NOT_FOUND다. 64KB 초과는 413 / PAYLOAD_TOO_LARGE이며 필터에서 같은 JSON 오류 형식과 Trace ID를 반환한다. Content-Length가 없어도 실제 읽은 크기로 제한한다.

대상 4xx는 FAILED / TARGET_HTTP_ERROR, 5xx는 UNKNOWN / TARGET_HTTP_ERROR, 네트워크 오류는 UNKNOWN / TARGET_IO_ERROR, 응답 계약 오류는 UNKNOWN / INVALID_RESPONSE. 네트워크 오류의 세부 분류는 후속 단계에서 확장한다.

GET /api/interfaces — 고정 샘플 목록. CRUD는 미구현.

Target POST /api/receive — 같은 요청 DTO, 필수 X-Trace-Id / X-Request-Id / X-Attempt-Id. 수신 메타데이터 저장 후 OK 응답. Request ID 헤더와 본문 일치 필수. Attempt ID는 소문자 hex UUID의 8-4-4-4-12 형식이다. 잘못된 형식은 DB 캐스팅 전에 400으로 거절하며 수신 기록을 만들지 않는다.

GET /actuator/health — DB 연결 상태 포함. /swagger-ui/index.html 및 /v3/api-docs — 각 서버 자동 생성 문서.

## 후속 API 계획

PROJECT.md 17절을 따른다. 논리 Request ID와 내부 UUID를 구분하고 GET /api/requests/{id}는 내부 UUID를 사용하도록 Phase 2 계약을 확정한다. 요청 출처·Interface ID를 생략한 복합키 조회를 제공하지 않는다. 이력·추적·통계·재처리·시뮬레이션 API는 아직 제공하지 않는다.

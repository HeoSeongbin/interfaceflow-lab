# Phase 2 API 계약

두 서버의 `/swagger-ui/index.html` / `/v3/api-docs`에서 실제 DTO를 확인한다. 최대 API 요청 크기는 64KiB이며 PUT/PATCH와 Content-Length 없는 요청에도 적용한다.

## 시스템과 인터페이스

| API | 동작 |
|---|---|
| GET /api/systems | 출발/대상 시스템 허용 목록 |
| POST /api/systems | SOURCE 시스템 등록 (201), TARGET 등록은 거절 |
| GET /api/interfaces?query=문자열 | ID/이름 검색, 정의 배열 |
| GET /api/interfaces/{id} | 현재 정의 전체 |
| POST /api/interfaces | 신규 정의 저장 (201), version=0 입력 |
| PUT /api/interfaces/{id} | 현재 version을 제출하여 전체 설정 변경 |
| PATCH /api/interfaces/{id}/status | `{ "version": 1, "enabled": false }` |
| DELETE /api/interfaces/{id} | 이력 없으면 삭제, 있으면 비활성화 및 버전 추가 |
| GET /api/interfaces/{id}/versions | 최근 50개 불변 설정 스냅샷 |
| GET /api/interfaces/{id}/mappings | `{version,rules}` |
| PUT /api/interfaces/{id}/mappings | `{version,rules}` 제출, 전체 정의의 새 버전 반환 |
| GET /api/interfaces/{id}/validations | `{version,rules}` |
| PUT /api/interfaces/{id}/validations | `{version,rules}` 제출, 전체 정의의 새 버전 반환 |

변경 결과는 저장된 Definition이다. 중복 ID와 stale version은 HTTP 409이다. 경로/본문 ID 불일치는 400. sourceSystem은 등록된 SOURCE, targetSystem은 등록된 TARGET이어야 한다. 대상은 SYSTEM_B만 사전 등록되며 URL 입력 API는 없다. method=POST, path=/api/receive만 지원한다. timeoutMs=100~10000, retryMaxAttempts=1~5이며 재시도는 아직 실행하지 않는다.

### Definition 예시

```json
{
  "interfaceId":"IF-DEMO-001", "interfaceName":"데모 연동", "description":"가상 데이터",
  "sourceSystem":"SYSTEM_A", "targetSystem":"SYSTEM_B", "method":"POST", "path":"/api/receive",
  "enabled":true, "timeoutMs":3000, "retryMaxAttempts":1, "version":0,
  "mappings":[
    {"source":"userId","target":"employee_id","transform":"COPY","required":true,"nullPolicy":"ERROR"}
  ],
  "validations":[
    {"field":"userId","stage":"SOURCE","type":"STRING","required":true,"nullable":false,"minLength":1},
    {"field":"employee_id","stage":"TARGET","type":"STRING","required":true,"nullable":false}
  ]
}
```

매핑은 점으로 구분한 객체 경로(최대 16단계, 영문/숫자/밑줄)를 지원한다. 배열 값 전체 복사는 가능하지만 배열 인덱스·와일드카드·스크립트는 지원하지 않는다. 빈 mappings는 독립적인 원본 복사, 규칙이 있으면 명시한 대상 필드만 생성한다. 대상 경로 중복·상하위 충돌은 설정 저장 시 거절한다.

transform: COPY / STRING / NUMBER / BOOLEAN / DATE. NUMBER는 숫자 또는 숫자 문자열, BOOLEAN은 Boolean 또는 정확한 문자열 true/false만 허용한다. STRING은 스칼라만 허용한다. DATE는 sourceFormat/targetFormat을 사용하며 LocalDate/LocalDateTime만 지원한다. 날짜는 `uuuu-MM-dd`, 일시는 `uuuu-MM-dd HH:mm:ss`처럼 지정하고 엄격하게 검사한다.

source가 없으면 defaultValue로 필드를 추가한다. 일반 source 누락 시 Null이 아닌 defaultValue를 사용하고, 없으면 required=true는 MAPPING_FAILED, false는 생략한다. `defaultValue:null`은 일반 source 누락을 숨기지 않는다. 실제 Null의 정책은 KEEP / OMIT / ERROR / DEFAULT이고 DEFAULT에는 Null이 아닌 기본값이 필요하다. source가 비어 있는 명시적 Null 상수는 KEEP으로 추가할 수 있다. 변환 결과도 64KiB 이하로 제한한다.

검증 stage는 SOURCE/TARGET, type은 STRING/NUMBER/BOOLEAN/OBJECT/ARRAY이다. required는 필드 존재와 빈 문자열을 검사하고 nullable은 명시적 Null 허용 여부다. minLength/maxLength(문자열), minValue/maxValue(숫자), enumValues, dateFormat, pattern을 조합한다. pattern은 RE2 문법이며 lookaround/역참조는 설정 단계에서 거절한다. 숫자 Enum은 소수 표현이 달라도 수치가 같으면 일치한다.

## 미리보기와 실제 전송

POST /api/interfaces/{id}/preview

```json
{"payload":{"userId":"U001","userName":"홍길동"}}
```

`{interfaceVersion,valid,processingStage,originalPayload,mappedPayload,errors}`를 반환한다. 검증·변환만 하며 Request/Attempt를 생성하거나 HTTP를 전송하지 않는다. 비활성 정의도 점검용 미리보기는 가능하다. 저장하지 않은 편집 내용이 아닌 DB 현재 버전을 사용한다.

POST /api/requests

```json
{"interfaceId":"IF-USER-001","requestId":"REQ-DEMO-0001","payload":{"userId":"USER001","userName":"홍길동"}}
```

interfaceId/requestId는 영문·숫자·밑줄·하이픈 1~80자이며 payload는 객체다. 등록되지 않거나 비활성인 인터페이스는 400으로 즉시 거절하고 대상 HTTP를 호출하지 않는다. 기본 IF-USER-001은 userId/userName을 필수 문자열로 검사하고 employee_id/employee_name으로 매핑한다. departmentCode/email은 선택 필드다.

응답은 기존 `{id,requestId,traceId,attemptId,status,httpStatus,response,errorCode}`에 `{processingStage,interfaceVersion,originalPayload,mappedPayload,errors}`를 추가한다. 접수 후 검증·매핑 실패도 HTTP 200 처리 결과로 반환하므로 status와 errors를 확인한다.

```json
{
  "id":"내부 UUID", "requestId":"REQ-DEMO-0001", "traceId":"32자리 hex", "attemptId":null,
  "status":"VALIDATION_FAILED", "httpStatus":null, "response":null, "errorCode":"VALIDATION_ERROR",
  "processingStage":"SOURCE_VALIDATION", "interfaceVersion":1,
  "originalPayload":{"userId":"U001"}, "mappedPayload":null,
  "errors":[{"field":"userName","rule":"required","message":"필수값입니다.","stage":"SOURCE"}]
}
```

Source 검증 실패는 SOURCE_VALIDATION, 매핑 실패는 FIELD_MAPPING/MAPPING_FAILED, 변환 결과 검증 실패는 TARGET_VALIDATION이다. 이 단계에서는 Attempt ID가 null이며 대상 호출이 없다. 검증 통과 후 새 Attempt ID를 생성하여 HTTP 헤더에 전파한다. I/O/5xx/응답 계약 오류는 UNKNOWN, 4xx는 FAILED이며 자동 재시도는 하지 않는다.

## 전송 이력

- GET /api/requests?query=문자열&status=SUCCESS&page=0&size=20 — `{items,total,page,size}`. query는 Request/Interface/Trace ID에 대한 부분 검색, 최대 160자. page=0~1000000, size=1~100.
- GET /api/requests/{id} — 내부 UUID로 상세 조회. 원본·변환·응답의 마스킹된 JSON, 오류, 소요 시간, 접수 당시 interfaceSnapshot을 포함한다.
- Phase 1 기록은 config version과 전문이 null일 수 있으며 추정값으로 덮어쓰지 않는다.

password/passwd/token/access_token/refresh_token/authorization/secret/api_key/email/ssn 등 표준 키를 재귀 마스킹한다. 민감 source 경로를 다른 이름으로 매핑한 경우 target 경로도 마스킹한다. 마스킹은 저장·조회·미리보기에 적용하며 실제 HTTP 전문은 변경하지 않는다. 필드명으로 인식할 수 없는 임의 문자열의 민감성을 자동 판정하지 않는다. 규칙 기본값에는 비밀값을 넣지 않는다.

## 오류와 Target

공통 오류는 `{code,traceId}`이며 도메인 오류에는 message를 추가한다. HTTP 400 VALIDATION_ERROR/INVALID_CONFIGURATION/INTERFACE_DISABLED, 404 INTERFACE_NOT_FOUND/REQUEST_NOT_FOUND/NOT_FOUND, 409 VERSION_CONFLICT/INTERFACE_EXISTS/SYSTEM_EXISTS, 405 METHOD_NOT_ALLOWED(Allow 헤더 보존), 415 UNSUPPORTED_MEDIA_TYPE, 413 PAYLOAD_TOO_LARGE, 500 INTERNAL_ERROR.

Target POST /api/receive는 같은 SendRequest DTO와 필수 X-Trace-Id / X-Request-Id / X-Attempt-Id를 받는다. Request ID는 본문과 같아야 하고 Trace ID는 32자리 소문자 hex, Attempt ID는 8-4-4-4-12 UUID다. 수신 메타데이터를 저장한 뒤 OK와 Request/Trace ID를 응답한다. Target의 비즈니스 스키마 관리·장애·멱등성은 후속 단계다.

GET /actuator/health는 DB 상태를 포함한다. Trace 타임라인·Attempt 별도 테이블·통계·재처리·시뮬레이션 API는 아직 제공하지 않는다.

# InterfaceFlow Lab — 시스템 연동 검증 및 장애 시뮬레이션 플랫폼 개발 지시서

## 1. 프로젝트 개요

### 프로젝트명

**InterfaceFlow Lab**

### 프로젝트 목적

Java Spring Boot와 React 기반의 시스템 연동 검증 및 장애 시뮬레이션 플랫폼을 개발한다.

서로 다른 두 가상 시스템 사이에서 실제 HTTP API를 통한 JSON 전문 송수신을 구현하고, 필드 매핑, 전문 변환, 유효성 검사, 전송 이력, 장애 시뮬레이션, 실패 재처리, 중복 방지, 로그 추적 기능을 제공한다.

본 프로젝트는 기업 시스템 연동 과정에서 실제로 발생할 수 있는 장애를 공개 가능한 가상 데이터로 재현하는 포트폴리오 프로젝트다.

실제 상용 시스템에 연결하거나 고객사 데이터를 사용하는 것이 아니라, 독립적으로 실행되는 가상 시스템을 이용해 연동 메커니즘을 구현한다.

### 핵심 개발 목표

1. 시스템 A에서 시스템 B로 JSON 전문 전송
2. Interface ID 기반 연동 라우팅
3. Source/Target 필드 매핑 및 데이터 변환
4. 필수값, 타입, 형식 등 전문 검증
5. 실제 HTTP 요청 및 응답 처리
6. 장애 상황을 선택하여 재현
7. Timeout 및 재시도 처리
8. 실패 이력 저장 및 수동 재처리
9. 중복 요청 감지 및 멱등성 보장
10. Trace ID를 이용한 전체 요청 흐름 추적
11. React 기반 관리 콘솔
12. PostgreSQL 기반 영속 데이터 관리
13. Docker Compose 로컬 실행 환경
14. 자동화 테스트 및 포트폴리오 문서화

프로젝트는 실제로 동작하는 서비스로 구현해야 하며, 화면에서만 성공·실패를 연출하는 목업으로 만들지 않는다.

---

## 2. 개발 원칙

다음 원칙을 준수한다.

- Java 및 Spring Boot 기반 백엔드
- React 및 TypeScript 기반 프론트엔드
- MyBatis 기반 DB 접근
- PostgreSQL 데이터베이스
- 실제 HTTP 통신 구현
- 명확한 모듈 책임 분리
- 장애 상황의 재현 가능성 확보
- 모든 요청에 추적 식별자 부여
- 재시도와 중복 요청의 안전한 처리
- DB 트랜잭션과 외부 HTTP 호출의 경계 고려
- 공개 가능한 가상 데이터만 사용
- 테스트 가능한 도메인 로직 설계
- 환경 설정을 코드와 분리
- 단계별 개발 및 검증
- 설계 결정 및 진행 상황의 문서화

최신 버전이라는 이유만으로 불안정하거나 호환되지 않는 라이브러리를 사용하지 않는다.

호환 가능한 안정 버전을 선정하고 결정 이유를 문서화한다.

---

## 3. 기술 스택

### Backend

- Java 21 LTS
- Spring Boot 3.x 또는 호환성이 검증된 안정 버전
- Spring Web
- Spring Validation
- Spring JDBC / Transaction Management
- MyBatis
- PostgreSQL JDBC Driver
- Flyway
- Spring Boot Actuator
- JUnit 5
- Mockito
- Testcontainers

HTTP Client는 Spring의 RestClient 또는 WebClient 중 요구사항에 적합한 방식을 선택한다.

선택 이유와 Timeout, 연결 풀, 예외 처리 정책을 기록한다.

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- TanStack Query
- Zustand
- Axios 또는 Fetch API
- React Hook Form
- Zod
- Recharts
- Lucide React
- Vitest
- Playwright

라이브러리는 필요한 기능에 따라 선택하며 불필요한 종속성을 도입하지 않는다.

### Database

- PostgreSQL
- MyBatis XML Mapper 중심
- Flyway 스키마 버전 관리

### Infrastructure

- Docker
- Docker Compose
- GitHub Actions
- Swagger/OpenAPI
- 구조화된 JSON Logging

선택적으로 Redis/Valkey를 도입할 수 있으나 초기 버전의 필수 의존성으로 설정하지 않는다.

---

## 4. 시스템 아키텍처

전체 시스템을 다음 구성요소로 나눈다.

### 4.1 Frontend Console

React 기반 관리 화면이다.

다음 기능을 제공한다.

- 대시보드
- 인터페이스 관리
- 전문 전송
- 필드 매핑 설정
- 유효성 검사 규칙 설정
- 장애 시뮬레이션
- 전송 이력
- 실패 건 재처리
- Trace 로그
- 시스템 설정

### 4.2 Integration Server

핵심 연동 백엔드다.

Spring Boot 애플리케이션으로 구성하며 다음 책임을 가진다.

- Interface ID 조회
- 요청 접수
- Trace ID 생성
- Request ID 관리
- 전문 검증
- 데이터 매핑 및 변환
- 대상 시스템 라우팅
- HTTP 전송
- 응답 검증
- 상태 변경
- 로그 기록
- 재시도 및 재처리
- 중복 요청 감지

외부 대상 시스템 호출 중 DB 트랜잭션을 장시간 유지하지 않도록 설계한다.

### 4.3 Target Simulator

별도의 Spring Boot 애플리케이션으로 구현한다.

Integration Server와 실제 HTTP로 통신한다.

다음 동작을 제공한다.

- 정상 응답
- 비즈니스 오류 응답
- HTTP 400
- HTTP 500
- 응답 지연
- Timeout 유발
- 중복 요청 감지
- 응답 데이터 오류
- 특정 횟수 실패 후 성공
- 요청 수신 이력 저장

가능하면 Integration Server와 Target Simulator는 별도 프로세스로 구동한다.

하나의 메서드 호출로 외부 시스템 통신을 대체하지 않는다.

### 4.4 Database

연동 정의, 필드 매핑, 요청 이력, 처리 시도, 장애 설정, 재처리 내역을 영속화한다.

### 권장 구조

```text
interfaceflow-lab/
├── PROJECT.md
├── AGENTS.md
├── README.md
├── docs/
│   ├── PROGRESS.md
│   ├── DECISIONS.md
│   ├── ARCHITECTURE.md
│   ├── API.md
│   └── TEST-SCENARIOS.md
├── backend/
│   ├── integration-server/
│   ├── target-simulator/
│   └── common/
├── frontend/
├── database/
│   └── migrations/
├── docker/
├── .github/
│   └── workflows/
└── docker-compose.yml
```

common 모듈은 공유가 필요한 DTO나 공통 규약만 포함하도록 하며, 두 서버가 하나의 도메인 서비스 구현에 강하게 결합되지 않게 한다.

---

## 5. 필수 문서 관리 체계

본 프로젝트는 다음 5개 문서를 핵심으로 관리한다.

### 5.1 PROJECT.md

프로젝트의 최종 요구사항을 정의한다.

필수 내용:

- 프로젝트 목표
- 기술 스택
- 아키텍처
- 기능 명세
- 데이터 모델
- API 설계
- 처리 상태 및 재시도 정책
- 장애 시뮬레이션 요구사항
- 보안 및 로그 정책
- 테스트 요구사항
- 개발 Phase
- 최종 완료 기준

기능을 개발하는 동안 기준 문서로 사용한다.

### 5.2 AGENTS.md

모든 에이전트가 준수할 작업 규칙을 정의한다.

필수 규칙:

1. 작업 전 PROJECT.md 전체 또는 관련 명세 확인
2. PROGRESS.md 확인
3. DECISIONS.md 확인
4. 기존 코드 및 테스트 확인
5. 현재 Phase 범위 파악
6. 기존 정상 기능 보존
7. 구현 후 빌드 및 테스트
8. 진행 상태 갱신
9. 설계 변경 기록
10. 미검증 기능 완료 처리 금지

세션이 바뀌더라도 동일한 개발 방향을 유지하도록 작성한다.

### 5.3 README.md

GitHub 방문자와 사용자를 위한 문서다.

다음 내용을 포함한다.

- 프로젝트 소개
- 주요 기능
- 전체 아키텍처
- 기술 스택
- 실행 방법
- 기본 테스트 시나리오
- 화면 설명
- API 사용 예시
- Docker 실행 방법
- 장애 시뮬레이션 사용법
- Trace ID 조회 방법
- 알려진 제약
- 테스트 실행 방법
- 포트폴리오 설명

구현되지 않은 기능을 완료된 기능으로 표시하지 않는다.

### 5.4 docs/PROGRESS.md

개발 진행 상황을 관리한다.

다음 내용을 기록한다.

- 현재 Phase
- 완료 기능
- 작업 중 기능
- 미완료 기능
- 현재 문제
- 해결한 문제
- 테스트 결과
- 다음 작업

### 5.5 docs/DECISIONS.md

주요 기술적 결정사항을 기록한다.

ADR 형식을 권장한다.

각 결정에 다음 내용을 기록한다.

- 결정 ID
- 작성 날짜
- 문제 상황
- 선택지
- 최종 결정
- 선택 이유
- 장단점
- 관련 코드
- 영향 범위

특히 멱등성, 재처리, 트랜잭션, 추적 로그, 상태 모델 변경은 반드시 기록한다.

---

## 6. 인터페이스 관리

시스템 간 통신을 위한 인터페이스 정의 기능을 구현한다.

### Interface 정보

```ts
interface InterfaceDefinition {
  interfaceId: string;
  interfaceName: string;
  description?: string;
  sourceSystem: string;
  targetSystem: string;
  method: "POST";
  path: string;
  enabled: boolean;
  timeoutMs: number;
  retryMaxAttempts: number;
}
```

### 기능

- 인터페이스 등록
- 조회
- 수정
- 활성화/비활성화
- 삭제 정책
- Interface ID 중복 검사
- Source System 등록
- Target System 등록
- 요청/응답 스키마 관리
- 필드 매핑 규칙 연결
- 기본 재시도 정책 지정

### 중요 조건

Interface ID가 존재하지 않으면 즉시 유효성 오류로 처리한다.

비활성화된 Interface ID로 요청하면 전송하지 않는다.

전송 이력이 존재하는 Interface는 참조 무결성을 고려하여 삭제 대신 비활성화하는 방식을 우선한다.

인터페이스 변경 시 기존 전송 이력을 해석할 수 있도록 설정 버전 또는 스냅샷을 저장한다.

외부 URL을 사용자가 자유롭게 입력해 임의의 내부 주소로 요청할 수 있는 구조는 초기 버전에서 제공하지 않는다.

대상 시스템은 사전 정의된 허용 목록으로 제한한다.

---

## 7. JSON 전문 전송

사용자는 React 관리 콘솔에서 JSON 전문을 직접 작성하거나 샘플을 선택해 전송한다.

### 예시 요청

```json
{
  "interfaceId": "IF-USER-001",
  "requestId": "REQ-DEMO-0001",
  "payload": {
    "userId": "USER001",
    "userName": "홍길동",
    "departmentCode": "DEV01",
    "email": "demo@example.com"
  }
}
```

### 전송 과정

1. 요청 접수
2. Request ID 검증
3. Trace ID 생성
4. Interface ID 조회
5. 중복 요청 확인
6. 유효성 검사
7. 필드 매핑
8. 데이터 변환
9. 대상 시스템 요청
10. 응답 수신 및 검사
11. 처리 상태 결정
12. 이력 및 로그 저장
13. 프론트엔드에 결과 제공

### 식별자 설계

다음 식별자를 분리해서 사용한다.

**Request ID**

논리적인 요청의 식별자다.

재시도하더라도 동일한 Request ID를 유지한다.

**Trace ID**

요청 처리 흐름을 추적하는 식별자다.

최초 요청에서 생성하거나 유효한 상위 Trace ID를 수신해 전파한다.

재시도 시에도 최초 Trace ID를 유지한다.

**Attempt ID**

각 HTTP 전송 시도를 구분하는 식별자다.

재시도할 때마다 새로운 Attempt ID를 생성한다.

**Interface ID**

요청이 어떤 연동 인터페이스에 해당하는지 식별한다.

**Correlation ID**

필요하다면 시스템 간 요청/응답 연관 관계 식별에 사용한다.

식별자 간 역할이 중복되지 않도록 명확하게 정의한다.

---

## 8. 필드 매핑 엔진

Source JSON 데이터를 Target JSON으로 변환하는 기능을 구현한다.

### 예시

Source:

```json
{
  "userId": "U001",
  "userName": "홍길동",
  "departmentCode": "DEV01"
}
```

Target:

```json
{
  "employee_id": "U001",
  "employee_name": "홍길동",
  "dept_code": "DEV01"
}
```

### 필드 매핑 규칙

| Source | Target |
|---|---|
| userId | employee_id |
| userName | employee_name |
| departmentCode | dept_code |

### 필수 기능

- 필드명 변경
- 중첩 JSON 경로 처리
- 기본값 지정
- 필드 제거
- 필드 추가
- String/Number/Boolean 변환
- 날짜 문자열 포맷 변환
- Null 처리 정책
- 매핑 누락 오류 표시
- 변환 전/후 비교

### 설계 원칙

- DB 기반 매핑 정의
- 재사용 가능한 Mapping Engine
- UI에서 매핑 규칙 관리
- 잘못된 매핑 감지
- 원본 JSON 변경 없이 결과 생성
- 지원하지 않는 변환은 오류 처리

임의 JavaScript 코드를 실행하는 사용자 정의 변환은 초기 버전에서 제공하지 않는다.

매핑 엔진의 동작은 입력 데이터와 규칙이 같을 때 동일한 결과를 반환할 수 있도록 결정적으로 구현한다.

---

## 9. 전문 유효성 검증

필드 단위 유효성 검증 기능을 구현한다.

### 검사 유형

- Required
- Data Type
- Min Length
- Max Length
- Min Value
- Max Value
- Enum
- Date Format
- Pattern
- Null 허용 여부
- 중첩 필드 존재 여부

### 예시

```json
{
  "userId": "",
  "userName": "홍길동",
  "departmentCode": "DEV01"
}
```

userId가 필수값이면 다음과 같은 오류를 반환한다.

```json
{
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "userId",
      "rule": "required",
      "message": "userId는 필수값입니다."
    }
  ]
}
```

검증 오류는 어떤 필드에서 어떤 규칙에 의해 발생했는지 확인할 수 있어야 한다.

### 처리 순서

입력 스키마 검증과 변환 결과 스키마 검증을 구분한다.

```text
Source JSON
  ↓
Source Validation
  ↓
Field Mapping
  ↓
Target Validation
  ↓
Target System
```

각 단계의 실패 내역은 별도로 기록한다.

---

## 10. 상대 시스템 시뮬레이터

독립 실행되는 Spring Boot 애플리케이션으로 구현한다.

### 기본 기능

- 정상 요청 수신
- JSON 구조 검증
- 응답 생성
- 수신 요청 이력 저장
- 요청별 중복 감지
- 장애 시나리오 처리

### 장애 시나리오

**SUCCESS**

정상 응답을 반환한다.

**HTTP_400**

잘못된 요청에 해당하는 HTTP 400 응답을 반환한다.

**HTTP_500**

서버 내부 오류를 재현한다.

**DELAY**

설정된 시간만큼 응답을 지연한다.

**TIMEOUT**

클라이언트에 설정된 읽기 Timeout보다 길게 응답을 지연시켜 Timeout을 재현한다.

**FAIL_THEN_SUCCESS**

설정한 횟수만큼 실패한 뒤 정상 응답한다.

**INVALID_RESPONSE**

예상하지 못한 JSON 응답 또는 필수값 누락 응답을 반환한다.

**DUPLICATE**

동일한 Request ID가 다시 전달되었을 때 중복 처리 정책을 검증한다.

**CONNECTION_FAILURE**

대상 시스템 연결 실패 상황을 검증한다.

연결 실패는 서버 종료 또는 네트워크 접근 불가 등 실제 연결 오류가 발생하는 방식으로 별도로 테스트한다.

### 시뮬레이션 정책

장애 설정은 전체 시스템에 무분별하게 적용하지 않는다.

시나리오별 식별자, 요청 범위 및 유효기간을 관리한다.

동시에 여러 사용자가 테스트할 때 설정이 서로 영향을 주지 않도록 한다.

---

## 11. 요청 처리 상태 관리

요청 처리 상태를 명확한 상태 머신으로 관리한다.

### 권장 상태

- RECEIVED
- VALIDATING
- VALIDATION_FAILED
- MAPPING_FAILED
- READY
- SENDING
- SUCCESS
- RETRY_WAIT
- FAILED
- UNKNOWN
- DEAD_LETTER

### 상태 설명

RECEIVED: 요청 접수

VALIDATING: 유효성 검사

VALIDATION_FAILED: 전문 검증 실패

MAPPING_FAILED: 필드 변환 실패

READY: 전송 대기

SENDING: 대상 시스템 전송 중

SUCCESS: 정상 응답 확인

RETRY_WAIT: 재시도 대기

FAILED: 명확하게 실패한 요청

UNKNOWN: 대상 처리 여부를 확정할 수 없는 요청

DEAD_LETTER: 자동 재시도 한도를 초과하거나 추가 처리가 필요한 요청

### 중요 설계 원칙

네트워크 Timeout이 발생했다고 해서 대상 시스템에서 반드시 처리되지 않았다고 판단해서는 안 된다.

예를 들어 대상 시스템이 요청을 처리한 후 응답 전달 단계에서 Timeout이 발생했다면 실제 처리 결과는 성공일 수 있다.

따라서 처리 결과가 불명확한 경우에는 UNKNOWN 상태 또는 동등한 처리 방식을 사용한다.

상태 전환 정책을 명시적으로 설계하고 잘못된 상태 전환을 방지한다.

처리 상태 변경은 이력으로 기록한다.

---

## 12. 실패 건 재처리

실패한 요청을 다시 처리하는 기능을 구현한다.

### 수동 재처리

- 실패 목록 조회
- 오류 원인 확인
- 요청 상세 조회
- 재처리 버튼
- 재처리 결과 확인
- 기존 이력과 연결
- 재처리 전 현재 상태 검증

### 자동 재시도

- 최대 재시도 횟수
- 재시도 간격
- Exponential Backoff
- Jitter
- 재시도 가능 오류 구분
- 재시도 불가능 오류 구분
- 최종 실패 처리

### 예시 정책

- 연결 오류: 재시도 가능
- HTTP 503: 재시도 가능
- HTTP 400: 기본적으로 재시도 불가
- 전문 필수값 누락: 재시도 불가
- Timeout: 처리 결과 불명확, 멱등성 확인 후 재시도
- HTTP 500: 대상 처리 여부를 고려한 정책 적용

HTTP 상태 코드만으로 모든 오류를 확정 분류하지 않는다.

### 재처리 원칙

논리 요청의 Request ID를 유지한다.

새 Attempt ID를 생성한다.

기존 요청 기록을 삭제하지 않는다.

재처리 이력을 추가한다.

동일한 요청에 대해 여러 재처리 작업자가 동시에 처리하지 못하도록 상태 조건 및 DB 동시성 제어를 적용한다.

재처리는 실패를 숨기는 기능이 아니라 장애 복구 과정을 추적할 수 있도록 구현한다.

---

## 13. 중복 요청 및 멱등성

시스템 연동에서 중요한 기술 요소다.

### 요구사항

동일한 Interface ID, 요청 출처, Request ID를 가진 논리 요청이 중복 전달되면 정책에 따라 중복 처리를 방지한다.

서로 다른 요청 출처가 우연히 같은 Request ID를 사용하는 경우까지 동일 요청으로 취급하지 않도록 고유성 범위를 정의한다.

### 중복 요청 처리

1. 요청의 멱등성 키 확인
2. DB 중복 등록 확인
3. 기존 요청 상태 확인
4. 진행 중이면 중복 실행 차단
5. 이미 성공했다면 저장된 처리 결과 제공
6. 실패 상태라면 재처리 정책 확인
7. 요청 본문이 다르면 충돌 오류 반환

메모리 Map만으로 중복 요청을 관리하지 않는다.

DB Unique Constraint 및 트랜잭션을 사용한다.

### 상대 시스템 처리

Integration Server와 Target Simulator 양쪽에서 멱등성을 검증한다.

대상 시스템에서도 동일 요청에 대한 중복 비즈니스 처리를 방지해야 한다.

일반적인 HTTP 재시도만으로 Exactly-Once 전달이 보장된다고 주장하지 않는다.

---

## 14. 요청 로그 및 추적

요청부터 최종 응답까지 같은 추적 식별자로 조회할 수 있는 기능을 구현한다.

### 공통 로그 필드

- timestamp
- traceId
- requestId
- attemptId
- interfaceId
- sourceSystem
- targetSystem
- processingStage
- status
- durationMs
- errorCode
- errorMessage

### 주요 로그 단계

```text
REQUEST_RECEIVED
  ↓
INTERFACE_RESOLVED
  ↓
SOURCE_VALIDATED
  ↓
FIELD_MAPPED
  ↓
TARGET_VALIDATED
  ↓
REQUEST_DISPATCHED
  ↓
TARGET_RECEIVED
  ↓
TARGET_RESPONDED
  ↓
RESPONSE_PROCESSED
  ↓
REQUEST_COMPLETED
```

실패 시에는 해당 단계에서 오류 정보를 기록한다.

### Spring MDC

MDC를 사용하여 Trace ID와 Request ID를 로그에 포함한다.

스레드 풀과 비동기 작업을 사용할 경우 Context가 누락되지 않도록 명시적으로 전파한다.

요청 종료 후 MDC를 정리하여 다른 요청의 로그와 섞이지 않게 한다.

### HTTP 추적

Integration Server에서 Target Simulator로 Trace ID, Request ID, Attempt ID를 HTTP 헤더로 전달한다.

### 추적 화면

React 화면에서 Trace ID를 검색하면 다음을 확인할 수 있어야 한다.

- 최초 요청
- 변환 전 JSON
- 변환 후 JSON
- 검증 결과
- 대상 시스템 전송
- HTTP 응답
- 오류 발생 단계
- 재시도 이력
- 최종 결과
- 처리 소요 시간

로그에는 비밀번호, 토큰 및 민감 필드를 기본적으로 기록하지 않는다.

예제 데이터를 사용하더라도 마스킹 및 로그 데이터 제한 설계를 포함한다.

---

## 15. 데이터베이스 설계

MyBatis 기반으로 데이터 접근 계층을 구현한다.

### 권장 테이블

**IF_SYSTEM**

가상 시스템 정보

**IF_INTERFACE**

인터페이스 정의

**IF_INTERFACE_VERSION**

인터페이스 설정 변경 버전

**IF_FIELD_MAPPING**

필드 매핑 규칙

**IF_VALIDATION_RULE**

유효성 검증 규칙

**IF_REQUEST**

논리 요청 및 현재 처리 상태

**IF_REQUEST_ATTEMPT**

전송 시도별 결과

**IF_TRACE_EVENT**

처리 단계별 추적 이벤트

**IF_SIMULATION_SCENARIO**

장애 시뮬레이션 정의

**IF_TARGET_RECEIPT**

대상 시스템의 요청 접수 및 멱등성 처리 정보

**IF_RETRY_SCHEDULE**

재시도 예정 작업

실제 테이블 구조는 분석 후 확정한다.

### DB 설계 요구사항

- PK/FK
- Unique Constraint
- 적절한 Index
- Created/Updated Time
- 상태 및 오류 정보
- 재시도 횟수
- Request ID
- Trace ID
- Attempt ID
- 요청/응답 JSON
- 설정 버전

PostgreSQL JSONB 활용을 검토한다.

대용량 전문 저장 시 성능과 보안, 보관 기간을 고려한다.

### 트랜잭션

- 요청 접수 및 멱등성 등록은 원자적으로 처리
- 요청 상태 전환은 동시성을 고려
- 외부 HTTP 요청 중 장시간 DB 트랜잭션 유지 금지
- 실패한 처리 시도 이력 보존
- 서버 중단 후 미완료 요청 복구 정책 설계
- 재시도 작업의 중복 실행 방지

Flyway로 DB 스키마 변경 이력을 관리한다.

---

## 16. React 관리 콘솔

전문적인 운영 모니터링 도구 스타일로 구현한다.

### 화면 1. Dashboard

- 전체 요청 수
- 성공 수
- 실패 수
- 성공률
- 평균 응답시간
- 재처리 대기 건수
- 최근 오류
- 인터페이스별 처리 현황
- 시간대별 처리량 차트

### 화면 2. Interface Management

- 인터페이스 목록
- Interface ID 검색
- 신규 인터페이스 등록
- 인터페이스 상세
- 필드 매핑 편집
- 검증 규칙 편집
- 설정 버전 확인

### 화면 3. Message Sender

- Interface 선택
- 샘플 전문 불러오기
- JSON 전문 편집
- JSON 문법 검사
- 전송 실행
- 변환 결과 미리보기
- 응답 결과
- Trace ID 표시

### 화면 4. Simulation Lab

- 장애 유형 선택
- HTTP 오류 설정
- 응답 지연 설정
- Timeout 설정
- 특정 횟수 실패 후 성공
- 중복 요청 테스트
- 시뮬레이션 실행
- 결과 비교

### 화면 5. Transmission History

- 요청 목록
- Interface ID 검색
- Request ID 검색
- Trace ID 검색
- 상태 필터
- 기간 필터
- 소요 시간
- 오류 코드
- 페이지네이션
- 상세 정보

### 화면 6. Trace Explorer

- Trace ID 검색
- 요청 타임라인
- 단계별 로그
- 각 HTTP 시도
- 전송/응답 데이터
- 실패 단계 강조
- 처리시간 비교

### 화면 7. Retry Center

- 실패 요청 목록
- 재처리 가능 여부
- 자동 재시도 상태
- 수동 재처리
- 재처리 횟수
- 이전 Attempt 비교
- 최종 처리 결과

### UI 원칙

- 운영 관리 콘솔 스타일
- 한국어 기본
- Dark/Light 모드
- 반응형 레이아웃
- 명확한 상태 구분
- 가독성 높은 JSON Viewer
- 로딩 및 오류 상태 처리
- 검색 및 필터
- 사용자 피드백 제공

실제 기능이 없는 버튼을 완성된 것처럼 보여주지 않는다.

---

## 17. API 설계

REST API 기반으로 구현한다.

### 인터페이스

- GET /api/interfaces
- GET /api/interfaces/{id}
- POST /api/interfaces
- PUT /api/interfaces/{id}
- PATCH /api/interfaces/{id}/status

### 매핑 및 검증

- GET /api/interfaces/{id}/mappings
- PUT /api/interfaces/{id}/mappings
- GET /api/interfaces/{id}/validations
- PUT /api/interfaces/{id}/validations

### 요청

- POST /api/requests
- GET /api/requests
- GET /api/requests/{requestId}
- POST /api/requests/{requestId}/retry

Request ID의 고유성 범위가 복합 키라면 API에서 요청 출처 또는 시스템 ID를 함께 식별하도록 설계한다.

### 추적

- GET /api/traces/{traceId}
- GET /api/requests/{requestId}/attempts

### 시뮬레이션

- GET /api/simulations
- POST /api/simulations
- POST /api/simulations/{id}/execute

### 대시보드

- GET /api/dashboard/summary
- GET /api/dashboard/statistics

구체적인 Request/Response DTO는 별도로 설계한다.

일관된 오류 응답 형식을 제공한다.

Swagger/OpenAPI 문서를 자동 생성한다.

---

## 18. 테스트 시나리오

테스트용 가상 인터페이스를 제공한다.

### Scenario 1. 정상 사용자 정보 연동

정상 JSON 전문을 전달한다.

기대 결과:

- 유효성 검증 성공
- 필드 매핑 성공
- 대상 시스템 처리 성공
- SUCCESS 상태
- 전체 Trace 기록

### Scenario 2. 잘못된 Interface ID

존재하지 않는 ID 사용.

기대 결과:

- 인터페이스 조회 실패
- 대상 시스템 호출 없음
- 정확한 오류 코드
- 오류 로그 확인

### Scenario 3. 필수값 누락

userId 등 필수값 누락.

기대 결과:

- VALIDATION_FAILED
- 해당 필드 표시
- 대상 시스템 호출 없음

### Scenario 4. 필드 타입 오류

문자열이 필요한 필드에 잘못된 타입 입력.

기대 결과:

- 검증 실패
- 원인 표시

### Scenario 5. HTTP 500 오류

대상 시스템이 HTTP 500 반환.

기대 결과:

- 실패 이력 기록
- 정책에 따라 재시도
- Attempt별 기록 확인

### Scenario 6. Response Timeout

대상 시스템이 설정 시간보다 늦게 응답한다.

기대 결과:

- 실제 Timeout 발생
- 처리 결과 불확실성 기록
- 무조건 미처리로 판단하지 않음
- 중복 처리 방지 기반 재시도

### Scenario 7. 중복 요청

동일한 Request ID와 Payload 반복.

기대 결과:

- 중복 요청 인지
- 중복 비즈니스 처리 방지
- 기존 결과 연결

### Scenario 8. 중복 Request ID와 다른 Payload

동일 ID로 서로 다른 데이터 전송.

기대 결과:

- 충돌 오류
- 기존 요청 훼손 없음

### Scenario 9. 재시도 후 성공

처음 두 번 실패하고 세 번째 성공.

기대 결과:

- 시도별 이력 유지
- 최종 SUCCESS
- Trace ID 동일
- Attempt ID 각각 다름

### Scenario 10. 서버 중단 후 복구

전송 중 서버 종료를 재현한다.

기대 결과:

- 미완료 요청 탐지
- 중복 실행 방지
- 정책에 맞는 복구 또는 확인 대기

### Scenario 11. 매핑 오류

존재하지 않는 Source 필드 참조.

기대 결과:

- 매핑 오류 기록
- 잘못된 전문 전송 방지

### Scenario 12. 응답 구조 오류

대상 시스템에서 예상과 다른 JSON 반환.

기대 결과:

- 응답 검증 실패
- 오류 이력 기록
- 원본 응답과 오류 원인 추적

---

## 19. 개발 단계

### Phase 0 — 프로젝트 설계 및 문서 구축

- 기술 스택 검토
- 시스템 아키텍처 설계
- 프로젝트 폴더 구조
- PROJECT.md 작성
- AGENTS.md 작성
- README.md 초기 작성
- docs/PROGRESS.md 작성
- docs/DECISIONS.md 작성
- 데이터 모델 설계
- API 계약 초안
- 상태 머신 설계
- 테스트 시나리오 정리

완료 기준: 핵심 구현에 필요한 설계와 개발 단계가 문서화됨.

### Phase 1 — 개발 환경 및 기본 통신

- Spring Boot Integration Server 생성
- Spring Boot Target Simulator 생성
- React 프로젝트 생성
- PostgreSQL 연결
- MyBatis 설정
- Flyway 구성
- Docker Compose 환경 구성
- 기본 API 작성
- A → B 실제 HTTP 통신
- 기본 Trace ID 전파
- 기본 화면 구성
- 빌드 및 테스트 환경

완료 기준: 두 Spring Boot 서비스가 별도 프로세스로 구동되고 실제 HTTP 요청/응답 가능.

### Phase 2 — 인터페이스 및 전문 관리

- Interface CRUD
- Interface ID 라우팅
- JSON 전문 입력
- 전문 검증
- 필드 매핑
- 매핑 미리보기
- 전송 이력 저장

완료 기준: 인터페이스 정의에 따라 실제 전문 변환 및 전송 가능.

### Phase 3 — 추적 및 모니터링

- Trace ID 기반 로그
- MDC 적용
- HTTP 헤더 전파
- 단계별 이력
- Attempt 관리
- Trace Explorer
- History 화면
- 대시보드

완료 기준: 하나의 Trace ID로 전체 처리 흐름 확인 가능.

### Phase 4 — 장애 시뮬레이션

- HTTP 400/500
- 지연 응답
- Timeout
- 연결 실패
- 잘못된 응답
- 실패 후 성공
- 중복 요청
- 장애 시나리오 UI

완료 기준: 주요 장애 시나리오를 반복적으로 재현할 수 있음.

### Phase 5 — 재시도 및 멱등성

- 중복 Request ID 검증
- DB Unique Constraint
- 상태 머신
- 자동 재시도
- Exponential Backoff
- 재처리 센터
- 서버 재시작 복구
- Target 멱등성 처리
- 동시성 테스트

완료 기준: 재시도 및 중복 요청 상황에서 데이터 일관성을 유지하고 이력을 추적할 수 있음.

### Phase 6 — UI/UX 및 성능

- 관리 콘솔 리디자인
- 차트와 필터
- JSON 비교 기능
- 대량 이력 조회 최적화
- DB 인덱스 검토
- 오류 처리 통일
- 접근성
- 다크모드
- 성능 측정

완료 기준: 운영 도구로 사용할 수 있는 일관된 사용자 경험 제공.

### Phase 7 — 통합 테스트

- 자동화 테스트 보강
- Testcontainers 통합 테스트
- E2E 테스트
- Docker Compose 실행 검증
- GitHub Actions
- README 완성
- 시스템 아키텍처 다이어그램
- 장애 재현 시나리오 문서
- 기술적 문제 및 해결 과정
- 실행 스크린샷
- 데모 영상용 시나리오 정리

완료 기준: README 절차에 따라 다른 개발자가 로컬에서 시스템을 구동하고 장애 재현 테스트를 수행할 수 있음.

---

## 20. 보안 및 운영 원칙

포트폴리오 프로젝트이지만 운영 시스템과 유사한 기본 안전성을 확보한다.

- 실제 고객사 데이터 사용 금지
- 가상 데이터 사용
- DB 계정 및 암호 환경 변수 관리
- 실제 암호를 Git에 커밋하지 않음
- SQL Injection 방지
- MyBatis 안전한 파라미터 바인딩
- 무제한 재시도 금지
- Timeout 필수 설정
- 외부 URL 접근 제한
- 요청 Payload 크기 제한
- 로그 민감정보 마스킹
- 개발용 장애 시뮬레이터를 공개 운영 환경에 무제한 노출하지 않음
- 장애 재현 API 사용 범위 제한
- 개발용 CORS 설정을 운영 환경에 그대로 적용하지 않음

프로젝트 실행은 로컬 Docker Compose를 기본으로 한다.

프론트엔드만 GitHub Pages에 배포하면 백엔드 API가 자동으로 운영되는 것은 아니므로 배포 방식을 명확히 구분한다.

---

## 21. 최종 완료 조건

- [ ] 두 시스템 간 실제 HTTP 통신
- [ ] Interface ID 기반 라우팅
- [ ] JSON 전문 전송
- [ ] 필드 매핑
- [ ] Source/Target 유효성 검사
- [ ] 요청/응답 DB 저장
- [ ] 요청 상태 관리
- [ ] 전송 이력 조회
- [ ] Trace ID 로그 추적
- [ ] Attempt ID 기반 시도별 추적
- [ ] 장애 시뮬레이터
- [ ] Timeout 재현
- [ ] 중복 요청 처리
- [ ] 자동 재시도
- [ ] 수동 재처리
- [ ] 멱등성 검증
- [ ] 대시보드
- [ ] Trace Explorer
- [ ] MyBatis 및 Flyway 설정
- [ ] Docker Compose 실행
- [ ] 통합 테스트
- [ ] README 문서화
- [ ] PROJECT.md 최신화
- [ ] AGENTS.md 유지
- [ ] PROGRESS.md 최신화
- [ ] DECISIONS.md 기록

---

## 22. 에이전트 작업 규칙

당신은 Java/Spring Boot 백엔드, React 프론트엔드, DB 설계 및 분산 시스템 장애 처리 경험이 있는 시니어 풀스택 개발자 역할이다.

다음 규칙을 따른다.

1. 개발 시작 전 필수 관리 문서를 읽는다.
2. 현재 저장소와 진행 상태를 분석한다.
3. 현재 Phase의 범위를 확인한다.
4. 코드를 구현하기 전에 관련 인터페이스와 상태 모델을 확인한다.
5. 구현 과정에서 기존 정상 기능을 보존한다.
6. 모든 핵심 로직에 테스트를 추가한다.
7. 빌드와 테스트를 실행한다.
8. 발생한 오류를 분석하고 해결한다.
9. 완료 후 PROGRESS.md를 업데이트한다.
10. 주요 기술 선택을 DECISIONS.md에 기록한다.
11. 실제 구현된 기능에 맞춰 README.md를 최신화한다.
12. 실행하지 않은 테스트는 통과했다고 보고하지 않는다.
13. 기능을 임의로 삭제하거나 축소하지 않는다.
14. 동작하지 않는 API를 정상 동작하는 것처럼 표시하지 않는다.
15. 실제 데이터가 없는 지표를 실시간 운영 통계처럼 표현하지 않는다.

작업 완료 시 생성·수정 파일, 구현 기능, 테스트 결과, 기술적 결정, 남은 문제, 다음 Phase를 보고한다.

---

## 23. 첫 실행 지시

이번 첫 실행에서는 **Phase 0과 Phase 1까지만 진행한다.**

작업 순서:

1. 현재 저장소 및 Git 상태 확인
2. 기술 스택 버전 및 호환성 검토
3. 프로젝트 전체 아키텍처 설계
4. 핵심 데이터 모델 및 상태 모델 설계
5. 5개 관리 문서 생성
6. Spring Boot Integration Server 초기화
7. Spring Boot Target Simulator 초기화
8. React + TypeScript 초기화
9. PostgreSQL / MyBatis / Flyway 설정
10. Docker Compose 구성
11. 실제 A → B HTTP 전송 구현
12. 기본 Trace ID 발급 및 전파
13. 기본 화면 구성
14. 테스트 및 빌드
15. 문서 최신화
16. 작업 결과 보고

Phase 2 이후의 고급 기능을 먼저 구현하지 않는다.

다만 향후 기능 확장을 고려하여 초기 아키텍처를 설계한다.

최종적으로 개발 환경에서 두 Spring Boot 애플리케이션이 서로 HTTP 통신을 수행하고, React 화면에서 테스트 요청을 전송할 수 있는 상태까지 구현한다.

**이 프로젝트의 최종 목표는 가상의 시스템 연동을 통해 성공, 실패, Timeout, 재시도, 중복 요청, 장애 복구의 전체 과정을 명확히 재현하고 추적할 수 있는 실전형 시스템 연동 검증 플랫폼을 만드는 것이다.**

이제 프로젝트 분석과 설계를 진행하고 Phase 0부터 개발을 시작하라.


## 24. 현재 구현 범위 — 2026-10-16 Phase 2

사용자의 후속 지시에 따라 Phase 2를 진행했다. DB 기반 인터페이스 CRUD/비활성화/버전, 출발 시스템 등록과 대상 허용 목록, Source/Target 필드 검증, 결정적 점 경로 매핑·형 변환·Null/기본값 정책, 미리보기, 실제 HTTP 전송, 마스킹된 전후·응답 전문 이력과 기본 조회를 제공한다. 구체적인 지원 구문과 한도는 docs/API.md, 결정과 검증 근거는 DECISIONS/PROGRESS를 기준으로 한다.

검증/매핑 실패는 Attempt ID 없이 단계별 오류로 저장하고 HTTP를 호출하지 않는다. 접수 시 불변 설정 버전을 고정하며 HTTP 동안 DB 트랜잭션을 유지하지 않는다. 요청 고유성/멱등성/자동·수동 재시도/전체 Trace 타임라인은 아직 구현하지 않았다. Docker Compose/Testcontainers/원격 CI의 미검증 상태도 유지한다.

배열 인덱스·와일드카드·사용자 스크립트·시간대 날짜 변환과 보호된 재처리 원본 저장은 지원하지 않는다. 현재 지원은 객체 점 경로, 배열 값 전체 복사, LocalDate/LocalDateTime, 마스킹된 진단 snapshot이다. 최종 요구사항을 완료한 것으로 해석하지 않는다.

## 25. 현재 구현 범위 — 2026-10-19 Phase 3

사용자의 Phase 3 진행 요청에 따라 Integration 단계별 이벤트와 HTTP Attempt 원자적 저장, Trace Explorer, 이력 기간·시간·오류 표시, 기간별 DB 집계 대시보드를 추가했다. Request/Trace/Attempt 의미 및 HTTP 트랜잭션 경계를 유지한다. Target 내부 수신·응답 생성은 구조화 로그에 남으며 Integration Explorer의 이벤트와 구분한다. 과거 누락 이벤트/Attempt를 합성하지 않는다. 세부 단계별 실행시간·중앙 로그 수집·재처리 대기·재시도·멱등성은 제공하지 않는다. 검증 범위와 실제 결과는 PROGRESS를 따른다.

## 26. 현재 구현 범위 — 2026-10-19 Phase 4

사용자의 다음 Phase 요청에 따라 요청 범위·유효기간이 있는 Target DB 시나리오, 실제 HTTP 오류/지연/Timeout/응답 계약 오류/실패 후 성공/중복 실험과 고정된 닫힌 주소의 실제 연결 실패, Simulation Lab UI를 추가했다. 일반 전송과 격리하며 기존 상태·Trace·Attempt·트랜잭션 정책을 유지한다. 중복 실험은 시나리오 내 반복 수신 409일 뿐 업무 멱등성은 아니다. 자동/수동 재처리와 복구는 Phase 5에 남긴다. 검증 범위·제약은 PROGRESS/API/ADR-011을 따른다.

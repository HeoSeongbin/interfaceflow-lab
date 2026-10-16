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

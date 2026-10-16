# InterfaceFlow Lab 작업 규칙

1. 작업 전 PROJECT.md 전체 또는 관련 명세, docs/PROGRESS.md, docs/DECISIONS.md를 읽는다.
2. 기존 코드와 테스트를 확인하고 현재 Phase 범위를 확인한다. 첫 실행은 Phase 0·1만 진행한다.
3. 구현 전 API 계약, 식별자와 상태 모델을 확인한다. 기존 정상 기능을 보존한다.
4. Java 21, Spring Boot, MyBatis XML, PostgreSQL, React TypeScript를 유지한다.
5. 외부 HTTP 호출을 DB 트랜잭션으로 감싸지 않는다. 불확실한 처리 결과를 확정 실패로 바꾸지 않는다.
6. 핵심 로직에 의미 있는 테스트를 추가하고 빌드·테스트를 실행한다. 실행하지 않은 검증은 통과로 기록하지 않는다.
7. 구현 후 PROGRESS, README를 갱신한다. 주요 설계 변경은 DECISIONS에 ADR로 기록한다.
8. 가상 데이터만 사용하고 전문·인증정보를 로그에 출력하지 않는다.
9. 완료 보고에는 변경 파일, 기능, 테스트 결과, 결정, 제약, 다음 Phase를 포함한다.

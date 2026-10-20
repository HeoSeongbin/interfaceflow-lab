# 실행·장애 재현 데모

가상 데이터만 사용한다. 화면 녹화 영상 자체는 생성하지 않았으며 아래는 약 7분 분량의 촬영 대본이다. 각 실험은 새 Request ID/시나리오를 사용하고 DB와 REPLAY_KEY를 유지한다. 기본 샘플의 자동 시도 한도는 1회다.

| 구간 | 조작 | 확인할 근거 |
| --- | --- | --- |
| 0:00~1:00 정상 통신 | 전문 전송에서 IF-USER-001 샘플 전송 | SUCCESS, Request/Trace/Attempt 세 식별자, Target 응답의 같은 Trace |
| 1:00~2:00 변환·검증 | 미리보기의 employee_id/employee_name 확인. userName을 제거해 전송 | SOURCE 검증 실패, Attempt 없음, Target 미호출. 이력 상세의 마스킹 전문/설정 버전 |
| 2:00~3:00 명확한 실패·불확실성 | Simulation Lab에서 새 HTTP_400 및 TIMEOUT 수동 시나리오 등록·실행 | 400은 FAILED, TIMEOUT은 UNKNOWN. timeout보다 큰 지연 설정. UNKNOWN을 업무 실패로 단정하지 않음 |
| 3:00~4:30 자동 재시도 | 별도 인터페이스에 retryMaxAttempts=3 저장. FAIL_THEN_SUCCESS/failureCount=2를 등록하고 재시도 정책으로 실행 | Retry Center WAIT/LEASED→DONE. Trace Explorer 같은 요청/Trace, 세 개의 서로 다른 Attempt, 마지막 SUCCESS |
| 4:30~5:30 결과 재사용 | TIMEOUT 또는 INVALID_RESPONSE를 새 신뢰성 시나리오로 실행 | 두 번째 Attempt 성공, Target 업무 결과 한 건과 replay receipt. 수동 execute는 독립 실험이므로 이 보장을 주장하지 않음 |
| 5:30~6:30 조회 | 이력 필터·이전/다음·전문 비교, 대시보드 현황 조회 | ∅는 필드 없음, 마스킹 경계. UNKNOWN 별도 차트, UTC 기간, 모바일 표/차트 스크롤 |
| 6:30~7:00 자동 검증 근거 | 아래 Compose 검증 결과와 보고서 표시 | Testcontainers/실제 Nginx→Integration→Target→DB/E2E/중단 복구 결과와 한계 |

## 자동 컨테이너 복구 실험

Docker 엔진과 Node 22.12+, frontend npm 의존성이 필요하다. 저장소 루트에서 실행한다.

```powershell
node scripts/Test-Compose.mjs
```

스크립트는 무작위 프로젝트명·빈 테스트 volume·동적 loopback 포트·임시 암호/키로 전체 이미지를 빌드한다. 기본 .env와 사용자 서비스를 수정하지 않는다. fresh migration(Integration V1~V7, Target V1~V3)과 실제 Nginx 경유 데스크톱/모바일 E2E를 확인한다. Target 업무 결과 커밋 직후 자신이 만든 Integration 컨테이너만 SIGKILL하고 같은 키/DB로 다시 시작한다. 실제 60초 lease 만료 후 같은 Request/Trace·새 Attempt·이전 UNKNOWN/최종 SUCCESS·Target 업무 결과 한 건/replay 한 건을 DB에서 대조한다.

정상/실패 종료 시 자신이 만든 프로젝트의 로그를 수집하고 컨테이너/네트워크/테스트 volume을 삭제한다. 프로젝트 충돌은 시작 전에 거절한다. 강제 OS 종료 등으로 finally가 실행되지 않으면 콘솔에 출력된 `iflab-test-...` 프로젝트만 확인한 후 정리한다. Docker Desktop 전체 종료나 다른 프로젝트의 down/prune은 필요하지 않다. 로그/결과는 `.tools/phase7-compose.log`, `.tools/phase7-compose-result.json`, Playwright 보고서는 frontend/playwright-report에 있다. 결과 파일에는 암호/키를 기록하지 않는다.

Chrome 기본 채널을 사용한다. 설치된 Edge는 PLAYWRIGHT_CHANNEL=msedge, 별도 Chromium 설치 후에는 chromium을 지정한다. CI는 Chromium 채널을 사용하므로 로컬 Chrome 성공과 원격 CI 성공을 구분한다.

## 고장 진단

- 5173/8080/8081/5432 충돌: 사용자 프로세스를 임의 종료하지 말고 .env의 FRONTEND_PORT/INTEGRATION_PORT/TARGET_PORT/DB_PORT를 바꾼다. 내부 서비스 주소는 바꾸지 않는다.
- 일반 전송 503: Integration의 REPLAY_KEY(32바이트 base64)를 확인한다. 기존 DB 복구는 원래 키가 필요하다.
- 재시도하지 않음: 기본 자동 한도 1회, 재시도 가능 오류인지/큐 상태/nextAt 확인. 검증·매핑·400/409는 새 전문과 새 Request ID로 전송한다.
- UNKNOWN 뒤 DEAD_LETTER: 한도 소진 또는 보호 원본/Target capability 확인 필요. UNKNOWN을 임의 SUCCESS/FAILED로 보정하지 않는다.
- Docker/Testcontainers 연결 실패: 엔진 실행과 docker version의 Server 확인. Docker 29는 Testcontainers 1.21.4 패치로 검증했다.
- 이력에 원문 없음: 공개 진단 값은 마스킹된다. 보호 원문은 공개 API로 복구할 수 없다.

## 기술 문제와 해결 근거

- HTTP 중 DB 잠금 → 짧은 접수/완료 트랜잭션과 불변 설정 (ADR-007).
- Timeout 뒤 이중 업무 처리 → Target 고유키/hash/결과 선커밋 및 replay (ADR-012).
- 서버 중단/늦은 완료 → 영속 스케줄·lease/token fencing (ADR-013).
- 깊은 조회/유효 lease 대량 스캔 → 커서·복합/부분 인덱스·분리 due SQL, 실제 측정 (ADR-014, PERFORMANCE.md).
- 테스트가 Vite·외부 DB에서만 통과 → fresh Compose/Nginx/독립 JVM/실제 PostgreSQL과 crash 회귀 (ADR-015).

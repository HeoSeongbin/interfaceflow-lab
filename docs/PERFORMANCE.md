# Phase 6 조회 성능 측정

2026-10-20, 로컬 PostgreSQL 17.6, Java 21 환경에서 `scripts/Test-QueryPerformance.mjs`를 실행했다. V6/V7 최종 인덱스가 적용된 같은 DB에서 SQL 형태를 비교했다. migration 이전 환경과의 비교는 아니다.

가상 요청 100,000건을 트랜잭션에 추가했다. timestamp당 10건이며 WAIT 20건, 만료 lease 20건, 유효 lease 19,980건, 나머지 terminal 상태다. ANALYZE 후 warm cache에서 EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)을 한 번 기록했다. 종료 시 ROLLBACK 및 기존 데이터 ANALYZE로 복원했다. 가상 행은 다른 작업자에게 공개하지 않는다.

| 조회 | 실행 시간 ms | shared hit blocks | 반환 행 |
| --- | ---: | ---: | ---: |
| 이력 OFFSET 90,000 | 16.826 | 9,432 | 20 |
| 같은 위치 keyset | 0.021 | 11 | 20 |
| 기존 due OR/clock_timestamp | 5.170 | 650 | 20 |
| 분리 due UNION/statement_timestamp | 0.104 | 19 | 20 |

이력 두 쿼리는 ix_request_created_key를 사용했다. 개선 due는 기존 ix_delivery_due와 ix_delivery_lease_due를 사용했다. V6의 중복 WAIT 인덱스와 대체된 기존 시간 인덱스는 V7에서 제거했다.

이 측정에는 커서 시작점 검색, API 직렬화·COUNT·HTTP·브라우저 비용이 포함되지 않는다. 부분 문자열/상태 조합·데이터 분포에 따라 계획이 달라질 수 있다. 단일 로컬 측정이며 운영 지연·처리량 보장이 아니다. 기존 OFFSET API는 계속 제공하고 부분 문자열 검색에는 광범위 스캔 가능성이 남는다. 작업자 동시성 단위 테스트는 두 작업의 동시 실행과 상한만 검증하며 처리량 부하 테스트가 아니다.

재현은 빌드/마이그레이션이 끝난 폐기용 DB에서 frontend 의존성을 설치하고 실행한다. DB_URL/DB_USER/DB_PASSWORD는 해당 DB를 지정한다.

```powershell
$env:ALLOW_PERF_TEST='true'
node scripts/Test-QueryPerformance.mjs
```

전체 실행 계획과 결과는 `.tools/phase6-performance.json`에 기록된다(로컬 생성 파일, Git 제외). 다른 개발 환경에서 숫자가 같을 필요는 없다.

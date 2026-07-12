# Index Aggregate Bench

1.5억 건 원본 이벤트 로그에서 기간 통계 조회가 500으로 실패하는 상황을 재현하고, 대상 식별자와 이벤트 발생 시각의 복합 인덱스, 캐시, 월별 사전 집계 테이블을 비교하는 Spring Boot + MySQL 실험 프로젝트입니다. 원본 로그의 시간 기준은 `created_at`이 아니라 `occurred_at`입니다.

과거 이벤트가 변경되지 않는다는 특성을 기본 전제로 삼습니다. 따라서 월별 사전 집계 테이블을 과거 데이터의 기본 조회 경로로 사용하고, raw 집계는 장애 재현·정합성 비교용으로 남깁니다. 이벤트 추가 직후의 최신 월을 보완하기 위한 인메모리 write-behind는 실시간성 실험 경로이며 운영용 내구성 보장으로 해석하지 않습니다.

## 실험 범위

- `event_logs`에 `30일 × 10,000개 대상 × 일 500건 = 150,000,000건`을 적재합니다.
- 인덱스가 없는 전체 기간 raw 집계가 Compose의 `MAX_EXECUTION_TIME(3000)` 제한으로 timeout/500이 되는 상황을 재현합니다.
- `(target_id, occurred_at)` 복합 인덱스를 포함한 raw 인덱스 적용 전후를 비교합니다.
- cache miss에서는 raw 집계를 다시 실행해 저장하고, cache hit에서는 메모리 캐시 결과를 반환하는 차이를 측정합니다.
- `event_monthly_stats`에 `occurred_at`의 달력 월별로 건수와 합계 지표를 저장하고, 기본 monthly API는 이 테이블만 조회합니다.
- 실시간성이 필요한 별도 실험에서는 realtime monthly API가 `event_monthly_stats` 값과 아직 flush되지 않은 write-behind delta를 합산합니다.
- 월별 rebuild 직후 raw와 monthly의 건수, 합계, 평균, ratio가 일치하는지 비교 응답과 테스트로 확인합니다.

## 빠른 실행

```bash
docker compose up -d --build
./scripts/01_seed_150m.sh
./scripts/02_reproduce_500.sh
./scripts/03_compare_index_cache_monthly_stats.sh
```

스크립트의 목적은 다음과 같습니다.

- `01_seed_150m.sh`: raw 보조 인덱스를 제거하고 1.5억 건을 적재한 뒤 row 수를 확인합니다.
- `02_reproduce_500.sh`: 인덱스 없는 전체 기간 raw 집계가 HTTP 500인지 확인합니다. Compose의 raw timeout은 3초입니다.
- `03_compare_index_cache_monthly_stats.sh`: 고정된 2026년 4월과 `targetId=1` 조건으로 인덱스, cache miss/hit, monthly rebuild와 조회를 비교합니다.

1.5억 건 적재와 인덱스 생성은 로컬 장비에 따라 오래 걸리고 큰 디스크 공간을 사용합니다. Docker 메모리, MySQL `innodb_buffer_pool_size`, JVM, 운영체제 page cache가 결과에 영향을 주므로 측정 결과에 데이터 건수·조회 조건·인덱스 구성·반복 횟수·장비·DB 설정을 함께 기록해야 합니다. cold/warm cache 여부도 구분합니다.

## 실험 행렬

| 실험 | 인덱스 | 캐시 | 집계 방식 | 목적 |
| --- | --- | --- | --- | --- |
| A | 없음 | 사용 안 함 | 원본 raw | timeout/500 재현 |
| B | 있음 | 사용 안 함 | 원본 raw | 인덱스 적용 후 baseline |
| C | 있음 | miss | 원본 raw 후 저장 | cache miss 비용 확인 |
| D | 있음 | hit | 메모리 캐시 | 반복 조회 단축 효과 확인 |
| E | monthly index | 사용 안 함 | 월별 집계 테이블 | 캐시 없는 대안 확인 |

로컬 Docker에서 재현한 시간과 운영에서 관측한 시간은 다른 측정입니다. 운영 관측값으로 기록된 “인덱스 적용 후 약 2분, 캐시 적용 후 약 0.8초”는 로컬 벤치마크 결과와 섞지 않습니다. `elapsedMillis`는 현재 요청의 애플리케이션 측정값이며, 평균·최소값·p95와 반복 횟수를 함께 봅니다.

## 주요 API

- `POST /api/events/seed`: `from`, `days`, `targetCount`, `recordsPerTargetPerDay`, `truncate` 조건으로 원본 이벤트를 생성합니다.
- `POST /api/events`: 이벤트 한 건을 저장하고, 선택적 realtime 경로용 write-behind와 관련 캐시를 커밋 이후 반영합니다. 기본 monthly 집계값은 flush/rebuild 전까지 자동으로 변경하지 않습니다.
- `GET /api/stats/raw`: `occurred_at` 범위의 원본 raw 집계입니다.
- `GET /api/stats/monthly`: `event_monthly_stats` 조회입니다. `from`은 월 초, `to`는 월 말이어야 하며 임의의 부분 월은 거부합니다.
- `GET /api/stats/monthly/realtime`: 실시간성 실험용 조회입니다. monthly DB 값에 pending write-behind delta를 추가합니다.
- `POST /api/stats/monthly/rebuild`: 같은 월 범위를 원본 로그에서 월별 집계 테이블로 재생성합니다.
- `GET /api/stats/compare`: raw와 monthly를 `iterations`회 반복해 성능과 정합성을 함께 비교합니다. 응답의 평균·최소·p95 시간, `statsMatch`, `difference`에서 측정값과 차이를 확인합니다.
- `DELETE /api/stats/cache`: 전체 메모리 캐시를 제거합니다.
- `GET /api/stats/cache`: 메모리 캐시 항목 수를 확인합니다.
- `GET /api/bench/indexes/raw`: raw 보조 인덱스 상태를 확인합니다.
- `POST /api/bench/indexes/raw/drop`: raw 보조 인덱스를 제거합니다.
- `POST /api/bench/indexes/raw/create`: raw 보조 인덱스를 생성합니다.
- `GET /api/bench/row-counts`: `event_logs`와 `event_monthly_stats` row 수를 확인합니다.
- `GET /api/bench/write-behind/status`: pending delta 수, 마지막 성공 시각, 실패 시각·원인, 시도 횟수, 다음 재시도 시각, 최종 실패 여부를 확인합니다.

통계 응답의 `backend`는 `RAW`, `MONTHLY_STATS`, `MONTHLY_STATS_REALTIME` 중 하나입니다. `elapsedMillis`는 해당 조회에 걸린 시간, `cacheHit`은 캐시에서 바로 반환했는지를 의미합니다. `stats`에는 로그 건수, duration·metric·cost 합계, 평균 duration, metric/cost ratio가 들어갑니다.

## 정합성과 write-behind 한계

월별 rebuild는 같은 월 범위를 raw에서 다시 계산하는 독립적인 집계 테이블 복구·검증 수단입니다. 기본 monthly 조회는 write-behind를 알지 못합니다. realtime 경로에서만 pending delta를 별도로 합산하며, flush는 그 실험 경로의 값을 집계 테이블에 반영합니다. flush는 batch마다 `batchId`를 부여하고 처리 이력의 unique 제약과 월별 합산을 같은 DB 트랜잭션에서 수행하므로, 동일 batch 재처리로 합계가 중복 증가하지 않도록 검증합니다. 실패한 flush는 트랜잭션 rollback 후 pending delta를 복원하고 제한된 횟수와 backoff로 재시도합니다.

write-behind 버퍼는 현재 인메모리이므로 프로세스 장애가 발생하면 아직 flush되지 않은 delta가 유실될 수 있습니다. 이 프로젝트는 이를 운영용 내구성 보장으로 표현하지 않습니다. 실제 운영 전환의 후속 과제는 durable outbox, 메시지 브로커 기반 재처리, 원본 저장과 집계 delta의 동일 트랜잭션 처리입니다. 마지막 성공 flush 시각, pending 수, 재시도 횟수, 집계 지연을 관찰하고, 최종 실패 시 운영자 확인이 필요합니다.

## 검증

```bash
./gradlew check
```

자동 테스트는 월 시작일·월 말일, 여러 월 범위, 빈 조건, target/segment 조건, raw/monthly 결과와 파생 지표, realtime 경로의 flush 전 pending 합산, rollback 복원, 재시도와 batch 중복 방지 계약을 검증합니다. 실제 1.5억 건과 MySQL timeout 재현은 Docker 실행 절차로 별도 검증합니다.

## 자세한 내용

- [인덱스와 캐시로 복구한 통계 API에서 집계 테이블 대안 검증](https://velog.io/@dochiri0916/%EC%9D%B8%EB%8D%B1%EC%8A%A4%EC%99%80-%EC%BA%90%EC%8B%9C%EB%A1%9C-%EB%B3%B5%EA%B5%AC%ED%95%9C-%ED%86%B5%EA%B3%84-API%EC%97%90%EC%84%9C-%EC%A7%91%EA%B3%84-%ED%85%8C%EC%9D%B4%EB%B8%94-%EB%8C%80%EC%95%88-%EA%B2%80%EC%A6%9D)

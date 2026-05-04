# Index Aggregate Bench

1.5억 건 통계성 이벤트 로그에서 원본 테이블 집계가 500 에러로 실패하는 상황을 재현하고, 다음 3가지 개선 방식의 차이를 비교하는 Spring Boot + MySQL 프로젝트입니다.

- 원본 `event_logs` 대용량 테이블 직접 집계
- 원본 테이블 인덱스 + `ConcurrentHashMap` 인메모리 캐시
- `event_daily_stats` 일별 집계 테이블

운영 코드 흐름은 Spring Web MVC, Service, JDBC 구조를 따릅니다. 대량 초기 데이터 적재만 1.5억 건을 현실적으로 만들기 위해 JDBC `INSERT ... SELECT`를 사용합니다.

## 이력서용 요약

### [대용량 이벤트 로그 통계 API 장애 복구 및 응답 성능 개선](docs/1.5억%20건%20데이터%20환경에서%20통계%20API를%20살린%20인덱스와%20캐시%20전략.md)

1.5억 건 이상 누적된 통계성 이벤트 로그 API에서 대규모 `GROUP BY` 집계가 반복 실행되며 500 에러와 타임아웃이 발생했습니다. 단일 API가 여러 통계 지표를 한 번에 계산하는 구조였기 때문에, 일부 집계 지연이 전체 요청 실패로 이어지는 문제가 있었습니다.

조회 목적별 API 분리로 불필요한 집계 실행을 줄이고, 조회 조건에 맞춘 복합 인덱스를 적용해 원본 테이블 탐색 범위를 축소했습니다. 단일 인스턴스 환경과 과거 로그 변경이 적은 데이터 특성을 고려해 Redis 대신 `ConcurrentHashMap` 기반 로컬 캐시를 적용했고, 신규 로그 적재 시 관련 식별자 조건의 캐시만 무효화하도록 구성했습니다. 장애 복구 이후에는 같은 데이터 환경에서 [`event_daily_stats` 집계 테이블 대안](docs/인덱스와%20캐시로%20복구한%20통계%20API에서%20집계%20테이블%20대안%20검증.md)을 별도로 검증해, 캐시 없이도 반복 통계 조회를 안정화할 수 있는지 비교했습니다.

그 결과 인덱스가 없는 원본 집계에서 재현되던 500 에러를 정상화했고, 동일 조건 반복 요청은 인메모리 캐시 hit 기준 1ms 수준으로 단축했습니다. 추가 검증에서 raw 인덱스 직접 집계는 반복 측정 기준 평균 38ms, 일별 집계 테이블 조회는 1ms 안팎으로 확인했습니다.

- 사용 기술: Java, Spring Boot, MySQL, JDBC, 인덱스 최적화, `ConcurrentHashMap` 로컬 캐시, 사전 집계 테이블

## 실행

```bash
docker compose up -d --build
```

애플리케이션은 `http://localhost:8080`에서 실행됩니다. Docker Compose 환경은 raw 집계 조회에 MySQL `MAX_EXECUTION_TIME(3000)` 힌트를 붙입니다. 인덱스 없는 1.5억 건 전체 기간 집계가 3초를 넘기면 애플리케이션은 500 응답을 반환합니다.

## 1.5억 건 재현

```bash
./scripts/01_seed_150m.sh
```

스크립트는 raw 테이블 보조 인덱스를 제거한 뒤 다음 조건으로 데이터를 생성합니다.

```text
30 days * 10,000 target ids * 500 records per target per day = 150,000,000 rows
```

인덱스가 없는 상태에서 500 에러를 확인합니다.

```bash
./scripts/02_reproduce_500.sh
```

또는 직접 호출합니다.

```bash
curl -i 'http://localhost:8080/api/stats/raw?from=2026-04-01&to=2026-04-30'
```

## 성능 비교

```bash
./scripts/03_compare_index_cache_daily_stats.sh
```

스크립트는 raw 인덱스를 생성하고, 집계 테이블을 재생성한 뒤 다음을 순서대로 측정합니다.

- raw 테이블 + 인덱스
- raw 테이블 + 인덱스 + 캐시 최초 조회
- raw 테이블 + 인덱스 + 캐시 hit
- 일별 집계 테이블
- raw와 daily stats 반복 비교

### 실험 결과

다음 결과는 로컬 Docker Compose 환경에서 `event_logs` 150,000,000건을 적재한 뒤 측정했습니다.

- 측정 쿼리: `targetId=1&from=2026-04-01&to=2026-04-30`
- 대상 데이터: 30일 * 10,000 target ids * 500 records per target per day
- raw 보조 인덱스: `(occurred_at, target_id, segment_id)`, `(target_id, occurred_at)`, `(segment_id, occurred_at)`
- 집계 테이블 row 수: 300,000건

| 방식 | 동작 |                                 측정 시간 |
| --- | --- |--------------------------------------:|
| raw + index 최초 조회 | `event_logs` 원본 테이블에서 직접 집계 |                               2,373ms |
| raw + index + cache warmup | 캐시 miss 후 raw 집계 결과를 인메모리 캐시에 저장 |                                  63ms |
| raw + index + cache hit | 같은 조건의 집계 결과를 인메모리 캐시에서 반환 |                                   1ms |
| daily stats | `event_daily_stats` 사전 집계 테이블 조회 |                                   3ms |
| raw + index 10회 반복 | 캐시 없이 raw 직접 집계 반복 |                      평균 38ms, 최소 34ms |
| daily stats 10회 반복 | 사전 집계 테이블 조회 반복 | 평균 0ms, 최소 0ms, 마지막 실측 1ms, 평균 1ms 미만 |

집계 테이블 재생성은 257,928ms가 걸렸고, `event_daily_stats` 300,000건을 생성했습니다.

`cache warmup`은 캐시가 이미 빨라진 상태를 뜻하지 않습니다. 첫 캐시 사용 요청에서 캐시 miss가 발생하고, DB 조회 결과를 캐시에 저장하는 단계입니다. 위 실험에서 warmup 시간이 최초 raw 조회보다 짧은 이유는 직전 raw 조회로 MySQL 버퍼와 OS 파일 캐시가 데워진 영향이 큽니다.

결론적으로, 같은 조건의 조회가 반복되면 인메모리 캐시 hit가 가장 빠릅니다. 다만 캐시 miss나 데이터 변경에 따른 무효화까지 고려하면, 사전 집계 테이블은 캐시 없이도 1ms 안팎의 안정적인 조회 시간을 제공합니다. raw + index는 인덱스와 DB 버퍼 상태에 따라 성능이 크게 달라집니다.

## 주요 API

- `POST /api/events/seed`: 샘플 이벤트 로그 데이터 생성
- `GET /api/stats/raw`: 원본 event_logs 테이블 직접 집계
- `GET /api/stats/daily`: 일별 집계 테이블 조회
- `GET /api/stats/compare`: 원본 집계와 집계 테이블 반복 측정
- `POST /api/stats/daily/rebuild`: 일별 집계 테이블 재생성
- `DELETE /api/stats/cache`: 전체 캐시 제거
- `GET /api/bench/row-counts`: 원본/집계 테이블 row 수 확인
- `GET /api/bench/indexes/raw`: raw 보조 인덱스 상태 확인
- `POST /api/bench/indexes/raw/drop`: raw 보조 인덱스 제거
- `POST /api/bench/indexes/raw/create`: raw 보조 인덱스 생성

## 패키지 구조

```text
com.dochiri.indexaggregatebench
├── application
│   ├── dto
│   └── service
└── infrastructure
    ├── cache
    ├── persistence
    └── web
```

## 테이블 구성

`event_logs`

- 원본 이벤트 로그
- 보조 인덱스: `(occurred_at, target_id, segment_id)`, `(target_id, occurred_at)`, `(segment_id, occurred_at)`

`event_daily_stats`

- 일별, 주요 식별자별 사전 집계 테이블
- 기본 키: `(stat_date, target_id, segment_id)`
- 보조 인덱스: `(target_id, stat_date)`, `(segment_id, stat_date)`

## 주의

1.5억 건 적재와 인덱스 생성은 로컬 장비 성능에 따라 오래 걸립니다. 디스크 여유 공간도 충분히 필요합니다. 빠른 기능 확인만 하려면 seed 요청의 `targetCount`나 `recordsPerTargetPerDay` 값을 줄여서 실행하면 됩니다.

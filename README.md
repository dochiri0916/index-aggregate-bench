# Index Aggregate Bench

1.5억 건 전기차 배터리 운행 로그에서 원본 테이블 집계가 500 에러로 실패하는 상황을 재현하고, 다음 3가지 개선 방식의 차이를 비교하는 Spring Boot + MySQL 프로젝트입니다.

- 원본 `vehicles` 대용량 테이블 직접 집계
- 원본 테이블 인덱스 + `ConcurrentHashMap` 인메모리 캐시
- `vehicle_daily_stats` 일별 집계 테이블

운영 코드 흐름은 Spring Web MVC, Service, JDBC 구조를 따릅니다. 대량 초기 데이터 적재만 1.5억 건을 현실적으로 만들기 위해 JDBC `INSERT ... SELECT`를 사용합니다.

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
30 days * 10,000 vehicle ids * 500 records per id per day = 150,000,000 rows
```

인덱스가 없는 상태에서 500 에러를 확인합니다.

```bash
./scripts/02_reproduce_500.sh
```

또는 직접 호출합니다.

```bash
curl -i 'http://localhost:8080/api/vehicle-stats/raw?from=2026-04-01&to=2026-04-30'
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

## 주요 API

- `POST /api/vehicles/seed`: 샘플 차량 운행 데이터 생성
- `GET /api/vehicle-stats/raw`: 원본 vehicles 테이블 직접 집계
- `GET /api/vehicle-stats/daily`: 일별 집계 테이블 조회
- `GET /api/vehicle-stats/compare`: 원본 집계와 집계 테이블 반복 측정
- `POST /api/vehicle-stats/daily/rebuild`: 일별 집계 테이블 재생성
- `DELETE /api/vehicle-stats/cache`: 전체 캐시 제거
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

`vehicles`

- 원본 운행 로그
- 보조 인덱스: `(started_at, id, battery_id)`, `(id, started_at)`, `(battery_id, started_at)`

`vehicle_daily_stats`

- 일별, 차량별, 배터리별 사전 집계 테이블
- 기본 키: `(stat_date, id, battery_id)`
- 보조 인덱스: `(id, stat_date)`, `(battery_id, stat_date)`

## 주의

1.5억 건 적재와 인덱스 생성은 로컬 장비 성능에 따라 오래 걸립니다. 디스크 여유 공간도 충분히 필요합니다. 빠른 기능 확인만 하려면 seed 요청의 `idCount`나 `recordsPerIdPerDay` 값을 줄여서 실행하면 됩니다.

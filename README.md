# Index Aggregate Bench

1.5억 건 통계성 이벤트 로그에서 원본 테이블 집계가 500 에러로 실패하는 상황을 재현하고, 인덱스와 캐시, 일별 집계 테이블 대안을 비교하는 Spring Boot + MySQL 프로젝트입니다.
여기에는 실행 가능한 코드와 핵심 요약만 두고, 설계 배경과 선택 기준은 블로그 글로 분리했습니다.

## 실험 범위

- 원본 `event_logs` 대용량 테이블 직접 집계 실패 상황을 재현합니다.
- 조회 조건에 맞춘 복합 인덱스로 원본 테이블 집계 성능 변화를 확인합니다.
- `ConcurrentHashMap` 기반 인메모리 캐시로 동일 조건 반복 조회를 단축합니다.
- 이벤트 저장 직후 캐시와 write-behind 버퍼에 일별 통계를 반영하고, DB 집계 테이블에는 주기적으로 배치 저장합니다.
- flush 전 조회도 DB 집계 값과 write-behind 대기 값을 합산해 최신 통계를 반환합니다.
- `event_daily_stats` 일별 집계 테이블을 구성해 캐시 없는 반복 통계 조회 대안을 검증합니다.
- raw 인덱스 직접 집계, raw 인덱스 + 캐시, 일별 집계 테이블 조회 성능을 비교합니다.

## 빠른 실행

```bash
docker compose up -d --build
./scripts/01_seed_150m.sh
./scripts/02_reproduce_500.sh
./scripts/03_compare_index_cache_daily_stats.sh
```

애플리케이션은 `http://localhost:8080`에서 실행됩니다.
1.5억 건 적재와 인덱스 생성은 로컬 장비 성능에 따라 오래 걸릴 수 있습니다.

## 주요 API

- `GET /api/stats/raw`: 원본 `event_logs` 테이블 직접 집계
- `GET /api/stats/daily`: 일별 집계 테이블 조회
- `GET /api/stats/compare`: 원본 집계와 집계 테이블 반복 측정
- `POST /api/stats/daily/rebuild`: 일별 집계 테이블 재생성
- `DELETE /api/stats/cache`: 전체 캐시 제거
- `GET /api/bench/row-counts`: 원본/집계 테이블 row 수 확인
- `POST /api/bench/indexes/raw/drop`: raw 보조 인덱스 제거
- `POST /api/bench/indexes/raw/create`: raw 보조 인덱스 생성

## 자세한 내용

- [인덱스와 캐시로 복구한 통계 API에서 집계 테이블 대안 검증](https://velog.io/@dochiri0916/%EC%9D%B8%EB%8D%B1%EC%8A%A4%EC%99%80-%EC%BA%90%EC%8B%9C%EB%A1%9C-%EB%B3%B5%EA%B5%AC%ED%95%9C-%ED%86%B5%EA%B3%84-API%EC%97%90%EC%84%9C-%EC%A7%91%EA%B3%84-%ED%85%8C%EC%9D%B4%EB%B8%94-%EB%8C%80%EC%95%88-%EA%B2%80%EC%A6%9D)

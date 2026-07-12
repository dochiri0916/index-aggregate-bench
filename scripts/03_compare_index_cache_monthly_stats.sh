#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
FROM="2026-04-01"
TO="2026-04-30"
QUERY="targetId=1&from=$FROM&to=$TO"

request() {
  local label="$1"
  local url="$2"
  echo
  echo "[$label] 조건: targetId=1, 월 범위=$FROM..$TO"
  curl -sS "$url"
  echo
}

curl -sS -X DELETE "$BASE_URL/api/stats/cache" > /dev/null
echo "측정 기준: 2026-04 한 달, targetId=1, cache 상태와 backend를 응답에서 확인합니다."

request "raw index 생성" "$BASE_URL/api/bench/indexes/raw/create"
request "monthly rebuild" "$BASE_URL/api/stats/monthly/rebuild?from=$FROM&to=$TO"
request "raw with index (cache 미사용)" "$BASE_URL/api/stats/raw?$QUERY"
request "raw with index + cache miss" "$BASE_URL/api/stats/raw?$QUERY&cache=true"
request "raw with index + cache hit" "$BASE_URL/api/stats/raw?$QUERY&cache=true"
request "monthly stats (cache 미사용, DB 사전 집계)" "$BASE_URL/api/stats/monthly?$QUERY"
request "monthly realtime (write-behind overlay 경로)" "$BASE_URL/api/stats/monthly/realtime?$QUERY"
request "compare (10회 반복, cache 미사용)" "$BASE_URL/api/stats/compare?$QUERY&iterations=10&cache=false"

#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
QUERY="id=1&from=2026-04-01&to=2026-04-30"

curl -s -X DELETE "$BASE_URL/api/vehicle-stats/cache" > /dev/null

echo "raw indexes:"
curl -s -X POST "$BASE_URL/api/bench/indexes/raw/create"
echo

echo "daily stats rebuild:"
curl -s -X POST "$BASE_URL/api/vehicle-stats/daily/rebuild?from=2026-04-01&to=2026-04-30"
echo

echo "raw with index:"
curl -s "$BASE_URL/api/vehicle-stats/raw?$QUERY"
echo

echo "raw with index + cache warmup:"
curl -s "$BASE_URL/api/vehicle-stats/raw?$QUERY&cache=true"
echo

echo "raw with index + cache hit:"
curl -s "$BASE_URL/api/vehicle-stats/raw?$QUERY&cache=true"
echo

echo "daily stats:"
curl -s "$BASE_URL/api/vehicle-stats/daily?$QUERY"
echo

echo "compare:"
curl -s "$BASE_URL/api/vehicle-stats/compare?$QUERY&iterations=10&cache=false"
echo

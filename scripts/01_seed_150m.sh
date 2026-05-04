#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -s -X POST "$BASE_URL/api/bench/indexes/raw/drop"
echo

curl -s -X POST "$BASE_URL/api/vehicles/seed" \
  -H 'Content-Type: application/json' \
  -d '{
    "from": "2026-04-01",
    "days": 30,
    "idCount": 10000,
    "recordsPerIdPerDay": 500,
    "truncate": true
  }'
echo

curl -s "$BASE_URL/api/bench/row-counts"
echo

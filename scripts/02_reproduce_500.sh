#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

curl -i -s "$BASE_URL/api/vehicle-stats/raw?from=2026-04-01&to=2026-04-30"
echo

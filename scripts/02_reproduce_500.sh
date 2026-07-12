#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

echo "raw 집계는 Compose의 MAX_EXECUTION_TIME(3000) 설정으로 timeout/500을 재현합니다."
status="$(curl -sS -o "$BODY_FILE" -w '%{http_code}' \
  "$BASE_URL/api/stats/raw?from=2026-04-01&to=2026-04-30")"
cat "$BODY_FILE"
echo

if [[ "$status" != "500" ]]; then
  echo "expected HTTP 500 from raw timeout, got HTTP $status" >&2
  exit 1
fi

echo "raw timeout/500 재현 성공 (HTTP $status)"

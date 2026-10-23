#!/usr/bin/env bash
# Poll /actuator/health via CloudFront until it returns <status> (P2-T09).
#   usage: wait-for-health.sh <domain> <status>   200 awake · 503 asleep (no targets) · 502 hibernated (no origin)
set -euo pipefail

domain="$1"
expected_status="$2"

ATTEMPTS=24 # × 10 s = 4 min
DELAY_SECONDS=10

health_url="https://$domain/actuator/health"
status_code=""
for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
  status_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$health_url" || true)
  if [ "$status_code" = "$expected_status" ]; then
    echo "GET $health_url → $status_code"
    exit 0
  fi
  sleep "$DELAY_SECONDS"
done
echo "::error::GET $health_url → $status_code, expected $expected_status"
exit 1

#!/usr/bin/env bash
# Demo client for CareSync: log in through the gateway, then submit claims for specs/03 examples E1, E3, E5, E7.
# Needs the gateway (8080), auth (8081) and claims (8082) running, and curl.
# Reads the member password from AUTH_MEMBER_PASSWORD; with the gateway started using
# GATEWAY_SECURITY_ENABLED=false run `NO_AUTH=1 ./scripts/demo.sh`.
set -euo pipefail

GATEWAY="${GATEWAY:-http://127.0.0.1:8080}"
AUTH_HEADER=()

if [[ -z "${NO_AUTH:-}" ]]; then
  : "${AUTH_MEMBER_PASSWORD:?Set AUTH_MEMBER_PASSWORD (same value the auth service was started with)}"
  login=$(curl -sS -X POST "$GATEWAY/api/v1/auth/token" -H 'Content-Type: application/json' \
    -d "{\"username\":\"member1\",\"password\":\"$AUTH_MEMBER_PASSWORD\"}")
  token=$(printf '%s' "$login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  [[ -n "$token" ]] || { echo "Login failed" >&2; exit 1; }
  AUTH_HEADER=(-H "Authorization: Bearer $token")
  echo "Logged in as member1 (token not printed)."
fi

days_ago() { date -u -d "$1 days ago" +%F 2>/dev/null || date -u -v-"$1"d +%F; }

submit() { # label member daysAgo amount category
  local body
  body=$(printf '{"memberId":"%s","providerId":"PRV-44102","serviceDate":"%s","billedAmount":"%s","benefitCategory":"%s"}' \
    "$2" "$(days_ago "$3")" "$4" "$5")
  echo; echo "$1"
  curl -sS -w '\n-> HTTP %{http_code}\n' -X POST "$GATEWAY/api/v1/claims" "${AUTH_HEADER[@]}" \
    -H 'Content-Type: application/json' -d "$body"
}

run=$((RANDOM % 80000 + 10000))   # own member per example: keeps the 24h duplicate rule (R6) out of the way
submit "E1 in-network primary care 150.00 (expect 120.00 / 30.00)" "MEM-$run"       6   150.00 IN_NETWORK_PRIMARY_CARE
submit "E3 preventive 200.00 (expect 200.00 / 0.00)"                "MEM-$((run+1))" 6   200.00 PREVENTIVE_SCREENING
submit "E5 below minimum 4.99 (expect 400 Validation Failed)"      "MEM-$((run+2))" 1   4.99   GENERAL_MEDICAL
submit "E7 timely filing exceeded (expect DENIED)"                 "MEM-$((run+3))" 400 100.00 IN_NETWORK_PRIMARY_CARE

echo
echo "Next: open http://localhost:8090 (Kafka UI) -> Topics -> claim-lifecycle-events -> Messages,"
echo "and look for [AUDIT NOTIFICATION] lines in the care-notification-service console."
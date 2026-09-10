#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Query smoke failed: $*" >&2; exit 1; }

local_check=false
protocol='=https'
if [[ "${1:-}" == --local && "$#" == 1 ]]; then
  # CI checks the packaged server without Google credentials or live upstream requests.
  [[ -z "${SMOKE_ID_TOKEN:-}" ]] || fail "Local checks must not receive a token."
  SMOKE_URL=http://127.0.0.1:18080
  protocol='=http'
  local_check=true
elif [[ "$#" != 0 ]]; then
  fail "Unsupported arguments."
elif [[ ! "${SMOKE_URL:-}" =~ ^https://[a-z0-9.-]+\.run\.app/?$ ]]; then
  fail "Expected a Cloud Run HTTPS URL."
fi
SMOKE_URL="${SMOKE_URL%/}"

response_body="$(mktemp)"
trap 'rm -f "$response_body"' EXIT
http_status=

request() {
  local path="$1" authorized="${2:-true}" header=
  if [[ "$authorized" == true && -n "${SMOKE_ID_TOKEN:-}" ]]; then
    header="X-Serverless-Authorization: Bearer $SMOKE_ID_TOKEN"
  fi
  # No redirects; token goes through stdin, never command-line arguments.
  if ! http_status="$(curl -q --silent --proto "$protocol" \
    --connect-timeout 5 --max-time 25 --max-filesize 2097152 \
    --output "$response_body" --write-out '%{http_code}' \
    --header @- "$SMOKE_URL$path" <<< "$header")"; then
    fail "$path: request failed."
  fi
}

check() {
  local path="$1" expected="$2" filter="${3:-}"
  request "$path"
  [[ "$http_status" == "$expected" ]] || fail "$path: expected $expected, received $http_status."
  if [[ -n "$filter" ]]; then
    jq --exit-status "$filter" "$response_body" >/dev/null 2>&1 \
      || fail "$path: unexpected JSON."
  fi
  echo "OK $path $http_status"
}

if [[ "${EXPECT_PRIVATE:-false}" == true ]]; then
  [[ -n "${SMOKE_ID_TOKEN:-}" ]] || fail "Private checks require an ID token."
  request /health false
  [[ "$http_status" == 401 || "$http_status" == 403 ]] \
    || fail "First deployment must reject unauthenticated requests."
fi

check /health 200 '.status == "ok"'
check '/api/v1/matches/upcoming?page=0' 400 '.code == "INVALID_REQUEST"'
check /openapi.json 404
check /swagger 404
check /api/v1/notification-targets 404

if [[ "$local_check" == false ]]; then
  check /api/v1/matches/upcoming 200 '.category == "upcoming" and (.groups | type == "array")'
  check /api/v1/news 200 '.items | type == "array"'
fi

#!/usr/bin/env bash
set -euo pipefail

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly service_helper="$script_dir/observability-service.sh"
readonly policy_helper="$script_dir/observability-policies.sh"
readonly monitoring_root="${MONITORING_API_ROOT:-https://monitoring.googleapis.com/v3}"
readonly logging_root="${LOGGING_API_ROOT:-https://logging.googleapis.com/v2}"
readonly error_root="${ERROR_REPORTING_API_ROOT:-https://clouderrorreporting.googleapis.com/v1beta1}"
readonly poll_seconds="${OBSERVABILITY_POLL_SECONDS:-30}"
readonly fault_reserve=1800

fail() { echo "Observability live validation failed: $*" >&2; exit 1; }
require_env() { test -n "${!1:-}" || fail "Missing $1."; }
now() { date +%s; }
sleep_for() { "${OBSERVABILITY_SLEEP_COMMAND:-sleep}" "$1"; }

set_validation_deadline() {
  OBSERVABILITY_DEADLINE_EPOCH="$(jq -enr --arg start "$1" --argjson now "$(now)" '
    ($start | fromdateiso8601) as $started | select($started <= $now) |
    [$now + 5400, $started + 7200] | min
  ' 2>/dev/null)" || fail 'The workflow attempt start time is invalid.'
  export OBSERVABILITY_DEADLINE_EPOCH
}

require_fault_time() {
  test "$(now)" -lt "$((OBSERVABILITY_DEADLINE_EPOCH - fault_reserve))" \
    || fail 'The validation fault cutoff expired; restoration reserve is active.'
}

require_fault_window() {
  local seconds="$1"
  test "$(( $(now) + seconds ))" -le "$((OBSERVABILITY_DEADLINE_EPOCH - fault_reserve))" \
    || fail 'Insufficient bounded fault time remains for the next phase.'
}

# Check before a provider poll; optionally wait without crossing its time budget.
poll_budget() {
  local phase="$1" delay="${2:-0}" cutoff="$OBSERVABILITY_DEADLINE_EPOCH" remaining
  case "$phase" in
    fault) cutoff=$((cutoff - fault_reserve)) ;;
    recovery) ;;
    *) fail 'Invalid polling phase.' ;;
  esac
  remaining=$((cutoff - $(now)))
  test "$remaining" -gt 0 || fail "The $phase polling budget expired."
  if test "$delay" -gt 0; then
    test "$delay" -le "$remaining" || delay="$remaining"
    sleep_for "$delay"
  fi
}

result() {
  printf 'Observability %s: %s\n' "$1" "$2"
  printf 'Observability %s: %s\n' "$1" "$2" >> "$GITHUB_STEP_SUMMARY"
}

api() {
  local method="$1" url="$2" output="$3" body="${4:-}" token error="$evidence/provider-error"
  if test -n "${OBSERVABILITY_PROVIDER_HTTP:-}"; then
    "$OBSERVABILITY_PROVIDER_HTTP" "$method" "$url" "$output" "$body"
    chmod 600 "$output"
    return
  fi
  local endpoint=provider status curl_exit=0
  local -a body_args=(--header @-)
  case "$url" in
    "$monitoring_root"/projects/*/alerts\?*) endpoint=monitoring.alerts.list ;;
    "$error_root"/projects/*/groupStats\?*) endpoint=errorreporting.groupStats.list ;;
    "$monitoring_root"/projects/*/notificationChannels/*) endpoint=monitoring.notificationChannels.get ;;
  esac
  token="$(gcloud auth print-access-token 2>/dev/null)" || fail 'Could not obtain a provider access token.'
  if test -n "$body"; then
    body_args+=(--header 'Content-Type: application/json' --data-binary "@$body")
  fi
  status="$(curl -q --silent --show-error --fail --connect-timeout 5 --max-time 30 --request "$method" \
    "${body_args[@]}" --output "$output" --write-out '%{http_code}' "$url" \
    <<< "Authorization: Bearer $token" 2> "$error")" || curl_exit=$?
  [[ "$status" =~ ^[0-9]{3}$ ]] || status=000
  if test "$curl_exit" -ne 0 || [[ "$status" != 2[0-9][0-9] ]]; then
    fail "Provider request failed: $endpoint (HTTP $status, curl $curl_exit)."
  fi
  chmod 600 "$output" "$error"
}

gcloud_read() {
  local output="$1"
  shift
  gcloud "$@" > "$output" 2> "$evidence/gcloud-error" || fail 'A bounded cloud inventory request failed.'
  chmod 600 "$output" "$evidence/gcloud-error"
}

decode_token() {
  local payload padding
  payload="$(cut -d. -f2 <<< "$SMOKE_ID_TOKEN" | tr '_-' '/+')"
  padding=$(( (4 - ${#payload} % 4) % 4 ))
  while test "$padding" -gt 0; do payload+='='; padding=$((padding - 1)); done
  printf '%s' "$payload" | openssl base64 -d -A 2>/dev/null
}

ensure_id_token() {
  if test -n "${OBSERVABILITY_PRIVATE_HTTP:-}"; then return; fi
  local claims exp aud refreshed
  claims="$(decode_token)" || fail 'The private ID token is malformed.'
  exp="$(jq -er '.exp | numbers' <<< "$claims")" || fail 'The private ID token has no expiry.'
  aud="$(jq -er '.aud | strings' <<< "$claims")" || fail 'The private ID token has no audience.'
  test "$aud" = "$SMOKE_URL" || fail 'The private ID token audience is not the validation URL.'
  if test "$exp" -le "$(( $(now) + 120 ))"; then
    refreshed="$(gcloud auth print-identity-token --audiences="$SMOKE_URL" 2>/dev/null)" \
      || fail 'The private ID token expired and could not be refreshed.'
    SMOKE_ID_TOKEN="$refreshed"
    export SMOKE_ID_TOKEN
    claims="$(decode_token)" || fail 'The refreshed private ID token is malformed.'
    jq -e --arg aud "$SMOKE_URL" --argjson after "$(( $(now) + 120 ))" \
      '.aud == $aud and .exp > $after' <<< "$claims" >/dev/null \
      || fail 'The refreshed private ID token is unusable.'
  fi
}

guard_target() {
  local expected_revision journal
  ensure_id_token
  gcloud_read "$evidence/project.json" projects describe "$PROJECT_ID" --format=json
  GCP_PROJECT_NUMBER="$(jq -er --arg project "$PROJECT_ID" '
    select(.projectId == $project) | .projectNumber | tostring | select(test("^[0-9]+$"))
  ' "$evidence/project.json")" || fail 'Live project inventory lacks an exact numeric project number.'
  MONITORING_SERVICE_AGENT="service-$GCP_PROJECT_NUMBER@gcp-sa-monitoring-notification.iam.gserviceaccount.com"
  export GCP_PROJECT_NUMBER MONITORING_SERVICE_AGENT
  gcloud_read "$evidence/project-iam.json" projects get-iam-policy "$PROJECT_ID" --format=json
  jq -e --arg member "serviceAccount:$MONITORING_SERVICE_AGENT" '
    [.bindings[]? | select(.role == "roles/monitoring.notificationServiceAgent" and
      (has("condition") | not) and any(.members[]?; . == $member))] | length == 1
  ' "$evidence/project-iam.json" >/dev/null \
    || fail 'Monitoring notification service-agent role proof is missing or ambiguous.'

  journal="$("$service_helper" read)" || fail 'The validation journal is unavailable.'
  expected_revision="projects/$PROJECT_ID/locations/$REGION/services/$SERVICE_NAME/revisions/$OBSERVABILITY_REVISION"
  jq -e --arg run "$OBSERVABILITY_RUN" --arg revision "$expected_revision" '
    .run == $run and .phase == "fault" and
    any(.resources[]?; .kind == "revision" and .name == $revision and .owned == true)
  ' <<< "$journal" >/dev/null || fail 'The validation journal does not own the serving revision.'

  gcloud_read "$evidence/service.json" run services describe "$SERVICE_NAME" \
    --project "$PROJECT_ID" --region "$REGION" --format=json
  jq -e --arg url "$SMOKE_URL" --arg revision "$OBSERVABILITY_REVISION" '
    .status.url == $url and
    ([.status.traffic[]? | select((.percent // 0) > 0)] | length == 1 and
      .[0].percent == 100 and .[0].revisionName == $revision) and
    ((.metadata.annotations["run.googleapis.com/invoker-iam-disabled"] // "false") != "true")
  ' "$evidence/service.json" >/dev/null || fail 'The fixed validation URL or serving revision changed.'
  gcloud_read "$evidence/service-iam.json" run services get-iam-policy "$SERVICE_NAME" \
    --project "$PROJECT_ID" --region "$REGION" --format=json
  jq -e '[.bindings[]? | select(.role == "roles/run.invoker") | .members[]? |
    select(. == "allUsers" or . == "allAuthenticatedUsers")] | length == 0' \
    "$evidence/service-iam.json" >/dev/null || fail 'The validation service is broadly invokable.'
}

private_request() {
  local method="$1" path="$2" expected="$3" trace="${4:-}" status output
  [[ "$path" == /health || "$path" == /__observability/* ]] || fail 'Private request path is not allowlisted.'
  case "$path" in
    /__observability/health/restore) guard_target ;;
    /__observability/*) require_fault_time; guard_target ;;
    /health) ensure_id_token ;;
  esac
  output="$evidence/private-response"
  if test -n "${OBSERVABILITY_PRIVATE_HTTP:-}"; then
    status="$("$OBSERVABILITY_PRIVATE_HTTP" "$method" "$SMOKE_URL" "$path" "$output" "$trace")"
  else
    local -a headers=(--header @-)
    test -z "$trace" || headers+=(--header "X-Cloud-Trace-Context: $trace/1;o=1")
    status="$(curl -q --silent --proto '=https' --connect-timeout 5 --max-time 25 --max-filesize 2097152 \
      --output "$output" --write-out '%{http_code}' --request "$method" "${headers[@]}" "$SMOKE_URL$path" \
      <<< "X-Serverless-Authorization: Bearer $SMOKE_ID_TOKEN")" || fail 'A private validation request failed.'
  fi
  chmod 600 "$output"
  test "$status" = "$expected" || fail 'A private validation endpoint returned an unexpected status.'
}

# Read-only provider checks also run before creating a recovery journal or revision.
provider_preflight() {
  local channel channels service_filter version_filter
  require_env PROJECT_ID; require_env SERVICE_NAME; require_env OBSERVABILITY_REVISION
  require_env OBSERVABILITY_NOTIFICATION_CHANNELS_JSON
  [[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,61}[a-z0-9]$ ]] || fail 'Invalid project identifier.'
  test "$SERVICE_NAME" = vlrgg-query-check || fail 'Provider checks require the private validation service.'
  [[ "$OBSERVABILITY_REVISION" =~ ^vlrgg-query-check-o[0-9]+-[0-9]+$ ]] || fail 'Invalid run-owned revision.'
  api GET "$monitoring_root/projects/$PROJECT_ID/alerts?pageSize=1" "$evidence/alerts-preflight.json"
  jq -e '(.alerts // []) | type == "array"' "$evidence/alerts-preflight.json" >/dev/null \
    || fail 'Monitoring alerts API preflight failed.'
  service_filter="$(jq -rn --arg value "$SERVICE_NAME" '$value|@uri')"
  version_filter="$(jq -rn --arg value "$OBSERVABILITY_REVISION" '$value|@uri')"
  api GET "$error_root/projects/$PROJECT_ID/groupStats?serviceFilter.service=$service_filter&serviceFilter.version=$version_filter&timeRange.period=PERIOD_1_HOUR&pageSize=1" \
    "$evidence/error-reporting-preflight.json"
  jq -e '((.errorGroupStats // []) | type) == "array" and
    ((.nextPageToken // "") | type) == "string" and
    (.timeRangeBegin | type) == "string"' "$evidence/error-reporting-preflight.json" >/dev/null \
    || fail 'Error Reporting API preflight failed.'
  channels="$(jq -cer --arg prefix "projects/$PROJECT_ID/notificationChannels/" '
    select(type == "array" and length > 0 and length <= 5 and
      all(.[]; type == "string" and startswith($prefix) and length <= 256)) | unique
  ' <<< "$OBSERVABILITY_NOTIFICATION_CHANNELS_JSON")" || fail 'Invalid protected notification channel list.'
  while IFS= read -r channel; do
    api GET "$monitoring_root/$channel" "$evidence/channel.json"
    jq -e --arg name "$channel" '.name == $name and .enabled == true' "$evidence/channel.json" >/dev/null \
      || fail 'The protected notification channel is not enabled.'
  done < <(jq -r '.[]' <<< "$channels")
}

preflight() {
  require_env PROJECT_ID; require_env REGION; require_env SERVICE_NAME; require_env VALIDATION_SERVICE
  require_env OBSERVABILITY_RUN; require_env OBSERVABILITY_REVISION; require_env OBSERVABILITY_DEADLINE_EPOCH
  require_env SMOKE_URL; require_env SMOKE_ID_TOKEN; require_env RUNNER_TEMP; require_env GITHUB_STEP_SUMMARY
  require_env OBSERVABILITY_NOTIFICATION_CHANNELS_JSON; require_env OBSERVABILITY_CONFIRMED_RECEIVERS
  require_env OBSERVABILITY_ERROR_REPORTING_ENROLLED
  test "$SERVICE_NAME" = vlrgg-query-check && test "$VALIDATION_SERVICE" = "$SERVICE_NAME" \
    || fail 'Live validation is limited to the fixed validation service.'
  [[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,61}[a-z0-9]$ ]] || fail 'Invalid project identifier.'
  [[ "$REGION" =~ ^[a-z0-9-]+$ ]] || fail 'Invalid region.'
  [[ "$SMOKE_URL" =~ ^https://[a-z0-9.-]+\.run\.app$ ]] || fail 'Invalid private validation URL.'
  [[ "$OBSERVABILITY_REVISION" =~ ^vlrgg-query-check-o[0-9]+-[0-9]+$ ]] || fail 'Invalid run-owned revision.'
  [[ "$OBSERVABILITY_RUN" =~ ^[0-9]+-[0-9]+$ ]] || fail 'Invalid observability run identifier.'
  [[ "$OBSERVABILITY_DEADLINE_EPOCH" =~ ^[0-9]{10}$ ]] || fail 'Invalid validation deadline.'
  [[ "$poll_seconds" =~ ^[0-9]+$ ]] && test "$poll_seconds" -ge 1 && test "$poll_seconds" -le 30 \
    || fail 'Polling interval must be between 1 and 30 seconds.'
  test "$OBSERVABILITY_CONFIRMED_RECEIVERS" = true \
    && test "$OBSERVABILITY_ERROR_REPORTING_ENROLLED" = true \
    || fail 'Protected receiver and Error Reporting enrollment proofs are required.'
  require_fault_time
  guard_target

  provider_preflight
  private_request GET /health 200
  jq -e '.status == "ok"' "$evidence/private-response" >/dev/null || fail 'Private health body mismatch.'
  result preflight PASS
}

log_entries() {
  local start="$1" output="$2" filter body
  filter="resource.type=\"cloud_run_revision\" AND resource.labels.project_id=\"$PROJECT_ID\" AND resource.labels.location=\"$REGION\" AND resource.labels.service_name=\"$SERVICE_NAME\" AND resource.labels.revision_name=\"$OBSERVABILITY_REVISION\" AND timestamp>=\"$start\""
  body="$evidence/log-query.json"
  jq -n --arg project "projects/$PROJECT_ID" --arg filter "$filter" \
    '{resourceNames:[$project],filter:$filter,orderBy:"timestamp asc",pageSize:1000}' > "$body"
  api POST "$logging_root/entries:list" "$output" "$body"
  jq -e '(.nextPageToken // "") == "" and ((.entries // []) | length <= 1000)' "$output" >/dev/null \
    || fail 'Bounded Logging inventory was incomplete.'
}

run_o3_o6() {
  local start trace groups group group_id events internal_group='' internal_groups=0 parsing_groups=0
  local group_body service_filter version_filter recurrence_epoch attempts
  start="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  trace="$(openssl rand -hex 16)"
  private_request GET /__observability/internal 500 "$trace"
  private_request GET /__observability/internal 500 invalid-trace
  private_request GET /__observability/internal 500
  private_request GET /__observability/internal 500
  private_request GET /__observability/internal 500
  private_request GET /__observability/parsing 502
  private_request GET /__observability/upstream 502
  private_request GET /__observability/expected 400
  poll_budget fault 61
  private_request GET /__observability/expected 400
  attempts=12
  while test "$attempts" -gt 0; do
    poll_budget fault
    log_entries "$start" "$evidence/application-logs.json"
    if jq -e --arg service "$SERVICE_NAME" --arg revision "$OBSERVABILITY_REVISION" \
    --arg trace "projects/$PROJECT_ID/traces/$trace" '
    [.entries[]? | select(.jsonPayload.serviceContext.service == $service and
      .jsonPayload.serviceContext.version == $revision and .severity == "ERROR" and
      (.jsonPayload.category | IN("INTERNAL","SOURCE_PARSING")))] as $errors |
    [$errors[] | select(.jsonPayload.category == "INTERNAL")] as $internal |
    ([$errors[] | .jsonPayload.category] | unique | length) == 2 and
    ($internal | length) == 4 and
    all($internal[];
      .jsonPayload.error_code == "INTERNAL_ERROR" and .jsonPayload.http_status == 500 and
      .jsonPayload.canonical_upstream == "none" and
      .jsonPayload["@type"] == "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent" and
      (.jsonPayload.message | contains("ValidationInternalFailure: INTERNAL_ERROR")) and
      (.jsonPayload.message | contains("ObservabilityValidationMain.kt")) and
      (.jsonPayload.truncation | type) == "object" and
      ([.jsonPayload.truncation | keys[]] == ["accessor_failure","bytes","candidates","causes","cycle","frames"]) and
      ([.jsonPayload.truncation[] | type] | all(. == "boolean")) and
      (.jsonPayload | has("logging.googleapis.com/trace") | not)) and
    $internal[0].trace == $trace and ($internal[1] | has("trace") | not) and
    ([$internal[] | select(.trace == $trace)] | length) == 1 and
    any(.entries[]?; .trace == $trace and
      ((.logName // "") | endswith("/logs/run.googleapis.com%2Frequests")) and .httpRequest.status == 500) and
    ([.entries[]? | select(.httpRequest.status == 500 and
      ((.httpRequest.requestUrl // "") | endswith("/__observability/internal")))] | length) == 5 and
    any($errors[]; .jsonPayload.category == "SOURCE_PARSING" and
      .jsonPayload.error_code == "SOURCE_PARSING_FAILURE" and .jsonPayload.http_status == 502 and
      .jsonPayload.canonical_upstream == "https://www.vlr.gg/" and
      (.jsonPayload.message | contains("ValidationParsingFailure: SOURCE_PARSING_FAILURE"))) and
    any(.entries[]?; .severity == "WARNING" and .jsonPayload.category == "UPSTREAM_NETWORK" and
      .jsonPayload.error_code == "UPSTREAM_NETWORK_FAILURE" and .jsonPayload.http_status == 502) and
    any(.entries[]?; .severity == "WARNING" and .jsonPayload.category == "EXPECTED" and
      .jsonPayload.error_code == "INVALID_REQUEST" and .jsonPayload.http_status == 400)
  ' "$evidence/application-logs.json" >/dev/null &&
  jq -e '
    any(.entries[]?;
      ((.textPayload // .jsonPayload.message // "") as $message |
        ($message | contains("public_api_summary")) and
        ($message | contains("requests=9")) and
        ($message | contains("diagnostics_emitted={EXPECTED=2,UPSTREAM_NETWORK=1,INTERNAL=4,SOURCE_PARSING=1}")) and
        ($message | contains("diagnostics_suppressed={EXPECTED=0,UPSTREAM_NETWORK=0,INTERNAL=1,SOURCE_PARSING=0}"))))
  ' "$evidence/application-logs.json" >/dev/null; then break; fi
    attempts=$((attempts - 1))
    test "$attempts" -gt 0 || fail 'Structured logs, trace, or sampling summary did not arrive.'
    poll_budget fault "$poll_seconds"
  done
  ! grep -Eq 'OBSERVABILITY_RAW_SECRET_SENTINEL|invalid-trace' "$evidence/application-logs.json" \
    || fail 'Protected or unpromoted request data appeared in structured logs.'

  service_filter="$(jq -rn --arg value "$SERVICE_NAME" '$value|@uri')"
  version_filter="$(jq -rn --arg value "$OBSERVABILITY_REVISION" '$value|@uri')"
  attempts=12
  while test "$attempts" -gt 0; do
    poll_budget fault
  api GET "$error_root/projects/$PROJECT_ID/groupStats?serviceFilter.service=$service_filter&serviceFilter.version=$version_filter&timeRange.period=PERIOD_1_HOUR&pageSize=100" \
    "$evidence/error-groups.json"
  jq -e '(.nextPageToken // "") == "" and ((.errorGroupStats // []) | length <= 100)' \
    "$evidence/error-groups.json" >/dev/null || fail 'Error Reporting group inventory was incomplete.'
  groups="$(jq -c --arg service "$SERVICE_NAME" --arg revision "$OBSERVABILITY_REVISION" '
    [.errorGroupStats[]? | select((.numAffectedServices | tonumber?) == 1 and
      (.affectedServices | length) == 1 and
      .affectedServices[0].service == $service and .affectedServices[0].version == $revision)]
  ' "$evidence/error-groups.json")"
  test "$(jq 'length' <<< "$groups")" = 2 && break
  attempts=$((attempts - 1))
  test "$attempts" -gt 0 || fail 'The validation run did not produce exactly two Error Reporting groups.'
  poll_budget fault "$poll_seconds"
  done
  while IFS= read -r group; do
    [[ "$group" =~ ^projects/$PROJECT_ID/(locations/[a-z0-9-]+/)?groups/[A-Za-z0-9_-]+$ ]] \
      || fail 'Unexpected Error Reporting group resource.'
    group_id="${group##*/}"
    attempts=12
    while test "$attempts" -gt 0; do
      poll_budget fault
      api GET "$error_root/projects/$PROJECT_ID/events?groupId=$group_id&serviceFilter.service=$service_filter&serviceFilter.version=$version_filter&timeRange.period=PERIOD_1_HOUR&pageSize=100" \
      "$evidence/error-events-$group_id.json"
    events="$evidence/error-events-$group_id.json"
    jq -e --arg service "$SERVICE_NAME" --arg revision "$OBSERVABILITY_REVISION" '
      (.nextPageToken // "") == "" and ((.errorEvents // []) | length <= 100) and
      all(.errorEvents[]?; .serviceContext.service == $service and
        .serviceContext.version == $revision and (.eventTime | type) == "string")
    ' "$events" >/dev/null || fail 'Run-owned Error Reporting event inventory was invalid.'
      test "$(jq '.errorEvents // [] | length' "$events")" -gt 0 && break
      attempts=$((attempts - 1))
      test "$attempts" -gt 0 || fail 'Error Reporting samples did not arrive.'
      poll_budget fault "$poll_seconds"
    done
    if jq -e 'any(.errorEvents[]?; (.message // "") | contains("ValidationInternalFailure: INTERNAL_ERROR"))' \
      "$events" >/dev/null; then internal_group="$group"; internal_groups=$((internal_groups + 1)); fi
    if jq -e 'any(.errorEvents[]?; (.message // "") | contains("ValidationParsingFailure: SOURCE_PARSING_FAILURE"))' \
      "$events" >/dev/null; then parsing_groups=$((parsing_groups + 1)); fi
  done < <(jq -r '.[].group.name' <<< "$groups")
  test "$internal_groups" = 1 && test "$parsing_groups" = 1 \
    || fail 'The two run-owned Error Reporting groups were not uniquely identified by sampled events.'
  poll_native_failures "$start" 7
  result O3 PASS
  result O4 PASS
  result O5 PASS
  api GET "$error_root/$internal_group" "$evidence/error-group.json"
  jq -e --arg name "$internal_group" '.name == $name' "$evidence/error-group.json" >/dev/null \
    || fail 'Error Reporting group read-back changed identity.'
  group_body="$evidence/error-group-resolve.json"
  jq '.resolutionStatus="RESOLVED"' "$evidence/error-group.json" > "$group_body"
  require_fault_time
  guard_target
  api PUT "$error_root/$internal_group" "$evidence/error-group-resolved.json" "$group_body"
  attempts=10
  while test "$attempts" -gt 0; do
    poll_budget fault
    api GET "$error_root/$internal_group" "$evidence/error-group-status.json"
    jq -e --arg name "$internal_group" '.name == $name and .resolutionStatus == "RESOLVED"' \
      "$evidence/error-group-status.json" >/dev/null && break
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || fail 'Error Reporting group did not resolve.'
    poll_budget fault "$poll_seconds"
  done
  poll_budget fault 301
  recurrence_epoch="$(now)"
  private_request GET /__observability/internal 500
  attempts=20
  while test "$attempts" -gt 0; do
    poll_budget fault
    api GET "$error_root/$internal_group" "$evidence/error-group-reopened.json"
    poll_budget fault
    api GET "$error_root/projects/$PROJECT_ID/events?groupId=${internal_group##*/}&serviceFilter.service=$service_filter&serviceFilter.version=$version_filter&timeRange.period=PERIOD_1_HOUR&pageSize=100" \
      "$evidence/error-events-recurrence.json"
    if jq -e --arg name "$internal_group" '.name == $name and .resolutionStatus == "OPEN"' \
      "$evidence/error-group-reopened.json" >/dev/null &&
      jq -e --argjson after "$recurrence_epoch" '
        (.nextPageToken // "") == "" and ((.errorEvents // []) | length <= 100) and
        any(.errorEvents[]?; (.eventTime | sub("[.][0-9]+Z$";"Z") | fromdateiso8601) >= $after)
      ' \
        "$evidence/error-events-recurrence.json" >/dev/null; then break; fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || fail 'Error Reporting group did not reopen with a fresh event.'
    poll_budget fault "$poll_seconds"
  done
  result O6 PASS
  result O6-receipt 'RECEIPT PENDING'
}

alerts_for_policy() {
  local policy="$1" output="$2"
  api GET "$monitoring_root/projects/$PROJECT_ID/alerts?pageSize=1000" "$output"
  jq -e '(.nextPageToken // "") == "" and ((.alerts // []) | length <= 1000)' "$output" >/dev/null \
    || fail 'Bounded alert inventory was incomplete.'
  jq -c --arg policy "$policy" '[.alerts[]? | select(.policy.name == $policy)]' "$output"
}

verify_single_condition() {
  local policy="$1" output="$evidence/policy.json"
  api GET "$monitoring_root/$policy" "$output"
  jq -e --arg name "$policy" '.name == $name and (.conditions | length) == 1 and
    (.conditions[0].name | type) == "string" and (.conditions[0].name | length) > 0 and
    ((.validity.code // 0) == 0)' "$output" >/dev/null \
    || fail 'The run-owned policy does not have one valid exact condition.'
}

poll_native_failures() {
  local start="$1" expected="$2" attempts=12 filter encoded count output="$evidence/native-failures.json"
  filter="metric.type=\"run.googleapis.com/request_count\" AND resource.type=\"cloud_run_revision\" AND resource.labels.project_id=\"$PROJECT_ID\" AND resource.labels.location=\"$REGION\" AND resource.labels.service_name=\"$SERVICE_NAME\" AND resource.labels.revision_name=\"$OBSERVABILITY_REVISION\" AND metric.labels.response_code_class=\"5xx\""
  encoded="$(jq -rn --arg value "$filter" '$value|@uri')"
  while test "$attempts" -gt 0; do
    poll_budget fault
    api GET "$monitoring_root/projects/$PROJECT_ID/timeSeries?filter=$encoded&interval.startTime=$start&interval.endTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)&view=FULL&pageSize=1000" "$output"
    jq -e '(.nextPageToken // "") == ""' "$output" >/dev/null || fail 'Native count inventory was incomplete.'
    count="$(jq -er '[.timeSeries[]?.points[]?.value.int64Value | tonumber] |
      select(length > 0 and all(.[]; isfinite and . >= 0)) | add' "$output" 2>/dev/null || true)"
    if test -n "$count"; then
      test "$count" -le "$expected" || fail 'Native failures exceeded the exact request ledger.'
      test "$count" = "$expected" && return
    fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget fault "$poll_seconds"
  done
  fail 'Native failures did not reconcile with the exact request ledger.'
}

poll_count() {
  local kind="$1" comparison="$2" threshold="$3" attempts="${4:-12}" value
  while test "$attempts" -gt 0; do
    poll_budget fault
    value="$("$policy_helper" "query-$kind-count" "$(now)" 2>/dev/null || true)"
    if test -n "$value" && awk -v value="$value" -v threshold="$threshold" "BEGIN { exit ! (value $comparison threshold) }"; then
      printf '%s\n' "$value"
      return
    fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget fault "$poll_seconds"
  done
  fail 'Native request-count transition did not arrive in the bounded poll.'
}

poll_alert_open() {
  local policy="$1" after="$2" kind="$3" attempts="${4:-20}" matches name
  while test "$attempts" -gt 0; do
    poll_budget fault
    matches="$(alerts_for_policy "$policy" "$evidence/alerts.json")"
    name="$(jq -er --argjson after "$after" --arg kind "$kind" \
      --arg run "${OBSERVABILITY_RUN//-/_}" '[.[] | select(.state == "OPEN" and
      (.openTime | type) == "string" and
      ((.openTime | sub("[.][0-9]+Z$";"Z") | fromdateiso8601) >= $after) and
      .policy.userLabels.managed_by == "issue122-validation" and
      .policy.userLabels.validation_run == $run and
      .policy.userLabels.resource_kind == (if $kind == "uptime" then "uptime_policy" else $kind end))] |
      select(length == 1) | .[0].name |
      select(test("^projects/[^/]+/alerts/[^/]+$"))' <<< "$matches" 2>/dev/null || true)"
    if test -n "$name"; then printf '%s\n' "$name"; return; fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget fault "$poll_seconds"
  done
  fail 'The exact run-owned policy did not open one fresh alert in the bounded poll.'
}

poll_alert_closed() {
  local policy="$1" alert="$2" after="$3" attempts="${4:-30}" matches
  while test "$attempts" -gt 0; do
    poll_budget recovery
    matches="$(alerts_for_policy "$policy" "$evidence/alerts.json")"
    if jq -e --arg alert "$alert" --argjson after "$after" '[.[] | select(
      .name == $alert and .state == "CLOSED" and (.closeTime | type) == "string" and
      ((.closeTime | sub("[.][0-9]+Z$";"Z") | fromdateiso8601) >= $after))] | length == 1' \
      <<< "$matches" >/dev/null; then return; fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget recovery "$poll_seconds"
  done
  fail 'The same provider alert did not close after recovery evidence in the bounded poll.'
}

ensure_policy() {
  require_fault_time
  guard_target
  "$policy_helper" ensure "$1"
}

disable_policy() {
  guard_target
  "$policy_helper" disable "$1"
}

poll_recovery() {
  local attempts="${1:-12}" at healthy five_x
  while test "$attempts" -gt 0; do
    poll_budget recovery
    at="$(now)"
    healthy="$("$policy_helper" query-2xx-sample "$at" 2>/dev/null || true)"
    poll_budget recovery
    five_x="$("$policy_helper" query-5xx-sample "$at" 2>/dev/null || true)"
    if jq -en --argjson healthy "${healthy:-null}" --argjson five_x "${five_x:-null}" '
      ($healthy | type) == "array" and ($five_x | type) == "array" and
      $healthy[0] == $five_x[0] and ($healthy[1] | tonumber) > 0 and ($five_x[1] | tonumber) == 0
    ' >/dev/null 2>&1; then
      printf '%s\n' "$five_x"
      return
    fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget recovery "$poll_seconds"
  done
  fail 'Fresh healthy traffic plus numeric zero did not arrive in the bounded poll.'
}

rfc3339_ago() {
  local minutes="$1"
  date -u -v-"${minutes}"M +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
    || date -u -d "$minutes minutes ago" +%Y-%m-%dT%H:%M:%SZ
}

uptime_locations() {
  local check_id="$1" wanted="$2" after="$3" output="$evidence/uptime-series.json" filter encoded
  filter="metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.labels.check_id=\"$check_id\" AND resource.labels.project_id=\"$PROJECT_ID\""
  encoded="$(jq -rn --arg value "$filter" '$value|@uri')"
  api GET "$monitoring_root/projects/$PROJECT_ID/timeSeries?filter=$encoded&interval.endTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)&interval.startTime=$(rfc3339_ago 15)&view=FULL&pageSize=1000" "$output"
  jq -e '(.nextPageToken // "") == "" and ((.timeSeries // []) | length <= 1000)' "$output" >/dev/null \
    || fail 'Bounded uptime metric inventory was incomplete.'
  jq --arg project "$PROJECT_ID" --arg region "$REGION" --arg service "$SERVICE_NAME" \
    --arg revision "$OBSERVABILITY_REVISION" \
    --arg check "$check_id" --argjson wanted "$wanted" --argjson after "$after" '
    [.timeSeries[]? | select(
      .metric.labels.check_id == $check and (.metric.labels.checker_location | type) == "string" and
      .resource.labels.project_id == $project and .resource.labels.location == $region and
      .resource.labels.service_name == $service and .resource.labels.revision_name == $revision and
      .points[0].value.boolValue == $wanted and
      ((.points[0].interval.endTime | sub("[.][0-9]+Z$";"Z") | fromdateiso8601) >= $after))] as $series |
    {count:([$series[].metric.labels.checker_location] | unique | length),
     evidenceEpoch:([$series[].points[] | select(.value.boolValue == $wanted) | .interval.endTime |
       sub("[.][0-9]+Z$";"Z") | fromdateiso8601 | select(. >= $after)] | min // 0)}
  ' "$output"
}

poll_uptime_locations() {
  local check_id="$1" wanted="$2" minimum="$3" after="$4" attempts="${5:-30}" phase="${6:-fault}" observation
  while test "$attempts" -gt 0; do
    poll_budget "$phase"
    observation="$(uptime_locations "$check_id" "$wanted" "$after")"
    if test "$(jq -r '.count' <<< "$observation")" -ge "$minimum"; then printf '%s\n' "$observation"; return; fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget "$phase" "$poll_seconds"
  done
  fail 'Uptime checker transition did not arrive in the bounded poll.'
}

uptime_http_locations() {
  local check_id="$1" after="$2" output="$evidence/uptime-http-series.json" filter encoded
  filter="metric.type=\"monitoring.googleapis.com/uptime_check/http_status\" AND metric.labels.check_id=\"$check_id\" AND resource.labels.project_id=\"$PROJECT_ID\""
  encoded="$(jq -rn --arg value "$filter" '$value|@uri')"
  api GET "$monitoring_root/projects/$PROJECT_ID/timeSeries?filter=$encoded&interval.endTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)&interval.startTime=$(rfc3339_ago 15)&view=FULL&pageSize=1000" "$output"
  jq -e '(.nextPageToken // "") == "" and ((.timeSeries // []) | length <= 1000)' "$output" >/dev/null \
    || fail 'Bounded uptime HTTP-status inventory was incomplete.'
  jq --arg project "$PROJECT_ID" --arg region "$REGION" --arg service "$SERVICE_NAME" \
    --arg revision "$OBSERVABILITY_REVISION" \
    --arg check "$check_id" --argjson after "$after" '
    [.timeSeries[]? | select(
      .metric.labels.check_id == $check and (.metric.labels.checker_location | type) == "string" and
      .resource.labels.project_id == $project and .resource.labels.location == $region and
      .resource.labels.service_name == $service and .resource.labels.revision_name == $revision and
      (.points[0].value.stringValue // "") == "200" and
      ((.points[0].interval.endTime | sub("[.][0-9]+Z$";"Z") | fromdateiso8601) >= $after))] as $series |
    {count:([$series[].metric.labels.checker_location] | unique | length),
     evidenceEpoch:([$series[].points[] | select(.value.stringValue == "200") | .interval.endTime |
       sub("[.][0-9]+Z$";"Z") | fromdateiso8601 | select(. >= $after)] | min // 0)}
  ' "$output"
}

poll_uptime_http() {
  local check_id="$1" after="$2" attempts="${3:-30}" phase="${4:-fault}" observation
  while test "$attempts" -gt 0; do
    poll_budget "$phase"
    observation="$(uptime_http_locations "$check_id" "$after")"
    if test "$(jq -r '.count' <<< "$observation")" -ge 3; then printf '%s\n' "$observation"; return; fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    poll_budget "$phase" "$poll_seconds"
  done
  fail 'Fresh uptime HTTP-200 evidence did not arrive in the bounded poll.'
}

verify_uptime_check() {
  local check="$1" output="$evidence/uptime-check.json"
  api GET "$monitoring_root/$check" "$output"
  jq -e --arg name "$check" --arg project "$PROJECT_ID" --arg region "$REGION" \
    --arg service "$SERVICE_NAME" --arg revision "$OBSERVABILITY_REVISION" '
    .name == $name and .monitoredResource.type == "cloud_run_revision" and
    .monitoredResource.labels.project_id == $project and .monitoredResource.labels.location == $region and
    .monitoredResource.labels.service_name == $service and .monitoredResource.labels.revision_name == $revision and
    .httpCheck.path == "/health" and .httpCheck.serviceAgentAuthentication.type == "OIDC_TOKEN" and
    .contentMatchers == [{"content":"^\\s*\\{\\s*\"status\"\\s*:\\s*\"ok\"\\s*\\}\\s*$","matcher":"MATCHES_REGEX"}]
  ' "$output" >/dev/null || fail 'Run-owned uptime check read-back failed.'
}

run_o7() {
  local policy count alerts fault_started fault_start_time alert recovery_sample recovery_epoch
  private_request GET /health 200
  poll_count 2xx '>' 0 >/dev/null
  poll_count 5xx '==' 0 >/dev/null
  policy="$(ensure_policy 5xx)"
  verify_single_condition "$policy"
  fault_started="$(now)"
  fault_start_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  private_request GET /__observability/internal 500
  private_request GET /__observability/internal 500
  poll_native_failures "$fault_start_time" 2
  count="$(poll_count 5xx '>' 0)"
  awk -v value="$count" 'BEGIN { exit !(value < 3) }' \
    || fail 'The native pre-threshold signal was not below the alert threshold.'
  alerts="$(alerts_for_policy "$policy" "$evidence/alerts-pre-threshold.json")"
  test "$(jq '[.[] | select(.state == "OPEN")] | length' <<< "$alerts")" = 0 \
    || fail 'The 5xx alert opened below threshold.'
  private_request GET /__observability/internal 500
  poll_native_failures "$fault_start_time" 3
  poll_count 5xx '>=' 3 >/dev/null
  alert="$(poll_alert_open "$policy" "$fault_started" 5xx)"
  recovery_epoch="$(now)"
  for _ in $(seq 1 12); do
    poll_budget recovery
    private_request GET /health 200
    recovery_sample="$(poll_recovery 1 2>/dev/null || true)"
    test -z "$recovery_sample" || break
    poll_budget recovery "$poll_seconds"
  done
  test -n "${recovery_sample:-}" || fail 'Native recovery remained absent after bounded healthy traffic.'
  poll_alert_closed "$policy" "$alert" "$recovery_epoch"
  disable_policy "$policy"
  result O7 PASS
  result O7-receipt 'RECEIPT PENDING'
}

run_o8() {
  local check policy check_id check_started fault_started alert recovered_at passed http recovery_epoch fault_ok=true
  check_started="$(now)"
  check="$(ensure_policy uptime)"
  check_id="${check##*/}"
  [[ "$check_id" =~ ^[A-Za-z0-9_-]+$ ]] || fail 'Unexpected uptime check resource.'
  export UPTIME_CHECK_ID="$check_id"
  verify_uptime_check "$check"
  policy="$(ensure_policy uptime-policy)"
  verify_single_condition "$policy"
  poll_uptime_locations "$check_id" true 3 "$check_started" >/dev/null
  poll_uptime_http "$check_id" "$check_started" >/dev/null
  fault_started="$(now)"
  private_request POST /__observability/health/fail 200
  jq -e '.status == "configured"' "$evidence/private-response" >/dev/null \
    || fail 'Health fault acknowledgement was malformed.'
  if ! alert="$(poll_uptime_locations "$check_id" false 2 "$fault_started" 40 >/dev/null &&
    poll_alert_open "$policy" "$fault_started" uptime 20)"; then fault_ok=false; fi
  recovered_at="$(now)"
  private_request POST /__observability/health/restore 200
  jq -e '.status == "configured"' "$evidence/private-response" >/dev/null \
    || fail 'Health restore acknowledgement was malformed.'
  poll_health
  passed="$(poll_uptime_locations "$check_id" true 3 "$recovered_at" 30 recovery)"
  http="$(poll_uptime_http "$check_id" "$recovered_at" 30 recovery)"
  recovery_epoch="$(jq -en --argjson passed "$passed" --argjson http "$http" \
    '[$passed.evidenceEpoch,$http.evidenceEpoch] | min')"
  test "$fault_ok" = true || fail 'Uptime fault evidence was incomplete after explicit recovery.'
  poll_alert_closed "$policy" "$alert" "$recovery_epoch" 30
  disable_policy "$policy"
  result O8 PASS
  result O8-receipt 'RECEIPT PENDING'
}

system_logs() {
  local start="$1" output="$2" filter body="$evidence/system-log-query.json"
  filter="resource.type=\"cloud_run_revision\" AND resource.labels.project_id=\"$PROJECT_ID\" AND resource.labels.location=\"$REGION\" AND resource.labels.service_name=\"$SERVICE_NAME\" AND logName=\"projects/$PROJECT_ID/logs/run.googleapis.com%2Fvarlog%2Fsystem\" AND timestamp>=\"$start\""
  jq -n --arg project "projects/$PROJECT_ID" --arg filter "$filter" \
    '{resourceNames:[$project],filter:$filter,orderBy:"timestamp asc",pageSize:1000}' > "$body"
  api POST "$logging_root/entries:list" "$output" "$body"
  jq -e '(.nextPageToken // "") == "" and ((.entries // []) | length <= 1000)' "$output" >/dev/null \
    || fail 'Bounded system-log inventory was incomplete.'
}

private_exit() {
  local status output="$evidence/private-response"
  require_fault_time
  guard_target
  if test -n "${OBSERVABILITY_PRIVATE_HTTP:-}"; then
    status="$("$OBSERVABILITY_PRIVATE_HTTP" POST "$SMOKE_URL" /__observability/exit "$output" '')" || status=000
  else
    status="$(curl -q --silent --proto '=https' --connect-timeout 5 --max-time 25 --max-filesize 2097152 \
      --output "$output" --write-out '%{http_code}' --request POST --header @- "$SMOKE_URL/__observability/exit" \
      <<< "X-Serverless-Authorization: Bearer $SMOKE_ID_TOKEN")" || status=000
  fi
  chmod 600 "$output"
  test "$status" = 202 \
    || fail 'The private abnormal-exit request returned an unexpected status.'
}

poll_health() {
  local attempts="${1:-20}" status output="$evidence/private-response"
  while test "$attempts" -gt 0; do
    ensure_id_token
    if test -n "${OBSERVABILITY_PRIVATE_HTTP:-}"; then
      status="$("$OBSERVABILITY_PRIVATE_HTTP" GET "$SMOKE_URL" /health "$output" '' 2>/dev/null || true)"
    else
      status="$(curl -q --silent --proto '=https' --connect-timeout 5 --max-time 25 --max-filesize 2097152 \
        --output "$output" --write-out '%{http_code}' --header @- "$SMOKE_URL/health" \
        <<< "X-Serverless-Authorization: Bearer $SMOKE_ID_TOKEN" 2>/dev/null || true)"
    fi
    chmod 600 "$output"
    if test "$status" = 200 && jq -e '.status == "ok"' "$output" >/dev/null 2>&1; then
      guard_target
      return
    fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || break
    sleep_for "$poll_seconds"
  done
  fail 'The private validation revision did not recover in the bounded poll.'
}

run_o9() {
  local policy signature start fault_started
  start="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  system_logs "$(rfc3339_ago 43200)" "$evidence/system-history.json"
  private_exit
  poll_health
  local attempts=12 candidates
  while test "$attempts" -gt 0; do
    poll_budget fault
    system_logs "$start" "$evidence/system-discovery.json"
    candidates="$(jq -c --arg revision "$OBSERVABILITY_REVISION" '
      [.entries[]? | select(.resource.labels.revision_name == $revision) | .textPayload |
        select(type == "string" and test("(exit|terminated|status)[^0-9]*42([^0-9]|$)"; "i"))] | unique
    ' "$evidence/system-discovery.json")"
    test "$(jq length <<< "$candidates")" -le 1 || fail 'The actual abnormal-exit signature was ambiguous.'
    if test "$(jq length <<< "$candidates")" = 1; then signature="$(jq -r '.[0]' <<< "$candidates")"; break; fi
    attempts=$((attempts - 1)); test "$attempts" -gt 0 || fail 'The actual abnormal-exit signature did not arrive.'
    poll_budget fault "$poll_seconds"
  done
  test "${#signature}" -le 240 && [[ "$signature" =~ ^[A-Za-z0-9._:/\ \(\)-]+$ ]] \
    || fail 'The actual abnormal-exit signature is unsafe for a narrow log policy.'
  jq -e --arg signature "$signature" 'all(.entries[]? |
    select((.textPayload // "") | test("(starting|startup|SIGTERM|exit[^0-9]*0([^0-9]|$))";"i"));
    (.textPayload // "") != $signature)' \
    "$evidence/system-history.json" >/dev/null || fail 'The abnormal-exit signature collides with bounded normal history.'
  export SYSTEM_LOG_NAME="projects/$PROJECT_ID/logs/run.googleapis.com%2Fvarlog%2Fsystem"
  export SYSTEM_LOG_SIGNATURE="$signature"
  policy="$(ensure_policy log)"
  verify_single_condition "$policy"
  fault_started="$(now)"
  private_exit
  poll_alert_open "$policy" "$fault_started" log 20 >/dev/null
  poll_health
  result O9 PASS
  result O9-receipt 'RECEIPT PENDING'
  result OOM 'NOT RUN'
  result O9-exits '2 PRIVATE EXITS'
}

main() {
  umask 077
  require_env RUNNER_TEMP
  [[ "${OBSERVABILITY_RUN:-}" =~ ^[0-9]+-[0-9]+$ ]] || fail 'Invalid observability run identifier.'
  evidence="$RUNNER_TEMP/issue122-live-${OBSERVABILITY_RUN:-unknown}"
  mkdir -p "$evidence"
  chmod 700 "$evidence"
  require_env OBSERVABILITY_WORKFLOW_STARTED_AT
  set_validation_deadline "$OBSERVABILITY_WORKFLOW_STARTED_AT"
  preflight
  require_fault_window 3300
  run_o3_o6
  require_fault_window 2400
  run_o7
  require_fault_window 1200
  run_o8
  require_fault_window 300
  run_o9
  result restore 'PENDING ALWAYS STEP'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi

#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly service_helper="$script_dir/observability-service.sh"
readonly monitoring_root="${MONITORING_API_ROOT:-https://monitoring.googleapis.com/v3}"
readonly prometheus_root="${MONITORING_PROMETHEUS_API_ROOT:-https://monitoring.googleapis.com/v1}"
readonly owner='issue122-validation'

fail() { echo "Observability policy operation failed: $*" >&2; exit 1; }
require_env() { test -n "${!1:-}" || fail "Missing $1."; }
temporary_file() { mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/issue122-observability.XXXXXX"; }

gcloud_json() {
  local output error
  output="$(temporary_file)"; error="$(temporary_file)"
  if ! gcloud "$@" > "$output" 2> "$error"; then
    rm -f "$output" "$error"
    fail 'Cloud IAM inventory failed.'
  fi
  cat "$output"
  rm -f "$output" "$error"
}

gcloud_mutate() {
  local output error
  output="$(temporary_file)"; error="$(temporary_file)"
  if ! gcloud "$@" > "$output" 2> "$error"; then
    rm -f "$output" "$error"
    fail 'Cloud IAM mutation failed.'
  fi
  rm -f "$output" "$error"
}

require_context() {
  require_env PROJECT_ID
  require_env REGION
  require_env SERVICE_NAME
  require_env OBSERVABILITY_RUN
  [[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,61}[a-z0-9]$ ]] || fail 'Invalid PROJECT_ID.'
  [[ "$REGION" =~ ^[a-z0-9-]+$ ]] || fail 'Invalid REGION.'
  [[ "$SERVICE_NAME" =~ ^[a-z][a-z0-9-]{0,62}$ ]] || fail 'Invalid SERVICE_NAME.'
  [[ "$OBSERVABILITY_RUN" =~ ^[0-9]+-[0-9]+$ ]] || fail 'Invalid OBSERVABILITY_RUN.'
}

http() {
  local method="$1" url="$2" body_file="${3:-}"
  if test -n "${OBSERVABILITY_HTTP:-}"; then
    "$OBSERVABILITY_HTTP" "$method" "$url" "$body_file"
    return
  fi
  local token error
  token="$(gcloud auth print-access-token 2>/dev/null)" || fail 'Could not obtain a cloud access token.'
  error="$(temporary_file)"
  if test -n "$body_file"; then
    if curl -q --silent --show-error --fail --connect-timeout 5 --max-time 30 --request "$method" \
      --header @- --header 'Content-Type: application/json' --data-binary "@$body_file" "$url" \
      <<< "Authorization: Bearer $token" 2> "$error"; then rm -f "$error"; else rm -f "$error"; fail 'Cloud Monitoring request failed.'; fi
  else
    if curl -q --silent --show-error --fail --connect-timeout 5 --max-time 30 --request "$method" --header @- "$url" \
      <<< "Authorization: Bearer $token" 2> "$error"; then rm -f "$error"; else rm -f "$error"; fail 'Cloud Monitoring request failed.'; fi
  fi
}

channels() {
  local input="${OBSERVABILITY_NOTIFICATION_CHANNELS_JSON:-}"
  test -n "$input" || fail 'Missing OBSERVABILITY_NOTIFICATION_CHANNELS_JSON.'
  jq -ce --arg project "projects/$PROJECT_ID/notificationChannels/" '
    type == "array" and length > 0 and length <= 5 and
    all(.[]; type == "string" and startswith($project) and length <= 256)
  ' <<< "$input" >/dev/null || fail 'Invalid notification channel list.'
  jq -c 'unique' <<< "$input"
}

labels() {
  local kind="$1"
  jq -cn --arg owner "$owner" --arg run "$OBSERVABILITY_RUN" --arg kind "$kind" \
    '{managed_by:$owner,validation_run:($run|gsub("-";"_")),resource_kind:($kind|gsub("-";"_")),spec_version:"v1"}'
}

request_count_arm() {
  local response_class="$1"
  printf 'sum(increase({"run.googleapis.com/request_count",monitored_resource="cloud_run_revision",project_id="%s",location="%s",service_name="%s",response_code_class="%s"}[5m]))' \
    "$PROJECT_ID" "$REGION" "$SERVICE_NAME" "$response_class"
}

request_count_query() {
  local five_x two_x
  five_x="$(request_count_arm 5xx)"
  two_x="$(request_count_arm 2xx)"
  printf '(%s or 0 * %s)' "$five_x" "$two_x"
}

query_prometheus_sample() {
  local query="$1" at="${2:-$(date +%s)}" encoded response value
  [[ "$at" =~ ^[0-9]{10}$ ]] || fail 'Invalid PromQL evaluation time.'
  encoded="$(jq -rn --arg value "$query" '$value|@uri')"
  response="$(http GET "$prometheus_root/projects/$PROJECT_ID/location/global/prometheus/api/v1/query?query=$encoded&time=$at&timeout=20s")"
  value="$(jq -er '
    select(.status == "success" and .data.resultType == "vector") |
    .data.result as $result | select(($result | length) == 1) |
    $result[0].value as $sample |
    select(($sample | type) == "array" and ($sample | length) == 2 and
      ($sample[0] | type) == "number" and ($sample[0] | isfinite) and $sample[0] >= 0 and
      ($sample[1] | type) == "string" and
      ($sample[1] | test("^-?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$")) and
      (($sample[1] | tonumber) as $number | ($number | isfinite) and $number >= 0)) |
    $sample
  ' <<< "$response")" || fail 'PromQL query did not return one finite numeric sample.'
  python3 - "$value" <<'PY' || fail 'PromQL query did not return one finite numeric sample.'
import json
import math
import sys

number = float(json.loads(sys.argv[1])[1])
sys.exit(not (math.isfinite(number) and number >= 0))
PY
  printf '%s\n' "$value"
}

query_prometheus() {
  query_prometheus_sample "$1" "${2:-$(date +%s)}" | jq -er '.[1]'
}

render_5xx() {
  local notification_channels query
  notification_channels="$(channels)"
  query="$(request_count_query) >= 3"
  jq -cn --arg display "issue122 validation 5xx $OBSERVABILITY_RUN" --arg query "$query" \
    --argjson labels "$(labels 5xx)" --argjson channels "$notification_channels" '
    {
      displayName:$display, enabled:true, combiner:"OR", userLabels:$labels,
      notificationChannels:$channels,
      alertStrategy:{notificationPrompts:["OPENED","CLOSED"],
        notificationChannelStrategy:[{notificationChannelNames:$channels,renotifyInterval:"3600s"}]},
      conditions:[{
        displayName:"Cloud Run native 5xx >= 3 in 5m",
        conditionPrometheusQueryLanguage:{query:$query,duration:"0s",evaluationInterval:"30s"}
      }]
    }'
}

render_uptime() {
  require_env OBSERVABILITY_REVISION
  require_env MONITORING_SERVICE_AGENT
  [[ "$MONITORING_SERVICE_AGENT" =~ ^service-[0-9]+@gcp-sa-monitoring-notification\.iam\.gserviceaccount\.com$ ]] \
    || fail 'Invalid Monitoring service agent.'
  [[ "$OBSERVABILITY_REVISION" =~ ^[a-z][a-z0-9-]{0,62}$ ]] || fail 'Invalid OBSERVABILITY_REVISION.'
  jq -cn --arg display "issue122 validation uptime $OBSERVABILITY_RUN" \
    --arg project "$PROJECT_ID" --arg region "$REGION" --arg service "$SERVICE_NAME" \
    --arg revision "$OBSERVABILITY_REVISION" \
    --argjson labels "$(labels uptime)" '
    {
      displayName:$display, userLabels:$labels, period:"300s", timeout:"10s",
      selectedRegions:["USA_IOWA","EUROPE","ASIA_PACIFIC"],
      monitoredResource:{type:"cloud_run_revision",labels:{project_id:$project,location:$region,service_name:$service,revision_name:$revision,configuration_name:$service}},
      httpCheck:{requestMethod:"GET",useSsl:true,validateSsl:true,port:443,path:"/health",
        acceptedResponseStatusCodes:[{statusValue:200}],
        serviceAgentAuthentication:{type:"OIDC_TOKEN"}},
      contentMatchers:[{content:"^\\s*\\{\\s*\"status\"\\s*:\\s*\"ok\"\\s*\\}\\s*$",matcher:"MATCHES_REGEX"}],
      checkerType:"STATIC_IP_CHECKERS"
    }'
}

render_uptime_policy() {
  require_env UPTIME_CHECK_ID
  [[ "$UPTIME_CHECK_ID" =~ ^[a-zA-Z0-9_-]+$ ]] || fail 'Invalid UPTIME_CHECK_ID.'
  local notification_channels filter
  notification_channels="$(channels)"
  filter="resource.type = \"cloud_run_revision\" AND resource.labels.project_id = \"$PROJECT_ID\" AND resource.labels.location = \"$REGION\" AND resource.labels.service_name = \"$SERVICE_NAME\" AND metric.type = \"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.labels.check_id = \"$UPTIME_CHECK_ID\""
  jq -cn --arg display "issue122 validation uptime alert $OBSERVABILITY_RUN" --arg filter "$filter" \
    --argjson labels "$(labels uptime-policy)" --argjson channels "$notification_channels" '
    {
      displayName:$display,enabled:true,combiner:"OR",userLabels:$labels,notificationChannels:$channels,
      alertStrategy:{autoClose:"1800s",notificationPrompts:["OPENED","CLOSED"],
        notificationChannelStrategy:[{notificationChannelNames:$channels,renotifyInterval:"3600s"}]},
      conditions:[{
        displayName:"Authenticated health failures in at least two checker locations for 10m",
        conditionThreshold:{filter:$filter,comparison:"COMPARISON_GT",thresholdValue:1,duration:"600s",
          aggregations:[{alignmentPeriod:"300s",perSeriesAligner:"ALIGN_NEXT_OLDER",
            crossSeriesReducer:"REDUCE_COUNT_FALSE",
            groupByFields:["resource.label.project_id","resource.label.location","resource.label.service_name"]}],
          trigger:{count:1}}
      }]
    }'
}

render_log() {
  require_env SYSTEM_LOG_NAME
  require_env SYSTEM_LOG_SIGNATURE
  [[ "$SYSTEM_LOG_NAME" =~ ^projects/${PROJECT_ID}/logs/[A-Za-z0-9._%+~-]+$ ]] || fail 'Invalid SYSTEM_LOG_NAME.'
  test "${#SYSTEM_LOG_SIGNATURE}" -le 240 || fail 'SYSTEM_LOG_SIGNATURE is too long.'
  [[ "$SYSTEM_LOG_SIGNATURE" =~ ^[A-Za-z0-9._:/\ \(\)-]+$ ]] \
    || fail 'SYSTEM_LOG_SIGNATURE contains unsupported characters.'
  local notification_channels filter
  notification_channels="$(channels)"
  filter="resource.type=\"cloud_run_revision\" AND resource.labels.project_id=\"$PROJECT_ID\" AND resource.labels.location=\"$REGION\" AND resource.labels.service_name=\"$SERVICE_NAME\" AND logName=\"$SYSTEM_LOG_NAME\" AND textPayload=\"$SYSTEM_LOG_SIGNATURE\""
  jq -cn --arg display "issue122 validation abnormal exit $OBSERVABILITY_RUN" --arg filter "$filter" \
    --argjson labels "$(labels log)" --argjson channels "$notification_channels" '
    {
      displayName:$display,enabled:true,combiner:"OR",userLabels:$labels,notificationChannels:$channels,
      alertStrategy:{autoClose:"1800s",notificationRateLimit:{period:"300s"}},
      conditions:[{displayName:"Verified Cloud Run abnormal exit signature",conditionMatchedLog:{filter:$filter}}]
    }'
}

verify_channels() {
  local channel
  test "${OBSERVABILITY_CONFIRMED_RECEIVERS:-false}" = true \
    || fail 'Protected receiver confirmation is required before live policy changes.'
  while IFS= read -r channel; do
    http GET "$monitoring_root/$channel" | jq -e --arg name "$channel" \
      '.name == $name and (.type | type == "string" and length > 0) and .enabled == true and
       ((.verificationStatus // "VERIFICATION_STATUS_UNSPECIFIED") |
         IN("VERIFIED","VERIFICATION_STATUS_UNSPECIFIED"))' >/dev/null \
      || fail 'A notification channel is unavailable or unverified.'
  done < <(channels | jq -r '.[]')
}

verify_5xx_label() {
  local filter encoded response descriptor
  descriptor="$(http GET "$monitoring_root/projects/$PROJECT_ID/metricDescriptors/run.googleapis.com/request_count")"
  jq -e '.type == "run.googleapis.com/request_count" and
    any(.labels[]?; .key == "response_code_class")' <<< "$descriptor" >/dev/null \
    || fail 'Native request metric descriptor lacks response_code_class.'
  filter="metric.type=\"run.googleapis.com/request_count\" AND resource.type=\"cloud_run_revision\" AND resource.labels.project_id=\"$PROJECT_ID\" AND resource.labels.location=\"$REGION\" AND resource.labels.service_name=\"$SERVICE_NAME\""
  encoded="$(jq -rn --arg value "$filter" '$value|@uri')"
  response="$(http GET "$monitoring_root/projects/$PROJECT_ID/timeSeries?filter=$encoded&interval.endTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)&interval.startTime=$(date -u -v-15M +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '15 minutes ago' +%Y-%m-%dT%H:%M:%SZ)&view=FULL")"
  jq -e 'any(.timeSeries[]?;
    .metric.labels.response_code_class == "5xx" and
    any(.points[]?; ((.value.int64Value // .value.doubleValue // null) | tonumber? // 0) > 0))
  ' <<< "$response" >/dev/null \
    || fail 'Live native metric inventory did not prove a numeric response_code_class=5xx point.'
}

resource_type() {
  case "$1" in
    5xx|uptime-policy|log|policy) printf 'alertPolicies' ;;
    uptime) printf 'uptimeCheckConfigs' ;;
    *) fail 'Unknown policy kind.' ;;
  esac
}

render() {
  case "$1" in
    5xx) render_5xx ;;
    uptime) render_uptime ;;
    uptime-policy) render_uptime_policy ;;
    log) render_log ;;
    *) fail 'Unknown policy kind.' ;;
  esac
}

mutation_journal() {
  test "$SERVICE_NAME" = vlrgg-query-check || fail 'Policy mutations are limited to the validation service.'
  export VALIDATION_SERVICE=vlrgg-query-check
  local journal
  journal="$("$service_helper" read)" || fail 'An active validation journal is required.'
  test "$(jq -er '.run' <<< "$journal")" = "$OBSERVABILITY_RUN" || fail 'Validation journal owner mismatch.'
  printf '%s\n' "$journal"
}

require_deadline() {
  require_env OBSERVABILITY_DEADLINE_EPOCH
  [[ "$OBSERVABILITY_DEADLINE_EPOCH" =~ ^[0-9]{10}$ ]] || fail 'Invalid observability deadline.'
  test "$(date +%s)" -le "$((OBSERVABILITY_DEADLINE_EPOCH - 1800))" \
    || fail 'The validation fault cutoff expired; the restoration reserve is active.'
}

ensure_monitoring_invoker() {
  local principal project_number project project_policy agent_bindings journal policy bindings count existed
  project="$(gcloud_json projects describe "$PROJECT_ID" --format=json)"
  project_number="$(jq -er --arg project "$PROJECT_ID" '
    select(.projectId == $project) | .projectNumber | tostring | select(test("^[0-9]+$"))
  ' <<< "$project")" || fail 'Live project inventory lacks an exact numeric project number.'
  principal="service-$project_number@gcp-sa-monitoring-notification.iam.gserviceaccount.com"
  MONITORING_SERVICE_AGENT="$principal"
  export MONITORING_SERVICE_AGENT
  project_policy="$(gcloud_json projects get-iam-policy "$PROJECT_ID" --format=json)"
  agent_bindings="$(jq -c --arg member "serviceAccount:$principal" '
    [.bindings[]? | select(.role == "roles/monitoring.notificationServiceAgent" and
      any(.members[]?; . == $member))]
  ' <<< "$project_policy")"
  test "$(jq 'length' <<< "$agent_bindings")" = 1 \
    || fail 'Monitoring notification service-agent project role is missing or ambiguous.'
  jq -e '.[0] | has("condition") | not' <<< "$agent_bindings" >/dev/null \
    || fail 'Monitoring notification service-agent project role is conditional.'
  journal="$(mutation_journal)"
  policy="$(gcloud_json run services get-iam-policy "$SERVICE_NAME" \
    --project "$PROJECT_ID" --region "$REGION" --format=json)"
  bindings="$(jq -c --arg member "serviceAccount:$principal" '
    [.bindings[]? | select(.role == "roles/run.invoker" and any(.members[]?; . == $member))]
  ' <<< "$policy")"
  count="$(jq 'length' <<< "$bindings")"
  test "$count" -le 1 || fail 'Monitoring invoker binding is ambiguous.'
  if test "$count" = 1; then
    jq -e '.[0] | has("condition") | not' <<< "$bindings" >/dev/null \
      || fail 'Monitoring service agent has a conditional invoker binding.'
    existed=true
  else
    existed=false
  fi

  if ! jq -e 'has("iam")' <<< "$journal" >/dev/null; then
    jq -e 'has("pending") | not' <<< "$journal" >/dev/null || fail 'Another journal mutation is pending.'
    "$service_helper" pending iam "$principal"
    "$service_helper" iam "$principal" "$existed"
    journal="$(mutation_journal)"
  fi
  jq -e --arg principal "$principal" '.iam.principal == $principal' <<< "$journal" >/dev/null \
    || fail 'Journal IAM principal mismatch.'
  if jq -e '.iam.existed == true' <<< "$journal" >/dev/null; then
    test "$existed" = true || fail 'Preexisting Monitoring invoker binding disappeared.'
    return
  fi
  if jq -e '.iam.added == true' <<< "$journal" >/dev/null; then
    test "$existed" = true || fail 'Owned Monitoring invoker binding disappeared.'
    return
  fi
  if ! jq -e '.pending.kind == "iam-add" and .pending.target == .iam.principal' <<< "$journal" >/dev/null; then
    "$service_helper" pending iam-add "$principal"
  fi
  if test "$existed" = false; then
    gcloud_mutate run services add-iam-policy-binding "$SERVICE_NAME" --quiet \
      --project "$PROJECT_ID" --region "$REGION" --role roles/run.invoker \
      --member "serviceAccount:$principal"
  fi
  policy="$(gcloud_json run services get-iam-policy "$SERVICE_NAME" \
    --project "$PROJECT_ID" --region "$REGION" --format=json)"
  jq -e --arg member "serviceAccount:$principal" '
    any(.bindings[]?; .role == "roles/run.invoker" and (has("condition") | not) and
      any(.members[]?; . == $member))
  ' <<< "$policy" >/dev/null || fail 'Monitoring invoker binding read-back failed.'
  "$service_helper" iam-added
}

verify_resource() {
  local desired="$1" response="$2"
  jq -e --argjson desired "$desired" '
    . as $actual | [$desired | paths(scalars)] as $paths |
    [$desired | paths(type == "array")] as $arrays |
    all($paths[]; . as $path | ($actual | getpath($path)) == ($desired | getpath($path))) and
    all($arrays[]; . as $path |
      (($actual | getpath($path) | type) == "array") and
      (($actual | getpath($path) | length) == ($desired | getpath($path) | length))) and
    ((.validity.code // 0) == 0)
  ' <<< "$response" >/dev/null || fail 'Owned resource read-back has a different or invalid configuration.'
}

ensure() {
  local kind="$1" collection desired list matches count body response name journal resource_kind
  export VALIDATION_SERVICE=vlrgg-query-check
  require_deadline
  journal="$(mutation_journal)"
  jq -e '.phase | IN("prepared","resources","fault")' <<< "$journal" >/dev/null \
    || fail 'Policy creation is unavailable during restoration.'
  test "$kind" != uptime || ensure_monitoring_invoker
  journal="$(mutation_journal)"
  verify_channels
  test "$kind" != 5xx || verify_5xx_label
  collection="$(resource_type "$kind")"
  desired="$(render "$kind")"
  list="$(http GET "$monitoring_root/projects/$PROJECT_ID/$collection?pageSize=1000")"
  jq -e '(.nextPageToken // "") == ""' <<< "$list" >/dev/null \
    || fail 'Owned resource inventory exceeded one bounded page.'
  matches="$(jq -c --arg owner "$owner" --arg run "${OBSERVABILITY_RUN//-/_}" --arg kind "${kind//-/_}" '
    [(.alertPolicies // .uptimeCheckConfigs // [])[]? |
      select(.userLabels.managed_by == $owner and .userLabels.validation_run == $run and
        .userLabels.resource_kind == $kind)]
  ' <<< "$list")"
  count="$(jq 'length' <<< "$matches")"
  test "$count" -le 1 || fail 'Multiple owned resources match this run.'
  if test "$count" = 1; then
    name="$(jq -er '.[0].name' <<< "$matches")"
    response="$(http GET "$monitoring_root/$name")"
    verify_resource "$desired" "$response"
    if ! jq -e --arg name "$name" 'any(.resources[]?; .name == $name and .owned == true)' \
      <<< "$journal" >/dev/null; then
      jq -e --arg kind "$kind" '.pending.kind == $kind and .pending.owner == .run' \
        <<< "$journal" >/dev/null || fail 'Existing owned resource is not journaled or pending.'
      resource_kind=policy
      test "$kind" = uptime && resource_kind=uptime
      "$service_helper" resource "$resource_kind" "$name"
    fi
    printf '%s\n' "$name"
    return
  fi
  jq -e 'has("pending") | not' <<< "$journal" >/dev/null || fail 'Another journal mutation is pending.'
  "$service_helper" pending "$kind" "$kind"
  body="$(temporary_file)"
  printf '%s\n' "$desired" > "$body"
  response="$(http POST "$monitoring_root/projects/$PROJECT_ID/$collection" "$body")"
  rm -f "$body"
  name="$(jq -er '.name' <<< "$response")"
  [[ "$name" == projects/"$PROJECT_ID"/"$collection"/* ]] || fail 'Unexpected created resource name.'
  response="$(http GET "$monitoring_root/$name")"
  verify_resource "$desired" "$response"
  resource_kind=policy
  test "$kind" = uptime && resource_kind=uptime
  "$service_helper" resource "$resource_kind" "$name"
  printf '%s\n' "$name"
}

find_owned() {
  local kind="$1" collection list matches
  collection="$(resource_type "$kind")"
  list="$(http GET "$monitoring_root/projects/$PROJECT_ID/$collection?pageSize=1000")"
  jq -e '(.nextPageToken // "") == ""' <<< "$list" >/dev/null \
    || fail 'Owned resource inventory exceeded one bounded page.'
  matches="$(jq -c --arg owner "$owner" --arg run "${OBSERVABILITY_RUN//-/_}" --arg kind "${kind//-/_}" '
    [(.alertPolicies // .uptimeCheckConfigs // [])[]? |
      select(.userLabels.managed_by == $owner and .userLabels.validation_run == $run and
        .userLabels.resource_kind == $kind) | .name]
  ' <<< "$list")"
  test "$(jq 'length' <<< "$matches")" -le 1 || fail 'Multiple owned resources match this run.'
  jq -r '.[]' <<< "$matches"
}

disable() {
  local name="$1" body journal
  export VALIDATION_SERVICE=vlrgg-query-check
  journal="$(mutation_journal)"
  jq -e '.phase | IN("resources","fault")' <<< "$journal" >/dev/null \
    || fail 'Policy disable is unavailable in this journal phase.'
  [[ "$name" == projects/"$PROJECT_ID"/alertPolicies/* ]] || fail 'Can only disable an exact alert policy.'
  jq -e --arg name "$name" 'any(.resources[]?; .kind == "policy" and .name == $name and .owned == true)' \
    <<< "$journal" >/dev/null || fail 'Policy is not owned by the active journal.'
  if ! jq -e 'has("pending")' <<< "$journal" >/dev/null; then
    "$service_helper" pending disable-policy "$name"
  else
    jq -e --arg name "$name" '.pending.kind == "disable-policy" and .pending.target == $name' \
      <<< "$journal" >/dev/null || fail 'Another journal mutation is pending.'
  fi
  body="$(temporary_file)"
  http GET "$monitoring_root/$name" | jq -e --arg owner "$owner" --arg run "${OBSERVABILITY_RUN//-/_}" \
    --arg name "$name" '
    .name == $name and .userLabels.managed_by == $owner and .userLabels.validation_run == $run
  ' >/dev/null || fail 'Policy ownership mismatch.'
  printf '%s\n' '{"enabled":false}' > "$body"
  http PATCH "$monitoring_root/$name?updateMask=enabled" "$body" >/dev/null
  rm -f "$body"
  "$service_helper" clear-pending disable-policy "$name"
}

delete_owned() {
  local name="$1" kind="$2" collection list current journal
  export VALIDATION_SERVICE=vlrgg-query-check
  journal="$(mutation_journal)"
  collection="$(resource_type "$kind")"
  [[ "$name" == projects/"$PROJECT_ID"/"$collection"/* ]] || fail 'Resource path does not match kind.'
  jq -e --arg name "$name" --arg kind "$kind" 'any(.resources[]?;
    .name == $name and .owned == true and
    (if $kind == "uptime" then .kind == "uptime" else .kind == "policy" end))' \
    <<< "$journal" >/dev/null || fail 'Resource is not owned by the active journal.'
  list="$(http GET "$monitoring_root/projects/$PROJECT_ID/$collection?pageSize=1000")"
  jq -e '(.nextPageToken // "") == ""' <<< "$list" >/dev/null \
    || fail 'Owned resource inventory exceeded one bounded page.'
  current="$(jq -c --arg name "$name" '[(.alertPolicies // .uptimeCheckConfigs // [])[]? |
    select(.name == $name)]' <<< "$list")"
  test "$(jq 'length' <<< "$current")" -le 1 || fail 'Duplicate resource names returned.'
  if test "$(jq 'length' <<< "$current")" = 0; then
    return
  fi
  current="$(jq -c '.[0]' <<< "$current")"
  jq -e --arg owner "$owner" --arg run "${OBSERVABILITY_RUN//-/_}" --arg kind "${kind//-/_}" '
    .userLabels.managed_by == $owner and .userLabels.validation_run == $run and
    (if $kind == "policy" then (.userLabels.resource_kind | IN("5xx","uptime_policy","log"))
     else .userLabels.resource_kind == $kind end)
  ' <<< "$current" >/dev/null || fail 'Resource ownership mismatch.'
  http DELETE "$monitoring_root/$name" >/dev/null
}

require_context
case "${1:-}" in
  render) render "${2:-}" ;;
  find-owned) find_owned "${2:-}" ;;
  verify-5xx-label) verify_5xx_label ;;
  query-5xx-count) query_prometheus "$(request_count_query)" "${2:-}" ;;
  query-2xx-count) query_prometheus "$(request_count_arm 2xx)" "${2:-}" ;;
  query-5xx-sample) query_prometheus_sample "$(request_count_query)" "${2:-}" ;;
  query-2xx-sample) query_prometheus_sample "$(request_count_arm 2xx)" "${2:-}" ;;
  ensure) ensure "${2:-}" ;;
  disable) disable "${2:-}" ;;
  delete) delete_owned "${2:-}" "${3:-}" ;;
  *) fail 'Usage: observability-policies.sh render|ensure|find-owned KIND | verify-5xx-label | query-(2xx|5xx)-(count|sample) [EPOCH] | disable NAME | delete NAME KIND' ;;
esac

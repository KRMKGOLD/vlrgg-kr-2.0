#!/usr/bin/env bash
set -euo pipefail

readonly journal_key='vlrgg-observability-validation'
readonly api_root="${CLOUD_RUN_API_ROOT:-https://run.googleapis.com/v2}"

fail() { echo "Observability service operation failed: $*" >&2; exit 1; }
require_env() { test -n "${!1:-}" || fail "Missing $1."; }

gcloud_json() {
  local output error
  output="$(mktemp)"; error="$(mktemp)"
  if ! gcloud "$@" > "$output" 2> "$error"; then
    rm -f "$output" "$error"
    fail 'Cloud inventory command failed.'
  fi
  cat "$output"
  rm -f "$output" "$error"
}

require_context() {
  require_env PROJECT_ID
  require_env REGION
  require_env VALIDATION_SERVICE
  [[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,61}[a-z0-9]$ ]] || fail 'Invalid PROJECT_ID.'
  [[ "$REGION" =~ ^[a-z0-9-]+$ ]] || fail 'Invalid REGION.'
  test "$VALIDATION_SERVICE" = vlrgg-query-check || fail 'Unexpected validation service.'
  service_name="projects/$PROJECT_ID/locations/$REGION/services/$VALIDATION_SERVICE"
}

http() {
  local method="$1" url="$2" body_file="${3:-}"
  if test -n "${OBSERVABILITY_HTTP:-}"; then
    "$OBSERVABILITY_HTTP" "$method" "$url" "$body_file"
    return
  fi
  local token
  token="$(gcloud auth print-access-token 2>/dev/null)" || fail 'Could not obtain a cloud access token.'
  if test -n "$body_file"; then
    curl -q --silent --show-error --fail --connect-timeout 5 --max-time 30 --request "$method" \
      --header @- --header 'Content-Type: application/json' --data-binary "@$body_file" "$url" \
      <<< "Authorization: Bearer $token"
  else
    curl -q --silent --show-error --fail --connect-timeout 5 --max-time 30 --request "$method" --header @- "$url" \
      <<< "Authorization: Bearer $token"
  fi
}

get_service() { http GET "$api_root/$service_name"; }
get_revision() { http GET "$api_root/$1"; }

wait_operation() {
  local operation="$1" response
  [[ "$operation" == projects/"$PROJECT_ID"/locations/"$REGION"/operations/* ]] \
    || fail 'Unexpected Cloud Run operation name.'
  for _ in {1..60}; do
    response="$(http GET "$api_root/$operation")"
    if jq -e '.done == true' <<< "$response" >/dev/null; then
      jq -e 'has("error") | not' <<< "$response" >/dev/null \
        || fail 'Cloud Run operation returned an error.'
      return
    fi
    sleep 2
  done
  fail 'Cloud Run operation timed out.'
}

validate_journal() {
  local journal="$1"
  test "$(printf %s "$journal" | wc -c | tr -d ' ')" -le 8192 || fail 'Journal exceeds 8 KiB.'
  jq -e '
    ((keys_unsorted - ["v","run","phase","baselineRevision","baselineTemplateHash","iam","resources","pending"]) | length == 0) and
    .v == 1 and
    (.run | type == "string" and test("^[0-9]+-[0-9]+$") and length <= 64) and
    (.phase | IN("prepared", "resources", "fault", "restoring", "verified")) and
    (.baselineRevision | type == "string" and test("^projects/[^/]+/locations/[^/]+/services/[^/]+/revisions/[^/]+$")) and
    (.baselineTemplateHash | type == "string" and test("^[0-9a-f]{64}$")) and
    (.resources | type == "array" and length <= 12 and all(.[ ];
      ((keys_unsorted - ["kind","name","owned"]) | length == 0) and
      (.kind | IN("policy", "uptime", "revision", "image")) and
      (.name | type == "string" and length > 0 and length <= 512 and
        (test("^https?://") | not) and test("^[[:graph:]]+$")) and .owned == true)) and
    ((has("iam") | not) or
      ((.iam | keys_unsorted) - ["principal","existed","added"] | length == 0) and
      (.iam.principal | type == "string" and length <= 320) and
      (.iam.existed | type == "boolean") and (.iam.added | type == "boolean")) and
    ((has("iam") | not) or (.iam.existed == false or .iam.added == false)) and
    ((has("pending") | not) or
      ((.pending | keys_unsorted) - ["kind","owner","target"] | length == 0) and
      (.pending.kind | type == "string" and length <= 40) and
      .pending.owner == .run and
      (.pending.target | type == "string" and length > 0 and length <= 512 and
        (test("^https?://") | not) and test("^[[:graph:]]+$")))
  ' <<< "$journal" >/dev/null || fail 'Invalid journal schema.'
  jq -e --arg prefix "projects/$PROJECT_ID/locations/$REGION/services/$VALIDATION_SERVICE/revisions/" \
    '.baselineRevision | startswith($prefix)' <<< "$journal" >/dev/null \
    || fail 'Journal belongs to another service.'
}

template_projection() {
  jq -Sc '
    {
      labels: ((.labels // {}) | with_entries(select(.key | test("\\.googleapis\\.com/|\\.knative\\.dev/") | not))),
      annotations: ((.annotations // {}) | with_entries(select(.key | test("\\.googleapis\\.com/|\\.knative\\.dev/") | not))),
      containers: [.containers[] | {
        name, image, command, args, env, resources, ports, volumeMounts,
        livenessProbe, startupProbe, readinessProbe, workingDir, dependsOn,
        baseImageUri, sourceCode, sandboxLauncher
      } | with_entries(select(.value != null))],
      volumes: [.volumes[]? | {
        name, secret, cloudSqlInstance, emptyDir, nfs, gcs
      } | with_entries(select(.value != null))],
      serviceAccount: (.serviceAccount // null), scaling: (.scaling // null),
      timeout: (.timeout // null),
      maxInstanceRequestConcurrency: (.maxInstanceRequestConcurrency // null),
      vpcAccess: (.vpcAccess // null), executionEnvironment: (.executionEnvironment // null),
      encryptionKey: (.encryptionKey // null), sessionAffinity: (.sessionAffinity // null),
      serviceMesh: (.serviceMesh // null), nodeSelector: (.nodeSelector // null),
      encryptionKeyRevocationAction: (.encryptionKeyRevocationAction // null),
      encryptionKeyShutdownDuration: (.encryptionKeyShutdownDuration // null),
      client: (.client // null), clientVersion: (.clientVersion // null),
      gpuZonalRedundancyDisabled: (.gpuZonalRedundancyDisabled // null)
    } | with_entries(select(.value != null))
  '
}

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum | awk '{print $1}';
  else shasum -a 256 | awk '{print $1}'; fi
}

patch_service() {
  local service="$1" update_mask="$2" patch="$3" body operation expected
  body="$(mktemp)"
  jq -n --arg name "$service_name" --arg etag "$(jq -er '.etag' <<< "$service")" \
    --argjson patch "$patch" '$patch + {name:$name, etag:$etag}' > "$body"
  operation="$(http PATCH "$api_root/$service_name?updateMask=$update_mask" "$body")"
  rm -f "$body"
  wait_operation "$(jq -er '.name' <<< "$operation")"
}

write_journal() {
  local journal="$1" expected="${2-__missing__}" expected_etag="${3:-}" service current annotations readback
  validate_journal "$journal"
  service="$(get_service)"
  if test -n "$expected_etag"; then
    test "$(jq -er '.etag' <<< "$service")" = "$expected_etag" || fail 'Service changed before journal prepare.'
  fi
  current="$(jq -r --arg key "$journal_key" '.annotations[$key] // empty' <<< "$service")"
  if test "$expected" = __missing__; then
    test -z "$current" || fail 'Journal appeared before the prepare CAS.'
  else
    test "$current" = "$expected" || fail 'Journal changed concurrently.'
  fi
  annotations="$(jq -c --arg key "$journal_key" --arg value "$journal" \
    '(.annotations // {}) + {($key):$value}' <<< "$service")"
  patch_service "$service" annotations "$(jq -cn --argjson annotations "$annotations" '{annotations:$annotations}')"
  readback="$(get_service)"
  test "$(jq -er --arg key "$journal_key" '.annotations[$key]' <<< "$readback")" = "$journal" \
    || fail 'Journal read-back mismatch.'
}

read_journal() {
  local service journal
  service="$(get_service)"
  journal="$(jq -r --arg key "$journal_key" '.annotations[$key] // empty' <<< "$service")"
  test -n "$journal" || return 1
  validate_journal "$journal"
  printf '%s\n' "$journal"
}

require_journal_owner() {
  local journal="$1"
  require_env OBSERVABILITY_RUN
  test "$(jq -er '.run' <<< "$journal")" = "$OBSERVABILITY_RUN" || fail 'Journal owner mismatch.'
}

guard() {
  local services service journal
  services="$(gcloud_json run services list --project "$PROJECT_ID" --region "$REGION" \
    --filter "metadata.name=$VALIDATION_SERVICE" --format=json)"
  jq -e --arg name "$VALIDATION_SERVICE" 'length <= 1 and all(.[]; .metadata.name == $name)' \
    <<< "$services" >/dev/null || fail 'Validation service inventory is ambiguous.'
  test "$(jq 'length' <<< "$services")" = 1 || return
  service="$(get_service)" || fail 'Could not inspect the validation recovery journal.'
  journal="$(jq -r --arg key "$journal_key" '.annotations[$key] // empty' <<< "$service")"
  test -z "$journal" || { validate_journal "$journal"; fail 'An observability validation recovery journal is active.'; }
}

prepare() {
  require_env OBSERVABILITY_RUN
  [[ "$OBSERVABILITY_RUN" =~ ^[0-9]+-[0-9]+$ ]] || fail 'Invalid OBSERVABILITY_RUN.'
  local service traffic revision projection desired_projection baseline_hash journal policy baseline_name
  service="$(get_service)"
  jq -e --arg name "$service_name" --arg key "$journal_key" '
    .name == $name and (.reconciling // false) == false and
    (.invokerIamDisabled // false) == false and
    ((.annotations // {}) | has($key) | not) and
    any(.terminalCondition?; .type == "Ready" and .state == "CONDITION_SUCCEEDED")
  ' <<< "$service" >/dev/null || fail 'Validation service is not in a clean Ready state.'
  jq -e '(.template.healthCheckDisabled // false) == false' <<< "$service" >/dev/null \
    || fail 'Baseline uses template-only settings that cannot be reconstructed from an immutable revision.'
  jq -e '
    ((.template | keys_unsorted) - ["revision","labels","annotations","containers","volumes","serviceAccount","scaling","timeout","maxInstanceRequestConcurrency","vpcAccess","executionEnvironment","encryptionKey","sessionAffinity","serviceMesh","nodeSelector","encryptionKeyRevocationAction","encryptionKeyShutdownDuration","client","clientVersion","gpuZonalRedundancyDisabled","healthCheckDisabled"] | length == 0) and
    all(.template.containers[]; ((keys_unsorted) - ["name","image","command","args","env","resources","ports","volumeMounts","livenessProbe","startupProbe","readinessProbe","workingDir","dependsOn","baseImageUri","sourceCode","sandboxLauncher","buildInfo"] | length == 0)) and
    all(.template.volumes[]?; ((keys_unsorted) - ["name","secret","cloudSqlInstance","emptyDir","nfs","gcs"] | length == 0)) and
    all((.template.labels // {}) + (.template.annotations // {}) | keys[]?;
      test("\\.googleapis\\.com/|\\.knative\\.dev/") | not)
  ' <<< "$service" >/dev/null || fail 'Baseline template contains unsupported or reserved fields.'
  policy="$(gcloud_json run services get-iam-policy "$VALIDATION_SERVICE" \
    --project "$PROJECT_ID" --region "$REGION" --format=json)"
  jq -e '[.bindings[]? | select(.role == "roles/run.invoker") | .members[]? |
    select(. == "allUsers" or . == "allAuthenticatedUsers")] | length == 0' \
    <<< "$policy" >/dev/null || fail 'Validation service must remain private.'
  traffic="$(jq -c '[.traffic[]? | select((.percent // 0) > 0)]' <<< "$service")"
  jq -e 'length == 1 and .[0].percent == 100 and (. [0].tag // "") == "" and
    (. [0].revision | type == "string" and length > 0)' <<< "$traffic" >/dev/null \
    || fail 'Validation service must have one untagged revision at 100% traffic.'
  revision="$(jq -r '.[0].revision' <<< "$traffic")"
  [[ "$revision" == projects/* ]] || revision="projects/$PROJECT_ID/locations/$REGION/services/$VALIDATION_SERVICE/revisions/$revision"
  revision_json="$(get_revision "$revision")"
  jq -e --arg name "$revision" '.name == $name and any(.conditions[]?; .type == "Ready" and .state == "CONDITION_SUCCEEDED")' \
    <<< "$revision_json" >/dev/null || fail 'Baseline revision is not Ready.'
  jq -e 'all(.containers[]; .image | test("@sha256:[0-9a-f]{64}$"))' <<< "$revision_json" >/dev/null \
    || fail 'Baseline revision does not expose immutable container digests.'
  projection="$(template_projection <<< "$revision_json")"
  desired_projection="$(template_projection <<< "$(jq -c '.template' <<< "$service")")"
  if test "$(jq -r '.containers | length' <<< "$projection")" = 1 &&
    test "$(jq -r '.containers | length' <<< "$desired_projection")" = 1 &&
    jq -e '.containers[0].name | type == "string" and length > 0' <<< "$projection" >/dev/null &&
    jq -e '(.containers[0] | has("name") | not)' <<< "$desired_projection" >/dev/null; then
    # Cloud Run can name the immutable container while omitting its name in the Service template.
    baseline_name="$(jq -er '.containers[0].name' <<< "$projection")"
    desired_projection="$(jq -Sc --arg name "$baseline_name" '.containers[0].name=$name' <<< "$desired_projection")"
  fi
  test "$projection" = "$desired_projection" || fail 'Service template differs from the serving baseline revision.'
  baseline_hash="$(printf %s "$projection" | sha256)"
  journal="$(jq -cn --arg run "$OBSERVABILITY_RUN" --arg revision "$revision" --arg hash "$baseline_hash" \
    '{v:1,run:$run,phase:"prepared",baselineRevision:$revision,baselineTemplateHash:$hash,resources:[]}')"
  write_journal "$journal" __missing__ "$(jq -er '.etag' <<< "$service")"
  printf '%s\n' "$journal"
}

mutate_journal() {
  local command="$1" kind="${2:-}" target="${3:-}" journal updated pending_target
  journal="$(read_journal)" || fail 'No recovery journal exists.'
  require_journal_owner "$journal"
  case "$command" in
    pending)
      [[ "$kind" == 5xx || "$kind" =~ ^[a-z][a-z0-9-]{0,39}$ ]] || fail 'Invalid pending kind.'
      test -n "$target" || fail 'Missing pending target.'
      jq -e 'has("pending") | not' <<< "$journal" >/dev/null || fail 'A pending intent already exists.'
      updated="$(jq -c --arg kind "$kind" --arg target "$target" \
        '.pending={kind:$kind,owner:.run,target:$target}' <<< "$journal")" ;;
    resource)
      [[ "$kind" =~ ^(policy|uptime|revision|image)$ ]] || fail 'Invalid resource kind.'
      test -n "$target" || fail 'Missing resource name.'
      jq -e --arg kind "$kind" '
        .pending.owner == .run and
        (if $kind == "policy" then (.pending.kind | IN("5xx","uptime-policy","log"))
         else .pending.kind == $kind end)
      ' <<< "$journal" >/dev/null || fail 'Resource does not complete the pending intent.'
      pending_target="$(jq -er '.pending.target' <<< "$journal")"
      if test "$kind" = revision; then
        test "${target##*/}" = "$pending_target" || fail 'Revision does not match the pending target.'
      elif test "$kind" = image; then
        test "${target%@sha256:*}" = "${pending_target%:*}" || fail 'Image does not match the pending target.'
      fi
      updated="$(jq -c --arg kind "$kind" --arg target "$target" '
        .resources += [{kind:$kind,name:$target,owned:true}] |
        .resources |= unique_by(.kind,.name) | del(.pending) | .phase="resources"
      ' <<< "$journal")" ;;
    phase)
      [[ "$kind" =~ ^(prepared|resources|fault|restoring|verified)$ ]] || fail 'Invalid phase.'
      jq -e 'has("pending") | not' <<< "$journal" >/dev/null || fail 'Cannot change phase with a pending intent.'
      updated="$(jq -c --arg phase "$kind" '.phase=$phase | del(.pending)' <<< "$journal")" ;;
    iam)
      [[ "$kind" =~ ^service-[0-9]+@gcp-sa-monitoring-notification\.iam\.gserviceaccount\.com$ ]] \
        || fail 'Invalid Monitoring service agent.'
      [[ "$target" == true || "$target" == false ]] || fail 'IAM existed must be boolean.'
      jq -e --arg principal "$kind" '.pending.kind == "iam" and .pending.target == $principal' \
        <<< "$journal" >/dev/null || fail 'IAM state does not complete the pending intent.'
      updated="$(jq -c --arg principal "$kind" --argjson existed "$target" \
        '.iam={principal:$principal,existed:$existed,added:false} | del(.pending) | .phase="resources"' \
        <<< "$journal")" ;;
    iam-added)
      jq -e '.iam.existed == false and .iam.added == false and .pending.kind == "iam-add" and
        .pending.target == .iam.principal' <<< "$journal" >/dev/null || fail 'IAM add state is invalid.'
      updated="$(jq -c '.iam.added=true | del(.pending)' <<< "$journal")" ;;
    drop-resource)
      [[ "$kind" =~ ^(policy|uptime|revision|image)$ ]] || fail 'Invalid resource kind.'
      updated="$(jq -c --arg kind "$kind" --arg target "$target" '
        .resources |= map(select(.kind != $kind or .name != $target))
      ' <<< "$journal")" ;;
    iam-cleared)
      updated="$(jq -c 'if .iam.added == true then .iam.added=false else . end' <<< "$journal")" ;;
    clear-pending)
      jq -e --arg kind "$kind" --arg target "$target" \
        '.pending.kind == $kind and .pending.target == $target' <<< "$journal" >/dev/null \
        || fail 'Pending intent changed.'
      updated="$(jq -c 'del(.pending)' <<< "$journal")" ;;
    *) fail 'Unsupported journal mutation.' ;;
  esac
  write_journal "$updated" "$journal"
}

restore() {
  local journal baseline revision projection hash service policy
  journal="$(read_journal)" || fail 'No recovery journal exists.'
  require_journal_owner "$journal"
  write_journal "$(jq -c '.phase="restoring"' <<< "$journal")" "$journal"
  journal="$(read_journal)"
  baseline="$(jq -er '.baselineRevision' <<< "$journal")"
  revision="$(get_revision "$baseline")"
  jq -e --arg name "$baseline" '.name == $name and any(.conditions[]?; .type == "Ready" and .state == "CONDITION_SUCCEEDED")' \
    <<< "$revision" >/dev/null || fail 'Baseline revision is missing or not Ready.'
  projection="$(template_projection <<< "$revision")"
  hash="$(printf %s "$projection" | sha256)"
  test "$hash" = "$(jq -er '.baselineTemplateHash' <<< "$journal")" \
    || fail 'Baseline revision template hash changed.'

  service="$(get_service)"
  patch_service "$service" traffic "$(jq -cn --arg revision "$baseline" \
    '{traffic:[{type:"TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION",revision:$revision,percent:100}]}')"
  service="$(get_service)"
  jq -e --arg revision "$baseline" '[.traffic[]? | select((.percent // 0) > 0)] |
    length == 1 and .[0].percent == 100 and .[0].revision == $revision and (. [0].tag // "") == ""' \
    <<< "$service" >/dev/null || fail 'Baseline traffic restoration did not persist.'

  patch_service "$service" template "$(jq -cn --argjson template "$projection" '{template:$template}')"
  service="$(get_service)"
  restored_projection="$(template_projection <<< "$(jq -c '.template' <<< "$service")")"
  test "$(printf %s "$restored_projection" | sha256)" = "$hash" \
    || fail 'Baseline template restoration did not persist.'
  jq -e '(.invokerIamDisabled // false) == false and (.ingress // "") != "INGRESS_TRAFFIC_NONE"' \
    <<< "$service" >/dev/null || fail 'Validation service is not privately invokable after restore.'
  policy="$(gcloud_json run services get-iam-policy "$VALIDATION_SERVICE" \
    --project "$PROJECT_ID" --region "$REGION" --format=json)"
  jq -e '[.bindings[]? | select(.role == "roles/run.invoker") | .members[]? |
    select(. == "allUsers" or . == "allAuthenticatedUsers")] | length == 0' \
    <<< "$policy" >/dev/null || fail 'Validation service IAM is public after restore.'
}

clear() {
  local service journal annotations
  journal="$(read_journal)" || fail 'No recovery journal exists.'
  require_journal_owner "$journal"
  jq -e '.phase == "verified" and (has("pending") | not) and (.resources | length == 0) and
    ((has("iam") | not) or .iam.added == false)' <<< "$journal" >/dev/null \
    || fail 'Journal cannot be cleared before verified restoration.'
  service="$(get_service)"
  test "$(jq -r --arg key "$journal_key" '.annotations[$key] // empty' <<< "$service")" = "$journal" \
    || fail 'Journal changed before clear.'
  annotations="$(jq -c --arg key "$journal_key" '(.annotations // {}) | del(.[$key])' <<< "$service")"
  patch_service "$service" annotations "$(jq -cn --argjson annotations "$annotations" '{annotations:$annotations}')"
  service="$(get_service)"
  jq -e --arg key "$journal_key" '((.annotations // {}) | has($key) | not)' <<< "$service" >/dev/null \
    || fail 'Journal clear read-back failed.'
}

require_context
case "${1:-}" in
  guard) guard ;;
  prepare) prepare ;;
  read) read_journal ;;
  pending|resource|phase|iam|iam-added|drop-resource|iam-cleared|clear-pending) mutate_journal "$@" ;;
  restore) restore ;;
  clear) clear ;;
  *) fail 'Usage: observability-service.sh guard|prepare|read|pending KIND TARGET|clear-pending KIND TARGET|resource KIND NAME|drop-resource KIND NAME|phase PHASE|iam PRINCIPAL EXISTED|iam-added|iam-cleared|restore|clear' ;;
esac

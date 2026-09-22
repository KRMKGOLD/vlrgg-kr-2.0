#!/usr/bin/env bash
# Exercises observability recovery helpers with local REST and gcloud stubs.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'if test "${KEEP_OBSERVABILITY_TEST_TMP:-false}" = true; then echo "$work_dir" >&2; else rm -rf "$work_dir"; fi' EXIT
mkdir -p "$work_dir/bin"

service_helper="$repo_root/.github/scripts/observability-service.sh"
policy_helper="$repo_root/.github/scripts/observability-policies.sh"
live_helper="$repo_root/.github/scripts/observability-live.sh"
cleanup_helper="$repo_root/.github/scripts/observability-cleanup.sh"
workflow="$repo_root/.github/workflows/deploy-server.yml"
preflight_script="$work_dir/preflight.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "PASS: $*"; }
expect_fail() {
  local stderr_file="$1"
  shift
  if "$@" > /dev/null 2> "$stderr_file"; then
    fail "command unexpectedly succeeded: $*"
  fi
}

cat > "$work_dir/http" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
method="$1"
url="$2"
body_file="${3:-}"
printf '%s\t%s\n' "$method" "$url" >> "$CASE_DIR/http-calls"

if [[ "$url" == https://run.googleapis.com/* ]]; then
  if [[ "$url" == */operations/* ]]; then
    printf '%s\n' '{"done":true}'
    exit
  fi
  if [[ "$url" == */revisions/* ]]; then
    cat "$CASE_DIR/revision.json"
    exit
  fi
  case "$method" in
    GET)
      count=0
      test ! -f "$CASE_DIR/service-get-count" || count="$(cat "$CASE_DIR/service-get-count")"
      count=$((count + 1))
      printf '%s\n' "$count" > "$CASE_DIR/service-get-count"
      if test -f "$CASE_DIR/mutate-on-service-get" && \
          test "$count" = "$(cat "$CASE_DIR/mutate-on-service-get")"; then
        if test -f "$CASE_DIR/drift-template"; then
          jq '.template.containers[0].image="image@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" | .etag="drift-etag"' \
            "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
        else
          jq '.annotations["vlrgg-observability-validation"] |=
            (fromjson | .phase="fault" | tojson) | .etag="drift-etag"' \
            "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
        fi
        mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
        rm "$CASE_DIR/mutate-on-service-get"
      fi
      cat "$CASE_DIR/service.json" ;;
    PATCH)
      if test -f "$CASE_DIR/conflict-once"; then
        rm "$CASE_DIR/conflict-once"
        jq '.etag = "external-etag"' "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
        mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
        echo 'etag conflict' >&2
        exit 1
      fi
      state_etag="$(jq -er '.etag' "$CASE_DIR/service.json")"
      body_etag="$(jq -er '.etag' "$body_file")"
      test "$state_etag" = "$body_etag" || { echo 'etag conflict' >&2; exit 1; }
      mask="${url##*updateMask=}"
      cp "$body_file" "$CASE_DIR/patch-$mask.json"
      case "$mask" in
        annotations) jq --slurpfile patch "$body_file" '.annotations=$patch[0].annotations | .etag="next-etag"' "$CASE_DIR/service.json" ;;
        traffic)
          traffic_revision="$(jq -er '.traffic[0].revision' "$body_file")"
          [[ "$traffic_revision" != projects/* ]] || {
            echo 'INVALID_ARGUMENT: traffic revision must be a short revision ID' >&2
            exit 1
          }
          jq --slurpfile patch "$body_file" '.traffic=$patch[0].traffic | .etag="next-etag"' "$CASE_DIR/service.json" ;;
        template) jq --slurpfile patch "$body_file" '.template=$patch[0].template | .etag="next-etag"' "$CASE_DIR/service.json" ;;
        *) echo 'unexpected update mask' >&2; exit 1 ;;
      esac > "$CASE_DIR/next.json"
      mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
      printf '%s\n' '{"name":"projects/test-project/locations/test-region/operations/local"}' ;;
    *) echo 'unexpected Cloud Run method' >&2; exit 1 ;;
  esac
  exit
fi

if [[ "$url" != *monitoring.googleapis.com* ]]; then
  echo 'unexpected HTTP endpoint' >&2
  exit 1
fi
if [[ "$url" == */notificationChannels/* ]]; then
  jq -n --arg name "${url#*monitoring.googleapis.com/v3/}" \
    '{name:$name,type:"email",enabled:true,verificationStatus:"VERIFIED"}'
  exit
fi
if [[ "$url" == */metricDescriptors/* ]]; then
  if [[ "$url" == *'run.googleapis.com%2Frequest_count' ]]; then
    printf '%s\n' '{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"Invalid metric name"}}' >&2
    exit 1
  fi
  [[ "$url" == */metricDescriptors/run.googleapis.com/request_count ]] || {
    echo 'unexpected metric descriptor path' >&2
    exit 1
  }
  if test -f "$CASE_DIR/missing-descriptor-label"; then
    printf '%s\n' '{"type":"run.googleapis.com/request_count","labels":[]}'
  else
    printf '%s\n' '{"type":"run.googleapis.com/request_count","labels":[{"key":"response_code_class"}]}'
  fi
  exit
fi
if [[ "$url" == *'/timeSeries?'* ]]; then
  if test -f "$CASE_DIR/missing-5xx-sample"; then printf '%s\n' '{"timeSeries":[]}'
  else printf '%s\n' '{"timeSeries":[{"metric":{"labels":{"response_code_class":"5xx"}},"points":[{"value":{"int64Value":"1"}}]}]}'; fi
  exit
fi
if [[ "$url" == *'/prometheus/api/v1/query?'* ]]; then
  response="$CASE_DIR/prometheus.json"
  [[ "$url" != *'response_code_class%3D%222xx%22'* || "$url" == *'response_code_class%3D%225xx%22'* ]] \
    || response="$CASE_DIR/prometheus-2xx.json"
  test -f "$response" || printf '%s\n' \
    '{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1,"0"]}]}}' \
    > "$response"
  cat "$response"
  exit
fi
collection=alertPolicies
[[ "$url" == *uptimeCheckConfigs* ]] && collection=uptimeCheckConfigs
inventory="$CASE_DIR/$collection.json"
test -f "$inventory" || printf '%s\n' "{\"$collection\":[]}" > "$inventory"
if test -f "$CASE_DIR/paginated" && [[ "$method" == GET && "$url" == *'?pageSize=1000' ]]; then
  jq '.nextPageToken="more"' "$inventory"
  exit
fi
case "$method" in
  GET)
    if [[ "$url" == *'?pageSize=1000' ]]; then
      cat "$inventory"
    else
      name="${url#*monitoring.googleapis.com/v3/}"
      jq -e --arg name "$name" --arg collection "$collection" '.[$collection][] | select(.name==$name)' "$inventory"
    fi ;;
  POST)
    test -n "$body_file"
    name="projects/test-project/$collection/created-$collection"
    jq --arg name "$name" '. + {name:$name}' "$body_file" > "$CASE_DIR/created.json"
    if test -f "$CASE_DIR/invalid-created-resource"; then
      jq '.validity={code:3,message:"invalid"}' "$CASE_DIR/created.json" > "$CASE_DIR/next-created.json"
      mv "$CASE_DIR/next-created.json" "$CASE_DIR/created.json"
    fi
    jq --slurpfile created "$CASE_DIR/created.json" --arg collection "$collection" \
      '.[$collection] += $created' "$inventory" > "$CASE_DIR/next.json"
    mv "$CASE_DIR/next.json" "$inventory"
    test ! -f "$CASE_DIR/post-response-loss" || { rm "$CASE_DIR/post-response-loss"; exit 1; }
    cat "$CASE_DIR/created.json" ;;
  PATCH)
    name="${url#*monitoring.googleapis.com/v3/}"
    name="${name%%\?*}"
    jq --arg name "$name" --arg collection "$collection" \
      '.[$collection] |= map(if .name==$name then .enabled=false else . end)' "$inventory" > "$CASE_DIR/next.json"
    mv "$CASE_DIR/next.json" "$inventory"
    printf '%s\n' '{}' ;;
  DELETE)
    name="${url#*monitoring.googleapis.com/v3/}"
    jq --arg name "$name" --arg collection "$collection" \
      '.[$collection] |= map(select(.name!=$name))' "$inventory" > "$CASE_DIR/next.json"
    mv "$CASE_DIR/next.json" "$inventory"
    printf '%s\n' '{}' ;;
  *) echo 'unexpected Monitoring method' >&2; exit 1 ;;
esac
STUB
chmod +x "$work_dir/http"

cat > "$work_dir/bin/gcloud" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$CASE_DIR/gcloud-calls"
member=
previous=
for argument in "$@"; do
  test "$previous" != --member || member="$argument"
  previous="$argument"
done
if test -f "$CASE_DIR/gcloud-denied"; then
  echo 'PERMISSION_DENIED secret@example.invalid https://secret.example.invalid token-SECRET project-secret' >&2
  exit 1
fi
case "$1 $2 ${3:-}" in
  'projects describe test-project') cat "$CASE_DIR/project.json" ;;
  'projects get-iam-policy test-project') cat "$CASE_DIR/project-iam-policy.json" ;;
  'run services get-iam-policy')
    if test -f "$CASE_DIR/public-iam"; then
      printf '%s\n' '{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}'
    else
      cat "$CASE_DIR/iam-policy.json"
    fi ;;
  'run services add-iam-policy-binding')
    test -n "$member"
    jq --arg member "$member" '
      .bindings += [{role:"roles/run.invoker",members:[$member]}]
    ' "$CASE_DIR/iam-policy.json" > "$CASE_DIR/next.json"
    mv "$CASE_DIR/next.json" "$CASE_DIR/iam-policy.json"
    printf '%s\n' '{}' ;;
  'run services remove-iam-policy-binding')
    test -n "$member"
    jq --arg member "$member" '
      .bindings |= map(.members |= map(select(.!=$member))) |
      .bindings |= map(select(.members|length>0))
    ' "$CASE_DIR/iam-policy.json" > "$CASE_DIR/next.json"
    mv "$CASE_DIR/next.json" "$CASE_DIR/iam-policy.json"
    printf '%s\n' '{}' ;;
  'run revisions describe')
    test ! -f "$CASE_DIR/gcloud-not-found" || { echo 'NOT_FOUND' >&2; exit 1; }
    cat "$CASE_DIR/owned-revision.json" ;;
  'run services describe') cat "$CASE_DIR/gcloud-service.json" ;;
  'run revisions list') cat "$CASE_DIR/gcloud-revisions.json" ;;
  'run revisions delete') printf '%s\n' '{}' ;;
  'artifacts docker images')
    if test "${4:-}" = list; then cat "$CASE_DIR/gcloud-images.json"
    else printf '%s\n' '{}'; fi ;;
  *) echo 'unexpected gcloud call' >&2; exit 1 ;;
esac
STUB
chmod +x "$work_dir/bin/gcloud"

cat > "$work_dir/private-http" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
method="$1"; base="$2"; path="$3"; output="$4"
printf '%s\t%s\t%s\n' "$method" "$base" "$path" >> "$CASE_DIR/private-calls"
case "$method $path" in
  'GET /health') printf '%s\n' '{"status":"ok"}' > "$output"; printf 200 ;;
  'POST /__observability/health/fail'|'POST /__observability/health/restore')
    printf '%s\n' '{"status":"configured"}' > "$output"; printf 200 ;;
  'POST /__observability/exit') printf '%s\n' '{"status":"accepted"}' > "$output"; printf 202 ;;
  *) printf '%s\n' '{}' > "$output"; printf 500 ;;
esac
STUB
chmod +x "$work_dir/private-http"

cat > "$work_dir/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$CASE_DIR/gh-calls"
case "$*" in
  "api repos/test-repository/git/ref/heads/main --jq .object.sha")
    printf '%s\n' test-sha ;;
  "api repos/test-repository/actions/workflows/ci.yml/runs?event=push&branch=main&head_sha=test-sha&per_page=1")
    printf '%s\n' '{"workflow_runs":[{"head_sha":"test-sha","head_branch":"main","event":"push","conclusion":"success"}]}' ;;
  *)
    echo 'unexpected gh call' >&2
    exit 1 ;;
esac
STUB
chmod +x "$work_dir/bin/gh"

awk '
  /^      - name: Require enabled deployment and successful CI for this main commit$/ { step = 1; next }
  step && /^      - name:/ { exit }
  step && /^        run: \|$/ { code = 1; next }
  code { sub(/^          /, ""); print }
' "$workflow" > "$preflight_script"
test -s "$preflight_script"
bash -n "$preflight_script"
export PATH="$work_dir/bin:$PATH"
export OBSERVABILITY_DEADLINE_EPOCH="$(($(date +%s) + 3600))"
unset GOOGLE_APPLICATION_CREDENTIALS CLOUDSDK_AUTH_ACCESS_TOKEN CLOUDSDK_CORE_PROJECT || true

new_case() {
  CASE_DIR="$work_dir/$1"
  export CASE_DIR
  mkdir -p "$CASE_DIR"
  : > "$CASE_DIR/http-calls"
  : > "$CASE_DIR/gcloud-calls"
  : > "$CASE_DIR/private-calls"
  local revision='projects/test-project/locations/test-region/services/vlrgg-query-check/revisions/baseline'
  local revision_id="${revision##*/}"
  jq -n --arg revision "$revision_id" '{
    name:"projects/test-project/locations/test-region/services/vlrgg-query-check",
    etag:"initial-etag",annotations:{},reconciling:false,
    terminalCondition:{type:"Ready",state:"CONDITION_SUCCEEDED"},
    traffic:[{percent:100,revision:$revision}],
    template:{containers:[{name:"server",image:"image@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",env:[{name:"SAFE",value:"true"}]}]},
    invokerIamDisabled:false,ingress:"INGRESS_TRAFFIC_ALL"
  }' > "$CASE_DIR/service.json"
  jq -n --arg revision "$revision" '{
    name:$revision,conditions:[{type:"Ready",state:"CONDITION_SUCCEEDED"}],
    containers:[{name:"server",image:"image@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",env:[{name:"SAFE",value:"true"}]}]
  }' > "$CASE_DIR/revision.json"
  printf '%s\n' '{"alertPolicies":[]}' > "$CASE_DIR/alertPolicies.json"
  printf '%s\n' '{"uptimeCheckConfigs":[]}' > "$CASE_DIR/uptimeCheckConfigs.json"
  printf '%s\n' '{"status":{"traffic":[]}}' > "$CASE_DIR/gcloud-service.json"
  printf '%s\n' '[]' > "$CASE_DIR/gcloud-revisions.json"
  printf '%s\n' '[]' > "$CASE_DIR/gcloud-images.json"
  printf '%s\n' '{"bindings":[]}' > "$CASE_DIR/iam-policy.json"
  printf '%s\n' '{"projectId":"test-project","projectNumber":"123"}' > "$CASE_DIR/project.json"
  printf '%s\n' '{"bindings":[{"role":"roles/monitoring.notificationServiceAgent","members":["serviceAccount:service-123@gcp-sa-monitoring-notification.iam.gserviceaccount.com"]}]}' \
    > "$CASE_DIR/project-iam-policy.json"
}

service() {
  env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
    OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" \
    OBSERVABILITY_HOST=vlrgg-query-check-test.run.app OBSERVABILITY_REVISION=validation-r123-1 \
    "$service_helper" "$@"
}

policy() {
  env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
    VALIDATION_SERVICE=vlrgg-query-check \
    OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" \
    OBSERVABILITY_HOST=vlrgg-query-check-test.run.app OBSERVABILITY_REVISION=validation-r123-1 \
    OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
    OBSERVABILITY_CONFIRMED_RECEIVERS=true "$policy_helper" "$@"
}

live() {
  local function="$1"
  shift
  env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
    SERVICE_NAME=vlrgg-query-check VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
    OBSERVABILITY_HTTP="$work_dir/http" \
    OBSERVABILITY_PRIVATE_HTTP="$work_dir/private-http" OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 \
    OBSERVABILITY_DEADLINE_EPOCH="$OBSERVABILITY_DEADLINE_EPOCH" \
    SMOKE_URL=https://vlrgg-query-check-test.run.app SMOKE_ID_TOKEN=stub-token \
    RUNNER_TEMP="$CASE_DIR" GITHUB_STEP_SUMMARY="$CASE_DIR/summary" CASE_DIR="$CASE_DIR" \
    bash -c 'source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"; "$2" "${@:3}"' \
      _ "$live_helper" "$function" "$@"
}

prepare_live_case() {
  local revision_name=vlrgg-query-check-o123-1
  local revision="projects/test-project/locations/test-region/services/vlrgg-query-check/revisions/$revision_name"
  service prepare >/dev/null
  service pending revision "$revision_name" >/dev/null
  service resource revision "$revision" >/dev/null
  service phase fault >/dev/null
  jq -n --arg url https://vlrgg-query-check-test.run.app --arg revision "$revision_name" '{
    metadata:{annotations:{"run.googleapis.com/invoker-iam-disabled":"false"}},
    status:{url:$url,traffic:[{percent:100,revisionName:$revision}]}
  }' > "$CASE_DIR/gcloud-service.json"
}

run_preflight_case() {
  local operation="$1" enabled="$2" expected="$3" result=0
  CASE_DIR="$work_dir/preflight-$operation-${enabled:-unset}"
  export CASE_DIR
  mkdir -p "$CASE_DIR"
  : > "$CASE_DIR/gh-calls"
  if test "$enabled" = unset; then
    env -u DEPLOY_ENABLED PATH="$work_dir/bin:$PATH" OPERATION="$operation" PROJECT_ID=test-project \
      WIF_PROVIDER=test-provider DEPLOY_SERVICE_ACCOUNT=deploy@example.invalid \
      RUNTIME_SERVICE_ACCOUNT=runtime@example.invalid GITHUB_SHA=test-sha \
      GITHUB_REPOSITORY=test-repository GH_TOKEN=test-token \
      bash --noprofile --norc -e -o pipefail "$preflight_script" \
      > "$CASE_DIR/stdout" 2> "$CASE_DIR/stderr" || result=$?
  else
    env PATH="$work_dir/bin:$PATH" OPERATION="$operation" DEPLOY_ENABLED="$enabled" PROJECT_ID=test-project \
      WIF_PROVIDER=test-provider DEPLOY_SERVICE_ACCOUNT=deploy@example.invalid \
      RUNTIME_SERVICE_ACCOUNT=runtime@example.invalid GITHUB_SHA=test-sha \
      GITHUB_REPOSITORY=test-repository GH_TOKEN=test-token \
      bash --noprofile --norc -e -o pipefail "$preflight_script" \
      > "$CASE_DIR/stdout" 2> "$CASE_DIR/stderr" || result=$?
  fi
  if test "$expected" = pass; then
    test "$result" = 0 || fail "preflight rejected $operation with enable=$enabled"
    test "$(wc -l < "$CASE_DIR/gh-calls" | tr -d ' ')" = 2 \
      || fail "preflight skipped required GitHub checks for $operation with enable=$enabled"
  else
    test "$result" != 0 || fail "preflight accepted $operation with enable=$enabled"
    test ! -s "$CASE_DIR/gh-calls" \
      || fail "rejected preflight reached GitHub checks for $operation with enable=$enabled"
  fi
}

for operation in deploy observability-validate; do
  run_preflight_case "$operation" true pass
  run_preflight_case "$operation" false reject
  run_preflight_case "$operation" unset reject
done
for enabled in true false unset; do
  run_preflight_case observability-restore "$enabled" pass
  run_preflight_case unknown "$enabled" reject
done
pass 'workflow preflight allows disabled restore only and retains operation, main SHA, and CI checks'

new_case journal
journal="$(service prepare)"
jq -e '.phase=="prepared" and .resources==[]' <<< "$journal" >/dev/null
service pending image 'test-region-docker.pkg.dev/test-project/repo/query-observability:sha-123-1' >/dev/null
touch "$CASE_DIR/conflict-once"
expect_fail "$CASE_DIR/conflict.stderr" service resource image \
  'test-region-docker.pkg.dev/test-project/repo/query-observability@sha256:owned'
jq -e '.pending.kind=="image" and (.resources|length)==0' <<< "$(service read)" >/dev/null
service resource image 'test-region-docker.pkg.dev/test-project/repo/query-observability@sha256:owned' >/dev/null
jq -e '(has("pending")|not) and .resources[0].kind=="image"' <<< "$(service read)" >/dev/null
pass 'journal CAS conflict retains write-ahead intent'

valid_service="$CASE_DIR/service-valid.json"
cp "$CASE_DIR/service.json" "$valid_service"
oversized="$(LC_ALL=C tr '\0' x < /dev/zero | head -c 8300 || true)"
jq --arg value "$oversized" '.annotations["vlrgg-observability-validation"]=$value' \
  "$valid_service" > "$CASE_DIR/service.json"
expect_fail "$CASE_DIR/oversized.stderr" service read
grep -q 'Journal exceeds 8 KiB' "$CASE_DIR/oversized.stderr"
jq '.annotations["vlrgg-observability-validation"]="{\"v\":2}"' \
  "$valid_service" > "$CASE_DIR/service.json"
expect_fail "$CASE_DIR/schema.stderr" service read
grep -q 'Invalid journal schema' "$CASE_DIR/schema.stderr"
pass 'journal size and schema fail closed'

new_case prepare-drift
touch "$CASE_DIR/drift-template"
printf '%s\n' 2 > "$CASE_DIR/mutate-on-service-get"
expect_fail "$CASE_DIR/drift.stderr" service prepare
grep -q 'changed before journal prepare' "$CASE_DIR/drift.stderr"
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'prepare drift wrote a journal'
jq -e '.annotations=={}' "$CASE_DIR/service.json" >/dev/null
pass 'prepare rejects template drift before journal CAS'

new_case prepare-generated-container-name
jq 'del(.template.containers[0].name) | .template.containers[0].ports=[{containerPort:8080}]' "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
jq '.containers[0].ports=[{containerPort:8080}]' "$CASE_DIR/revision.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/revision.json"
journal="$(service prepare)"
jq -e '.phase=="prepared" and (.baselineTemplateHash|test("^[0-9a-f]{64}$"))' <<< "$journal" >/dev/null
service restore >/dev/null
jq -e '.template.containers[0].name=="server"' "$CASE_DIR/service.json" >/dev/null
jq -e '.phase=="restoring"' <<< "$(service read)" >/dev/null
pass 'prepare accepts a missing single-container name and restore keeps the immutable baseline'

new_case prepare-both-container-names-absent
jq 'del(.template.containers[0].name)' "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
jq 'del(.containers[0].name)' "$CASE_DIR/revision.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/revision.json"
service prepare >/dev/null
service restore >/dev/null
pass 'prepare and restore preserve an unnamed baseline'

new_case prepare-explicit-container-name-mismatch
jq '.template.containers[0].name="other"' "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
expect_fail "$CASE_DIR/name-mismatch.stderr" service prepare
grep -q 'template differs' "$CASE_DIR/name-mismatch.stderr"
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'explicit container name mismatch wrote a journal'
pass 'prepare retains explicit container name mismatch protection'

new_case prepare-multi-container-missing-name
jq '.template.containers[0] |= del(.name) |
  .template.containers += [{name:"sidecar",image:"image@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}]' \
  "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
jq '.containers += [{name:"sidecar",image:"image@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}]' \
  "$CASE_DIR/revision.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/revision.json"
expect_fail "$CASE_DIR/multi-container.stderr" service prepare
grep -q 'template differs' "$CASE_DIR/multi-container.stderr"
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'multi-container missing name wrote a journal'
pass 'prepare retains multi-container name safeguards'

new_case restore-order
service prepare >/dev/null
: > "$CASE_DIR/http-calls"
service restore >/dev/null
traffic_line="$(grep -n 'updateMask=traffic' "$CASE_DIR/http-calls" | head -n1 | cut -d: -f1)"
template_line="$(grep -n 'updateMask=template' "$CASE_DIR/http-calls" | head -n1 | cut -d: -f1)"
test -n "$traffic_line" && test -n "$template_line" && test "$template_line" -gt "$traffic_line" \
  || fail 'restore did not pin baseline traffic before restoring template'
jq -e '.phase=="restoring"' <<< "$(service read)" >/dev/null
jq -e '.traffic[0].revision == "baseline"' "$CASE_DIR/patch-traffic.json" >/dev/null \
  || fail 'restore sent a full revision resource to the traffic patch'
jq -e '.traffic[0].revision == "baseline"' "$CASE_DIR/service.json" >/dev/null \
  || fail 'traffic read-back did not retain the short revision ID'
jq -e --arg revision 'projects/test-project/locations/test-region/services/vlrgg-query-check/revisions/baseline' \
  '.annotations["vlrgg-observability-validation"] | fromjson | .baselineRevision == $revision' \
  "$CASE_DIR/service.json" >/dev/null \
  || fail 'journal read-back lost the full baseline revision resource'
pass 'restore pins traffic before template and verifies journal'

new_case restore-hash
service prepare >/dev/null
jq '.containers[0].image="image@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"' "$CASE_DIR/revision.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/revision.json"
: > "$CASE_DIR/http-calls"
expect_fail "$CASE_DIR/hash.stderr" service restore
grep -q 'template hash changed' "$CASE_DIR/hash.stderr"
! grep -q 'updateMask=traffic' "$CASE_DIR/http-calls" || fail 'hash mismatch changed traffic'
pass 'baseline hash mismatch stops before traffic mutation'

new_case run-owner
service prepare >/dev/null
expect_fail "$CASE_DIR/owner.stderr" env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=999-1 OBSERVABILITY_HTTP="$work_dir/http" \
  "$service_helper" phase fault
grep -qi 'owner\|run' "$CASE_DIR/owner.stderr"
: > "$CASE_DIR/http-calls"
expect_fail "$CASE_DIR/restore-owner.stderr" env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=999-1 OBSERVABILITY_HTTP="$work_dir/http" \
  "$service_helper" restore
! grep -q 'updateMask=traffic' "$CASE_DIR/http-calls" || fail 'foreign restore changed traffic'
expect_fail "$CASE_DIR/clear-owner.stderr" env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=999-1 OBSERVABILITY_HTTP="$work_dir/http" \
  "$service_helper" clear
pass 'journal mutation, restore, and clear reject another validation run'

new_case clear-cas
service prepare >/dev/null
service restore >/dev/null
service phase verified >/dev/null
rm -f "$CASE_DIR/service-get-count"
printf '%s\n' 2 > "$CASE_DIR/mutate-on-service-get"
expect_fail "$CASE_DIR/clear-cas.stderr" service clear
grep -q 'Journal changed before clear' "$CASE_DIR/clear-cas.stderr"
jq -e '.annotations["vlrgg-observability-validation"]|fromjson|.phase=="fault"' \
  "$CASE_DIR/service.json" >/dev/null
pass 'clear compares the current journal before removing it'

new_case pending-guard
service prepare >/dev/null
service pending image 'test-region-docker.pkg.dev/test-project/repo/query-observability:sha-123-1' >/dev/null
expect_fail "$CASE_DIR/pending-overwrite.stderr" service pending revision vlrgg-query-check-o123-1
expect_fail "$CASE_DIR/pending-phase.stderr" service phase fault
expect_fail "$CASE_DIR/pending-resource.stderr" service resource policy projects/test-project/alertPolicies/unrelated
jq -e '.pending.kind=="image" and (.resources|length)==0 and .phase=="prepared"' \
  <<< "$(service read)" >/dev/null
pass 'pending intent cannot be overwritten or cleared by unrelated mutations'

new_case invalid-iam
service prepare >/dev/null
jq '.annotations["vlrgg-observability-validation"] |=
  (fromjson | .iam={principal:"service-123@gcp-sa-monitoring-notification.iam.gserviceaccount.com",existed:true,added:true} | tojson)' \
  "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
expect_fail "$CASE_DIR/iam.stderr" service read
grep -q 'Invalid journal schema' "$CASE_DIR/iam.stderr"
pass 'journal rejects contradictory IAM ownership state'

new_case prepare-public-iam
touch "$CASE_DIR/public-iam"
expect_fail "$CASE_DIR/public-iam.stderr" service prepare
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'public IAM baseline wrote a journal'
pass 'prepare rejects public invoker IAM'

new_case prepare-iam-bypass
jq '.invokerIamDisabled=true' "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
expect_fail "$CASE_DIR/iam-bypass.stderr" service prepare
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'IAM bypass baseline wrote a journal'
pass 'prepare rejects disabled invoker IAM checks'

new_case prepare-unknown-template
jq '.template.containers[0].futureField="unsafe"' "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
jq '.containers[0].futureField="unsafe"' "$CASE_DIR/revision.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/revision.json"
expect_fail "$CASE_DIR/unknown-template.stderr" service prepare
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'unknown template field wrote a journal'
pass 'prepare rejects unknown template fields instead of restoring lossily'

new_case prepare-system-annotation
jq '.template.annotations={"run.googleapis.com/future-setting":"unsafe"}' \
  "$CASE_DIR/service.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/service.json"
jq '.annotations={"run.googleapis.com/future-setting":"unsafe"}' \
  "$CASE_DIR/revision.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/revision.json"
expect_fail "$CASE_DIR/system-annotation.stderr" service prepare
! grep -q $'^PATCH\t' "$CASE_DIR/http-calls" || fail 'unknown system annotation wrote a journal'
pass 'prepare rejects unknown system template annotations'

new_case policy-render
five_x="$(policy render 5xx)"
jq -e '
  (.conditions[0] | has("conditionThreshold") | not) and
  (.alertStrategy | has("autoClose") | not) and
  (.conditions[0].conditionPrometheusQueryLanguage.query | contains("{\"run.googleapis.com/request_count\"")) and
  (.conditions[0].conditionPrometheusQueryLanguage.query | contains("response_code_class=\"5xx\"}[5m]")) and
  (.conditions[0].conditionPrometheusQueryLanguage.query | contains("response_code_class=\"2xx\"}[5m]")) and
  (.conditions[0].conditionPrometheusQueryLanguage.query | contains(" or 0 * ")) and
  (.conditions[0].conditionPrometheusQueryLanguage.query | endswith(") >= 3")) and
  .conditions[0].conditionPrometheusQueryLanguage.duration=="0s" and
  .conditions[0].conditionPrometheusQueryLanguage.evaluationInterval=="30s" and
  .alertStrategy.notificationPrompts==["OPENED","CLOSED"]
' <<< "$five_x" >/dev/null
! grep -q 'vector(0)' <<< "$five_x" || fail '5xx policy used unconditional zero'
uptime="$(env PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" \
  OBSERVABILITY_HOST=vlrgg-query-check-test.run.app OBSERVABILITY_REVISION=validation-r123-1 \
  MONITORING_SERVICE_AGENT=service-123@gcp-sa-monitoring-notification.iam.gserviceaccount.com \
  "$policy_helper" render uptime)"
jq -e '
  .period=="300s" and .timeout=="10s" and
  .selectedRegions==["USA_IOWA","EUROPE","ASIA_PACIFIC"] and
  .httpCheck.acceptedResponseStatusCodes==[{"statusValue":200}] and
  .contentMatchers==[{"content":"^\\s*\\{\\s*\"status\"\\s*:\\s*\"ok\"\\s*\\}\\s*$","matcher":"MATCHES_REGEX"}]
' <<< "$uptime" >/dev/null
pass 'policy render shares native DELTA PromQL and keeps exact health body regex'

new_case policy-promql-query
for value in 2 3 0 2.75 1e3 4.25e-2; do
  jq -n --arg value "$value" '{status:"success",data:{resultType:"vector",result:[{metric:{},value:[1,$value]}]}}' \
    > "$CASE_DIR/prometheus.json"
  test "$(policy query-5xx-count 1700000000)" = "$value"
done
jq -e '.[0]==1 and .[1]=="4.25e-2"' <<< "$(policy query-5xx-sample 1700000000)" >/dev/null
for bad in empty multiple nan infinity overflow overflow-mantissa negative; do
  case "$bad" in
    empty) result='[]' ;;
    multiple) result='[{"metric":{},"value":[1,"0"]},{"metric":{},"value":[1,"0"]}]' ;;
    nan) result='[{"metric":{},"value":[1,"NaN"]}]' ;;
    infinity) result='[{"metric":{},"value":[1,"+Inf"]}]' ;;
    overflow) result='[{"metric":{},"value":[1,"1e999"]}]' ;;
    overflow-mantissa) result='[{"metric":{},"value":[1,"1.7976931348623159e308"]}]' ;;
    negative) result='[{"metric":{},"value":[1,"-1"]}]' ;;
  esac
  printf '{"status":"success","data":{"resultType":"vector","result":%s}}\n' "$result" \
    > "$CASE_DIR/prometheus.json"
  expect_fail "$CASE_DIR/promql-$bad.stderr" policy query-5xx-count 1700000000
done
grep -Fq '0%20%2A%20sum%28increase' "$CASE_DIR/http-calls"
pass 'PromQL numeric query accepts finite samples and rejects missing or ambiguous telemetry'

new_case policy-idempotent
service prepare >/dev/null
first="$(policy ensure 5xx)"
second="$(policy ensure 5xx)"
test "$first" = "$second"
test "$(grep -c $'^POST\t' "$CASE_DIR/http-calls")" = 1
grep -Fq $'\thttps://monitoring.googleapis.com/v3/projects/test-project/metricDescriptors/run.googleapis.com/request_count' \
  "$CASE_DIR/http-calls"
! grep -Eq '/metricDescriptors/[^?]*%2Frequest_count' "$CASE_DIR/http-calls" \
  || fail 'metric descriptor request used an encoded metric name'
pass 'policy ensure is idempotent'

jq '.alertPolicies[0].conditions[0].conditionPrometheusQueryLanguage.query += " drift"' \
  "$CASE_DIR/alertPolicies.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/alertPolicies.json"
before_writes="$(grep -Ec $'^(POST|PATCH|DELETE)\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls")"
expect_fail "$CASE_DIR/array-drift.stderr" policy ensure 5xx
after_writes="$(grep -Ec $'^(POST|PATCH|DELETE)\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls")"
test "$before_writes" = "$after_writes" || fail 'array drift triggered a Monitoring mutation'
pass 'policy ensure rejects existing resources with array drift'

new_case policy-response-loss
service prepare >/dev/null
touch "$CASE_DIR/post-response-loss"
expect_fail "$CASE_DIR/response-loss.stderr" policy ensure 5xx
jq -e '.pending.kind=="5xx" and (.resources|length)==0' <<< "$(service read)" >/dev/null
recovered_policy="$(policy ensure 5xx)"
jq -e --arg name "$recovered_policy" '
  (has("pending")|not) and any(.resources[]; .kind=="policy" and .name==$name)
' <<< "$(service read)" >/dev/null
test "$(grep -c $'^POST\t' "$CASE_DIR/http-calls")" = 1
pass 'policy create response loss recovers and journals the owned resource'

new_case policy-create-readback
service prepare >/dev/null
touch "$CASE_DIR/invalid-created-resource"
expect_fail "$CASE_DIR/invalid-created.stderr" policy ensure 5xx
jq -e '.pending.kind=="5xx" and (.resources|length)==0' <<< "$(service read)" >/dev/null
pass 'new Monitoring resources are read back and rejected before journaling when provider validity fails'

new_case policy-kind
service prepare >/dev/null
jq -n '{alertPolicies:[{
  name:"projects/test-project/alertPolicies/other-kind",
  userLabels:{managed_by:"issue122-validation",validation_run:"123_1",resource_kind:"uptime_policy"}
}]}' > "$CASE_DIR/alertPolicies.json"
created="$(policy ensure 5xx)"
test "$created" = 'projects/test-project/alertPolicies/created-alertPolicies'
test "$(jq '.alertPolicies|length' "$CASE_DIR/alertPolicies.json")" = 2
pass 'policy kind collision creates a distinct owned resource'

new_case policy-page
service prepare >/dev/null
touch "$CASE_DIR/paginated"
expect_fail "$CASE_DIR/page.stderr" policy ensure 5xx
grep -q 'bounded page' "$CASE_DIR/page.stderr"
! grep -q $'^POST\t' "$CASE_DIR/http-calls" || fail 'paginated inventory created a resource'
pass 'policy inventory pagination fails closed'

new_case policy-live-label-proof
service prepare >/dev/null
touch "$CASE_DIR/missing-descriptor-label"
expect_fail "$CASE_DIR/descriptor.stderr" policy ensure 5xx
! grep -q $'^POST\t' "$CASE_DIR/http-calls" || fail 'missing metric label created a policy'
rm "$CASE_DIR/missing-descriptor-label"
touch "$CASE_DIR/missing-5xx-sample"
expect_fail "$CASE_DIR/sample.stderr" policy ensure 5xx
! grep -q $'^POST\t' "$CASE_DIR/http-calls" || fail 'missing 5xx sample created a policy'
pass '5xx policy requires descriptor label and an actual matching sample'

new_case policy-log-input
valid_log="$(env PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
  SYSTEM_LOG_NAME=projects/test-project/logs/run.googleapis.com%2Fvarlog%2Fsystem \
  SYSTEM_LOG_SIGNATURE='Container called exit(42).' "$policy_helper" render log)"
jq -e '.conditions[0].conditionMatchedLog.filter|contains("Container called exit(42).")' \
  <<< "$valid_log" >/dev/null
jq -e '.conditions[0].conditionMatchedLog.filter|contains("textPayload=\"Container called exit(42).\"")' \
  <<< "$valid_log" >/dev/null
expect_fail "$CASE_DIR/cross-project.stderr" env PROJECT_ID=test-project REGION=test-region \
  SERVICE_NAME=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
  SYSTEM_LOG_NAME=projects/other-project/logs/system SYSTEM_LOG_SIGNATURE='exit 42' \
  "$policy_helper" render log
expect_fail "$CASE_DIR/filter-injection.stderr" env PROJECT_ID=test-project REGION=test-region \
  SERVICE_NAME=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
  SYSTEM_LOG_NAME=projects/test-project/logs/system SYSTEM_LOG_SIGNATURE='exit" OR true' \
  "$policy_helper" render log
pass 'log policy rejects cross-project names and filter metacharacters'

principal='service-123@gcp-sa-monitoring-notification.iam.gserviceaccount.com'
member="serviceAccount:$principal"

new_case uptime-iam-existing
service prepare >/dev/null
jq -n --arg member "$member" '{bindings:[{role:"roles/run.invoker",members:[$member]}]}' \
  > "$CASE_DIR/iam-policy.json"
policy ensure uptime >/dev/null
jq -e '.iam.principal==$principal and .iam.existed==true and .iam.added==false' \
  --arg principal "$principal" <<< "$(service read)" >/dev/null
! grep -q '^run services add-iam-policy-binding' "$CASE_DIR/gcloud-calls" \
  || fail 'preexisting Monitoring invoker binding was added again'
pass 'preexisting Monitoring invoker binding is preserved and journaled'

new_case uptime-iam-added
service prepare >/dev/null
policy ensure uptime >/dev/null
jq -e '.iam.existed==false and .iam.added==true' <<< "$(service read)" >/dev/null
grep -q '^run services add-iam-policy-binding' "$CASE_DIR/gcloud-calls"
env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore
env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
jq -e --arg member "$member" '
  all(.bindings[]?|select(.role=="roles/run.invoker")|.members[]?; .!=$member)
' "$CASE_DIR/iam-policy.json" >/dev/null
jq -e '.iam.added==false' <<< "$(service read)" >/dev/null
pass 'run-added Monitoring invoker binding is removed after cleanup'

new_case uptime-iam-runner-loss
service prepare >/dev/null
service pending iam "$principal" >/dev/null
service iam "$principal" false >/dev/null
service pending iam-add "$principal" >/dev/null
jq -n --arg member "$member" '{bindings:[{role:"roles/run.invoker",members:[$member]}]}' \
  > "$CASE_DIR/iam-policy.json"
service restore >/dev/null
env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
jq -e '.iam.added==false and (has("pending")|not)' <<< "$(service read)" >/dev/null
pass 'cleanup recovers IAM add response loss and removes only the run-added binding'

new_case uptime-iam-conditional
service prepare >/dev/null
jq -n --arg member "$member" '{bindings:[{
  role:"roles/run.invoker",members:[$member],condition:{title:"shared",expression:"true"}
}]}' > "$CASE_DIR/iam-policy.json"
expect_fail "$CASE_DIR/conditional.stderr" policy ensure uptime
! grep -q '^run services add-iam-policy-binding' "$CASE_DIR/gcloud-calls" \
  || fail 'conditional IAM binding was mutated'
! grep -q $'^POST\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'conditional IAM binding allowed uptime creation'
pass 'conditional Monitoring invoker binding fails closed'

new_case uptime-iam-foreign-project
service prepare >/dev/null
jq '.bindings[0].members=["serviceAccount:service-999@gcp-sa-monitoring-notification.iam.gserviceaccount.com"]' \
  "$CASE_DIR/project-iam-policy.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/project-iam-policy.json"
expect_fail "$CASE_DIR/foreign-agent.stderr" policy ensure uptime
! grep -q '^run services add-iam-policy-binding' "$CASE_DIR/gcloud-calls" \
  || fail 'foreign-project Monitoring service agent received an IAM binding'
! grep -q $'^POST\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'foreign-project Monitoring service agent allowed uptime creation'
pass 'derived Monitoring service agent must hold the exact unconditional project role'

new_case uptime-iam-mismatched-live-project
service prepare >/dev/null
printf '%s\n' '{"projectId":"test-project","projectNumber":"999"}' > "$CASE_DIR/project.json"
expect_fail "$CASE_DIR/live-project-number.stderr" policy ensure uptime
! grep -q '^run services add-iam-policy-binding' "$CASE_DIR/gcloud-calls" \
  || fail 'mismatched live project number received an IAM binding'
! grep -q $'^POST\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'mismatched live project number allowed uptime creation'
pass 'Monitoring service agent is derived from live project inventory before IAM mutation'

new_case live-target-guard
prepare_live_case
live guard_target
grep -qx 'projects describe test-project --format=json' "$CASE_DIR/gcloud-calls"
grep -qx 'projects get-iam-policy test-project --format=json' "$CASE_DIR/gcloud-calls"
grep -qx 'run services describe vlrgg-query-check --project test-project --region test-region --format=json' \
  "$CASE_DIR/gcloud-calls"
pass 'live target guard proves the journal revision, private IAM, project number, and service-agent role'

printf '%s\n' '{"projectId":"other-project","projectNumber":"999"}' > "$CASE_DIR/project.json"
expect_fail "$CASE_DIR/live-project.stderr" live guard_target
test ! -s "$CASE_DIR/private-calls" || fail 'mismatched project number reached a private endpoint'
pass 'live target guard rejects project inventory that does not identify the configured project'

new_case live-sanitized-provider-failure
prepare_live_case
touch "$CASE_DIR/gcloud-denied"
expect_fail "$CASE_DIR/live-provider.stderr" live guard_target
! grep -Eq 'secret@example|secret\.example|token-SECRET|project-secret' "$CASE_DIR/live-provider.stderr" \
  || fail 'live driver leaked protected provider stderr'
pass 'live driver exposes only fixed errors when provider inventory fails'

new_case live-api-provider-errors
if env -u OBSERVABILITY_PROVIDER_HTTP CASE_DIR="$CASE_DIR" bash -c '
  source "$1"; PROJECT_ID=test-project; export PROJECT_ID; evidence="$CASE_DIR/evidence-http403"; mkdir -p "$evidence"
  gcloud() { printf "%s\n" "token-SECRET"; }
  curl() {
    printf "%s\n" "$*" > "$CASE_DIR/curl-args"
    local output=; while test "$#" -gt 0; do
      if test "$1" = --output; then output="$2"; shift 2; else shift; fi
    done
    printf "%s\n" "{\"error\":{\"code\":403,\"message\":\"SECRET_BODY secret@example.invalid\",\"status\":\"PERMISSION_DENIED\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"SERVICE_DISABLED\",\"domain\":\"secret.example.invalid\",\"metadata\":{\"consumer\":\"projects/project-secret\",\"account\":\"token-SECRET\",\"url\":\"https://secret.example.invalid\"}},{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"HOSTILE_UNKNOWN_REASON\",\"domain\":\"secret.example.invalid\",\"metadata\":{\"project\":\"project-secret\"}}]}}" > "$output"
    printf "%s" 403
    printf "%s\n" "provider secret@example.invalid https://secret.example.invalid body=SECRET_BODY token-SECRET project-secret" >&2
    return 22
  }
  api GET "https://monitoring.googleapis.com/v3/projects/test-project/alerts?pageSize=1" "$evidence/alerts.json"
' _ "$live_helper" > "$CASE_DIR/api-http403.stdout" 2> "$CASE_DIR/api-http403.stderr"; then
  fail 'provider HTTP 403 unexpectedly succeeded'
fi
if grep -Fxq 'Observability live validation failed: Provider request failed: monitoring.alerts.list (HTTP 403, curl 22).' \
  "$CASE_DIR/api-http403.stderr"; then
  fail 'recognized provider reason was not classified'
fi
grep -Fxq 'Observability live validation failed: Provider request failed: monitoring.alerts.list (HTTP 403, curl 22, reason SERVICE_DISABLED).' \
  "$CASE_DIR/api-http403.stderr"
! grep -Eq 'secret@example|secret\.example|SECRET_BODY|token-SECRET|project-secret' \
  "$CASE_DIR/api-http403.stdout" "$CASE_DIR/api-http403.stderr" \
  || fail 'provider HTTP 403 leaked protected request data'
grep -Eq 'secret@example|secret\.example|SECRET_BODY|token-SECRET|project-secret' \
  "$CASE_DIR/evidence-http403/alerts.json"
grep -Fq -- '--fail-with-body' "$CASE_DIR/curl-args"
grep -Fq -- '--header X-Goog-User-Project: test-project' "$CASE_DIR/curl-args"

if env -u OBSERVABILITY_PROVIDER_HTTP CASE_DIR="$CASE_DIR" bash -c '
  source "$1"; PROJECT_ID=test-project; export PROJECT_ID; evidence="$CASE_DIR/evidence-unknown"; mkdir -p "$evidence"
  gcloud() { printf "%s\n" "token-SECRET"; }
  curl() {
    local output=; while test "$#" -gt 0; do
      if test "$1" = --output; then output="$2"; shift 2; else shift; fi
    done
    printf "%s\n" "{\"error\":{\"message\":\"SECRET_BODY secret@example.invalid\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"HOSTILE_UNKNOWN_REASON\",\"domain\":\"secret.example.invalid\",\"metadata\":{\"project\":\"project-secret\",\"account\":\"token-SECRET\"}}]}}" > "$output"
    printf "%s" 403
    return 22
  }
  api GET "https://monitoring.googleapis.com/v3/projects/test-project/alerts?pageSize=1" "$evidence/alerts.json"
' _ "$live_helper" > "$CASE_DIR/api-unknown.stdout" 2> "$CASE_DIR/api-unknown.stderr"; then
  fail 'unknown provider reason unexpectedly succeeded'
fi
grep -Fxq 'Observability live validation failed: Provider request failed: monitoring.alerts.list (HTTP 403, curl 22).' \
  "$CASE_DIR/api-unknown.stderr"
! grep -Eq 'HOSTILE_UNKNOWN_REASON|secret@example|secret\.example|SECRET_BODY|token-SECRET|project-secret' \
  "$CASE_DIR/api-unknown.stdout" "$CASE_DIR/api-unknown.stderr" \
  || fail 'unknown provider reason leaked protected request data'

for fixture in malformed plain; do
  if env -u OBSERVABILITY_PROVIDER_HTTP CASE_DIR="$CASE_DIR" FIXTURE="$fixture" bash -c '
    source "$1"; PROJECT_ID=test-project; export PROJECT_ID; evidence="$CASE_DIR/evidence-$FIXTURE"; mkdir -p "$evidence"
    gcloud() { printf "%s\n" "token-SECRET"; }
    curl() {
      local output=; while test "$#" -gt 0; do
        if test "$1" = --output; then output="$2"; shift 2; else shift; fi
      done
      if test "$FIXTURE" = malformed; then
        printf "%s\n" "not-json SECRET_BODY secret@example.invalid token-SECRET" > "$output"
      else
        printf "%s\n" "plain SECRET_BODY secret@example.invalid token-SECRET project-secret" > "$output"
      fi
      printf "%s" 403
      return 22
    }
    api GET "https://monitoring.googleapis.com/v3/projects/test-project/alerts?pageSize=1" "$evidence/alerts.json"
  ' _ "$live_helper" > "$CASE_DIR/api-$fixture.stdout" 2> "$CASE_DIR/api-$fixture.stderr"; then
    fail "provider $fixture unexpectedly succeeded"
  fi
  grep -Fxq 'Observability live validation failed: Provider request failed: monitoring.alerts.list (HTTP 403, curl 22).' \
    "$CASE_DIR/api-$fixture.stderr"
  ! grep -Eq 'secret@example|secret\.example|SECRET_BODY|token-SECRET|project-secret' \
    "$CASE_DIR/api-$fixture.stdout" "$CASE_DIR/api-$fixture.stderr" \
    || fail "provider $fixture leaked protected request data"
done

if env -u OBSERVABILITY_PROVIDER_HTTP CASE_DIR="$CASE_DIR" bash -c '
  source "$1"; PROJECT_ID=test-project; export PROJECT_ID; evidence="$CASE_DIR/evidence-http000"; mkdir -p "$evidence"
  gcloud() { printf "%s\n" "token-SECRET"; }
  curl() {
    local output=; while test "$#" -gt 0; do
      if test "$1" = --output; then output="$2"; shift 2; else shift; fi
    done
    : > "$output"; return 7
  }
  api GET "https://monitoring.googleapis.com/v3/projects/test-project/alerts?pageSize=1" "$evidence/alerts.json"
' _ "$live_helper" > "$CASE_DIR/api-http000.stdout" 2> "$CASE_DIR/api-http000.stderr"; then
  fail 'provider transport failure unexpectedly succeeded'
fi
grep -Fxq 'Observability live validation failed: Provider request failed: monitoring.alerts.list (HTTP 000, curl 7).' \
  "$CASE_DIR/api-http000.stderr"

env -u OBSERVABILITY_PROVIDER_HTTP CASE_DIR="$CASE_DIR" bash -c '
  source "$1"; PROJECT_ID=test-project; export PROJECT_ID; evidence="$CASE_DIR/evidence-http200"; mkdir -p "$evidence"
  gcloud() { printf "%s\n" "token-SECRET"; }
  curl() {
    local output=; while test "$#" -gt 0; do
      if test "$1" = --output; then output="$2"; shift 2; else shift; fi
    done
    printf "%s\n" "{\"alerts\":[]}" > "$output"; printf "%s" 200
  }
  api GET "https://monitoring.googleapis.com/v3/projects/test-project/alerts?pageSize=1" "$evidence/alerts.json"
' _ "$live_helper"
jq -e '.alerts == []' "$CASE_DIR/evidence-http200/alerts.json" >/dev/null
pass 'provider api reports sanitized HTTP 403/curl 22, HTTP 000 transport failures, and accepts a 200 response'

new_case live-provider-before-resources
for mode in success failure; do
  : > "$CASE_DIR/provider-calls"
  test_status=0
  env CASE_DIR="$CASE_DIR" MODE="$mode" PROJECT_ID=test-project SERVICE_NAME=vlrgg-query-check \
    OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 \
    OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
    bash -c '
      source "$1"; evidence="$CASE_DIR/preflight"; mkdir -p "$evidence"
      api() {
        test "$1" = GET || exit 1
        printf "%s\n" "$3" >> "$CASE_DIR/provider-calls"
        test "$MODE" != failure || exit 1
        case "$3" in
          */alerts-preflight.json) printf "%s\n" "{\"alerts\":[]}" > "$3" ;;
          */error-reporting-preflight.json) printf "%s\n" "{\"timeRangeBegin\":\"2026-01-01T00:00:00Z\"}" > "$3" ;;
          */channel.json) printf "%s\n" "{\"name\":\"projects/test-project/notificationChannels/channel-1\",\"enabled\":true}" > "$3" ;;
          *) exit 1 ;;
        esac
      }
      provider_preflight
    ' _ "$live_helper" || test_status=$?
  if test "$mode" = success; then
    test "$test_status" = 0 && test "$(wc -l < "$CASE_DIR/provider-calls" | tr -d " ")" = 3
  else
    test "$test_status" != 0 && test "$(wc -l < "$CASE_DIR/provider-calls" | tr -d " ")" = 1
  fi
done
provider_line="$(grep -n 'name: Check observability provider access before creating resources' "$workflow" | cut -d: -f1)"
journal_line="$(grep -n 'name: Guard or prepare the validation recovery journal' "$workflow" | cut -d: -f1)"
test "$provider_line" -lt "$journal_line" || fail 'provider checks must precede journal/resource creation'
pass 'provider preflight performs only three reads without a journal and fails before later requests'

new_case live-private-contract
prepare_live_case
live private_request POST /__observability/health/fail 200
jq -e '.status == "configured"' "$CASE_DIR/evidence/private-response" >/dev/null
live private_exit
grep -q $'^POST\thttps://vlrgg-query-check-test.run.app\t/__observability/exit$' "$CASE_DIR/private-calls"
pass 'live driver uses the actual 200 health mutation and 202 abnormal-exit contracts'

new_case live-o3-o6-schema
# Minimal synthetic fixtures follow the formatter and provider contracts; no local runtime logs are required.
internal_message='kr.co.cotton.vlrgg_mobile.observability.validation.ValidationInternalFailure: INTERNAL_ERROR
	at kr.co.cotton.vlrgg_mobile.observability.validation.ObservabilityValidationMainKt.validationInternal(ObservabilityValidationMain.kt:1)'
parsing_message='kr.co.cotton.vlrgg_mobile.observability.validation.ValidationParsingFailure: SOURCE_PARSING_FAILURE
	at kr.co.cotton.vlrgg_mobile.observability.validation.ObservabilityValidationMainKt.validationParsing(ObservabilityValidationMain.kt:1)'
jq -n --arg internal "$internal_message" --arg parsing "$parsing_message" '
  def event($category; $code; $status; $message):
    {severity:(if $status == 500 or $category == "SOURCE_PARSING" then "ERROR" else "WARNING" end),
     jsonPayload:{category:$category,error_code:$code,http_status:$status,
       serviceContext:{service:"vlrgg-query-check",version:"vlrgg-query-check-o123-1"},
       canonical_upstream:(if $category == "INTERNAL" then "none" else "https://www.vlr.gg/" end),
       "@type":"type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent",
       message:$message,truncation:{accessor_failure:false,bytes:false,candidates:false,causes:false,cycle:false,frames:false}}};
  {entries:([range(0;4) as $index | event("INTERNAL";"INTERNAL_ERROR";500;$internal) +
     (if $index == 0 then {trace:"projects/test-project/traces/0123456789abcdef0123456789abcdef"}
      elif $index == 2 then {trace:"projects/test-project/traces/abcdef0123456789abcdef0123456789"} else {} end)] +
    [event("SOURCE_PARSING";"SOURCE_PARSING_FAILURE";502;$parsing),
     event("UPSTREAM_NETWORK";"UPSTREAM_NETWORK_FAILURE";502;"safe upstream category"),
     event("EXPECTED";"INVALID_REQUEST";400;"safe expected category")] +
    [range(0;5) as $index | {
      logName:"projects/test-project/logs/run.googleapis.com%2Frequests",
      trace:(if $index == 0 then "projects/test-project/traces/0123456789abcdef0123456789abcdef"
        elif $index == 2 then "projects/test-project/traces/abcdef0123456789abcdef0123456789"
        else null end),
      httpRequest:{status:500,requestUrl:"https://vlrgg-query-check-test.run.app/__observability/internal"}
    } | if .trace == null then del(.trace) else . end] +
    [{textPayload:"public_api_summary requests=9 diagnostics_emitted={EXPECTED=2,UPSTREAM_NETWORK=1,INTERNAL=4,SOURCE_PARSING=1} diagnostics_suppressed={EXPECTED=0,UPSTREAM_NETWORK=0,INTERNAL=1,SOURCE_PARSING=0}"}])}
' > "$CASE_DIR/application-fixture.json"
jq '.entries[1].trace="projects/test-project/traces/11112222333344445555666677778888" |
  .entries[8].trace=.entries[1].trace |
  .entries[0] as $first | .entries[0]=.entries[3] | .entries[3]=$first' \
  "$CASE_DIR/application-fixture.json" > "$CASE_DIR/managed-fixture.json"
jq '.entries[2].trace="projects/test-project/traces/not-a-trace"' \
  "$CASE_DIR/application-fixture.json" > "$CASE_DIR/malformed-fixture.json"
jq '.entries[2].trace="projects/other-project/traces/abcdef0123456789abcdef0123456789"' \
  "$CASE_DIR/application-fixture.json" > "$CASE_DIR/wrong-project-fixture.json"
jq '.entries[2].trace="projects/test-project/traces/ffffffffffffffffffffffffffffffff"' \
  "$CASE_DIR/application-fixture.json" > "$CASE_DIR/noncorrelated-fixture.json"
jq -n '{errorGroupStats:[
  {group:{name:"projects/test-project/groups/internal"},numAffectedServices:1,affectedServices:[{service:"vlrgg-query-check",version:"vlrgg-query-check-o123-1"}],representative:{}},
  {group:{name:"projects/test-project/locations/global/groups/parsing"},numAffectedServices:1,affectedServices:[{service:"vlrgg-query-check",version:"vlrgg-query-check-o123-1"}],representative:{}}
]}' > "$CASE_DIR/groups-fixture.json"
jq -n --arg message "$internal_message" '{errorEvents:[{eventTime:"2100-01-01T00:00:00Z",serviceContext:{service:"vlrgg-query-check",version:"vlrgg-query-check-o123-1"},message:$message}]}' \
  > "$CASE_DIR/events-internal.json"
jq -n --arg message "$parsing_message" '{errorEvents:[{eventTime:"2100-01-01T00:00:00Z",serviceContext:{service:"vlrgg-query-check",version:"vlrgg-query-check-o123-1"},message:$message}]}' \
  > "$CASE_DIR/events-parsing.json"
printf '%s\n' '{"name":"projects/test-project/groups/internal","resolutionStatus":"OPEN","trackingIssues":[{"url":"safe"}]}' \
  > "$CASE_DIR/group-fixture.json"
printf '%s\n' '{"name":"projects/test-project/groups/internal","resolutionStatus":"RESOLVED"}' \
  > "$CASE_DIR/resolved-fixture.json"
printf '%s\n' '{"name":"projects/test-project/groups/internal","resolutionStatus":"OPEN"}' \
  > "$CASE_DIR/reopened-fixture.json"
env PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 CASE_DIR="$CASE_DIR" \
  GITHUB_STEP_SUMMARY="$CASE_DIR/summary" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    openssl() { printf "0123456789abcdef0123456789abcdef\n"; }
    private_request() { :; }; sleep_for() { :; }; guard_target() { :; }; require_fault_time() { :; }
    result() { printf "%s %s\n" "$1" "$2" >> "$CASE_DIR/results"; }
    log_entries() { cp "$CASE_DIR/${TRACE_CASE:-application}-fixture.json" "$2"; }
    poll_native_failures() { test "$2" = 7; }
    api() {
      local method="$1" url="$2" output="$3"
      case "$url" in
        *groupStats*) cp "$CASE_DIR/groups-fixture.json" "$output" ;;
        *events?groupId=internal*) cp "$CASE_DIR/events-internal.json" "$output" ;;
        *events?groupId=parsing*) cp "$CASE_DIR/events-parsing.json" "$output" ;;
        *)
          if test "$method" = PUT; then cp "$CASE_DIR/resolved-fixture.json" "$output"
          elif [[ "$output" == *status.json ]]; then cp "$CASE_DIR/resolved-fixture.json" "$output"
          elif [[ "$output" == *reopened.json ]]; then cp "$CASE_DIR/reopened-fixture.json" "$output"
          else cp "$CASE_DIR/group-fixture.json" "$output"; fi ;;
      esac
    }
    run_o3_o6
    TRACE_CASE=managed run_o3_o6
    for TRACE_CASE in malformed wrong-project noncorrelated; do
      if (run_o3_o6); then exit 1; fi
    done
  ' _ "$live_helper"
jq -e '.resolutionStatus=="RESOLVED" and .trackingIssues==[{"url":"safe"}]' \
  "$CASE_DIR/evidence/error-group-resolve.json" >/dev/null
for gate in O3 O4 O5 O6; do grep -qx "$gate PASS" "$CASE_DIR/results"; done
grep -qx 'O6-receipt RECEIPT PENDING' "$CASE_DIR/results"
pass 'O3-O6 fixtures validate trace-less, managed, malformed, cross-project, and noncorrelated traces'

new_case live-native-ledger
env PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 CASE_DIR="$CASE_DIR" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    sleep_for() { :; }
    api() {
      if test ! -e "$CASE_DIR/queried"; then
        touch "$CASE_DIR/queried"; printf "{}\n" > "$3"
      else
        printf "%s\n" "{\"timeSeries\":[{\"points\":[{\"value\":{\"int64Value\":\"7\"}}]}]}" > "$3"
      fi
    }
    poll_native_failures 2026-01-01T00:00:00Z 7
    if (poll_native_failures 2026-01-01T00:00:00Z 6) 2>/dev/null; then exit 1; fi
    api() { printf "{}\n" > "$3"; }
    if (poll_native_failures 2026-01-01T00:00:00Z 0) 2>/dev/null; then exit 1; fi
  ' _ "$live_helper"
pass 'raw native counts retry ingestion delay, reject excess traffic, and never accept missing data as zero'

new_case live-promql-recovery
jq -n '{status:"success",data:{resultType:"vector",result:[{metric:{},value:[1,"4"]}]}}' \
  > "$CASE_DIR/prometheus-2xx.json"
jq -n '{status:"success",data:{resultType:"vector",result:[{metric:{},value:[1,"0"]}]}}' \
  > "$CASE_DIR/prometheus.json"
live poll_recovery 1 >/dev/null
printf '%s\n' '{"status":"success","data":{"resultType":"vector","result":[]}}' \
  > "$CASE_DIR/prometheus.json"
expect_fail "$CASE_DIR/live-missing-recovery.stderr" live poll_recovery 1
pass 'live recovery requires fresh 2xx and one finite numeric 5xx zero, never missing data'
cat > "$CASE_DIR/ingest-after-wait" <<'SH'
#!/usr/bin/env bash
printf '%s\n' '{"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1,"0"]}]}}' > "$CASE_DIR/prometheus.json"
SH
chmod +x "$CASE_DIR/ingest-after-wait"
( export OBSERVABILITY_SLEEP_COMMAND="$CASE_DIR/ingest-after-wait"; live poll_count 5xx '==' 0 2 ) >/dev/null
pass 'PromQL polling retries an initially absent series instead of aborting or treating absence as zero'

new_case live-alert-transitions
jq -n '{alerts:[{name:"projects/test-project/alerts/exact",policy:{name:"projects/test-project/alertPolicies/owned",userLabels:{managed_by:"issue122-validation",validation_run:"123_1",resource_kind:"5xx"}},state:"OPEN",openTime:"2026-01-01T00:00:00Z"}]}' \
  > "$CASE_DIR/alerts-open.json"
jq -n '{alerts:[{name:"projects/test-project/alerts/exact",policy:{name:"projects/test-project/alertPolicies/owned",userLabels:{managed_by:"issue122-validation",validation_run:"123_1",resource_kind:"5xx"}},state:"CLOSED",openTime:"2026-01-01T00:00:00Z",closeTime:"2026-01-01T00:05:00Z"}]}' \
  > "$CASE_DIR/alerts-closed.json"
cp "$CASE_DIR/alerts-open.json" "$CASE_DIR/alerts-current.json"
env PROJECT_ID=test-project SERVICE_NAME=vlrgg-query-check OBSERVABILITY_RUN=123-1 OBSERVABILITY_DEADLINE_EPOCH="$OBSERVABILITY_DEADLINE_EPOCH" \
  CASE_DIR="$CASE_DIR" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    api() { cp "$CASE_DIR/alerts-current.json" "$3"; }
    sleep_for() { :; }
    alert="$(poll_alert_open projects/test-project/alertPolicies/owned 0 5xx 1)"
    test "$alert" = projects/test-project/alerts/exact
    cp "$CASE_DIR/alerts-closed.json" "$CASE_DIR/alerts-current.json"
    poll_alert_closed projects/test-project/alertPolicies/owned "$alert" 0 1
  ' _ "$live_helper"
cp "$CASE_DIR/alerts-open.json" "$CASE_DIR/alerts-current.json"
expect_fail "$CASE_DIR/alert-name.stderr" env PROJECT_ID=test-project \
  OBSERVABILITY_DEADLINE_EPOCH="$OBSERVABILITY_DEADLINE_EPOCH" CASE_DIR="$CASE_DIR" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    api() { cp "$CASE_DIR/alerts-current.json" "$3"; }; sleep_for() { :; }
    poll_alert_closed projects/test-project/alertPolicies/owned projects/test-project/alerts/other 0 1
  ' _ "$live_helper"
pass 'live alert polling binds OPEN and CLOSED to one alert name without a nonexistent condition field'

new_case live-uptime-freshness
jq -n '["USA_IOWA","EUROPE","ASIA_PACIFIC"] as $locations | {timeSeries:[$locations[] as $location | {
  metric:{labels:{check_id:"check-1",checker_location:$location}},
  resource:{labels:{project_id:"test-project",location:"test-region",service_name:"vlrgg-query-check",revision_name:"vlrgg-query-check-o123-1"}},
  points:[{interval:{endTime:"2100-01-01T00:00:00Z"},value:{boolValue:true}}]
}]}' > "$CASE_DIR/uptime-passed.json"
jq -n '["USA_IOWA","EUROPE","ASIA_PACIFIC"] as $locations | {timeSeries:[$locations[] as $location | {
  metric:{labels:{check_id:"check-1",checker_location:$location}},
  resource:{labels:{project_id:"test-project",location:"test-region",service_name:"vlrgg-query-check",revision_name:"vlrgg-query-check-o123-1"}},
  points:[{interval:{endTime:"2100-01-01T00:00:00Z"},value:{stringValue:"200"}}]
}]}' > "$CASE_DIR/uptime-http.json"
env PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 CASE_DIR="$CASE_DIR" bash -c '
  source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
  api() { if [[ "$2" == *http_status* ]]; then cp "$CASE_DIR/uptime-http.json" "$3"; else cp "$CASE_DIR/uptime-passed.json" "$3"; fi; }
  passed="$(uptime_locations check-1 true 0)"; http="$(uptime_http_locations check-1 0)"
  test "$(jq -r .count <<< "$passed")" = 3
  test "$(jq -r .count <<< "$http")" = 3
  poll_uptime_locations check-1 true 3 0 1 >/dev/null
  poll_uptime_http check-1 0 1 >/dev/null
  stale="$(uptime_locations check-1 true 4102444801)"
  test "$(jq -r .count <<< "$stale")" = 0
' _ "$live_helper"
pass 'uptime evidence requires three fresh labeled checker points and fresh HTTP 200 values'

new_case live-uptime-always-restores
if env PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 CASE_DIR="$CASE_DIR" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    now() { printf 1700000000; }
    ensure_policy() { if test "$1" = uptime; then printf "projects/test-project/uptimeCheckConfigs/check-1\n"; else printf "projects/test-project/alertPolicies/uptime\n"; fi; }
    verify_uptime_check() { :; }; verify_single_condition() { :; }
    poll_uptime_locations() {
      if test "$2" = false; then fail "missing false transition"; fi
      printf "%s\n" "{\"count\":3,\"evidenceEpoch\":1700000000}"
    }
    poll_uptime_http() { printf "%s\n" "{\"count\":3,\"evidenceEpoch\":1700000000}"; }
    private_request() {
      printf "%s %s\n" "$1" "$2" >> "$CASE_DIR/requests"
      printf "%s\n" "{\"status\":\"configured\"}" > "$evidence/private-response"
    }
    poll_health() { :; }; poll_alert_open() { printf "alert\n"; }; result() { :; }
    run_o8
  ' _ "$live_helper" > /dev/null 2> "$CASE_DIR/stderr"; then
  fail 'uptime phase unexpectedly passed without false-transition evidence'
fi
grep -qx 'POST /__observability/health/restore' "$CASE_DIR/requests"
pass 'uptime evidence failure still executes the explicit private health restore path'

new_case live-poll-deadline
CASE_DIR="$CASE_DIR" bash -c '
  source "$1"
  evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
  OBSERVABILITY_DEADLINE_EPOCH=2000000000
  printf "1999998200\n" > "$CASE_DIR/clock"
  now() { cat "$CASE_DIR/clock"; }
  uptime_locations() {
    printf "query\n" >> "$CASE_DIR/queries"
    printf "%s\n" "{\"count\":0,\"evidenceEpoch\":0}"
  }
  sleep_for() {
    printf "%s\n" "$1" >> "$CASE_DIR/waits"
    printf "%s\n" "$(( $(now) + $1 ))" > "$CASE_DIR/clock"
  }
  if (poll_uptime_locations check false 2 0 3) 2>/dev/null; then exit 1; fi
  test ! -s "$CASE_DIR/queries"
  printf "1999998195\n" > "$CASE_DIR/clock"
  if (poll_uptime_locations check false 2 0 3) 2>/dev/null; then exit 1; fi
  test "$(wc -l < "$CASE_DIR/queries" | tr -d " ")" = 1
  test "$(cat "$CASE_DIR/waits")" = 5
  uptime_locations() {
    printf "query\n" >> "$CASE_DIR/queries"
    printf "%s\n" "{\"count\":3,\"evidenceEpoch\":1999998200}"
  }
  poll_uptime_locations check true 3 0 1 recovery >/dev/null
  printf "2000000000\n" > "$CASE_DIR/clock"
  if (poll_uptime_locations check true 3 0 1 recovery) 2>/dev/null; then exit 1; fi
  test "$(wc -l < "$CASE_DIR/queries" | tr -d " ")" = 2
' _ "$live_helper"
pass 'polling stops at the cutoff, caps waits, and gives recovery its separate deadline'

new_case live-uptime-deadline-restores
if CASE_DIR="$CASE_DIR" bash -c '
  source "$1"
  evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
  OBSERVABILITY_DEADLINE_EPOCH=2000000000
  printf "1999998199\n" > "$CASE_DIR/clock"
  now() { cat "$CASE_DIR/clock"; }
  ensure_policy() { printf "projects/test-project/uptimeCheckConfigs/check-1\n"; }
  verify_uptime_check() { :; }; verify_single_condition() { :; }
  uptime_locations() {
    printf "query\n" >> "$CASE_DIR/queries"
    printf "%s\n" "{\"count\":3,\"evidenceEpoch\":1999998200}"
  }
  uptime_http_locations() { uptime_locations; }
  private_request() {
    printf "%s\n" "$2" >> "$CASE_DIR/requests"
    printf "%s\n" "{\"status\":\"configured\"}" > "$evidence/private-response"
    if test "$2" = /__observability/health/fail; then printf "1999998200\n" > "$CASE_DIR/clock"; fi
  }
  poll_health() { printf "health\n" >> "$CASE_DIR/requests"; }
  result() { printf "%s %s\n" "$1" "$2" >> "$CASE_DIR/results"; }
  run_o8
' _ "$live_helper" > /dev/null 2> "$CASE_DIR/stderr"; then
  fail 'uptime passed despite the expired fault-evidence budget'
fi
grep -q 'polling budget expired' "$CASE_DIR/stderr"
grep -qx '/__observability/health/restore' "$CASE_DIR/requests"
grep -qx 'health' "$CASE_DIR/requests"
test "$(wc -l < "$CASE_DIR/queries" | tr -d ' ')" = 4 || fail 'expired fault poll queried the provider'
test ! -s "$CASE_DIR/results" || fail 'uptime deadline reported a false pass'
pass 'uptime deadline expiry still restores health and never reports a pass'

new_case live-attempt-deadline
bash -c '
  source "$1"
  now() { printf 1767226800; }
  set_validation_deadline 2026-01-01T00:00:00Z
  test "$OBSERVABILITY_DEADLINE_EPOCH" = 1767232200
  require_fault_window 3300
  now() { printf 1767228000; }
  set_validation_deadline 2026-01-01T00:00:00Z
  test "$OBSERVABILITY_DEADLINE_EPOCH" = 1767232800
  if (require_fault_window 3300) 2>/dev/null; then exit 1; fi
' _ "$live_helper"
pass 'live deadline starts after preparation while preserving the attempt ceiling and restoration reserve'

new_case live-expired-fault
prepare_live_case
expect_fail "$CASE_DIR/live-deadline.stderr" env OBSERVABILITY_DEADLINE_EPOCH=1000000000 \
  bash -c 'source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"; private_request GET /__observability/internal 500' \
    _ "$live_helper"
test ! -s "$CASE_DIR/private-calls" || fail 'expired fault cutoff reached the private validation endpoint'
( export OBSERVABILITY_DEADLINE_EPOCH=1000000000; live private_request POST /__observability/health/restore 200 )
( export OBSERVABILITY_DEADLINE_EPOCH=1000000000; live poll_health 1 )
grep -q $'^POST\thttps://vlrgg-query-check-test.run.app\t/__observability/health/restore$' "$CASE_DIR/private-calls"
grep -q $'^GET\thttps://vlrgg-query-check-test.run.app\t/health$' "$CASE_DIR/private-calls"
pass 'fault cutoff blocks new faults but never blocks explicit restore and recovery polling'

new_case live-o9-exits
env OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 PROJECT_ID=test-project REGION=test-region \
  SERVICE_NAME=vlrgg-query-check CASE_DIR="$CASE_DIR" \
  GITHUB_STEP_SUMMARY="$CASE_DIR/summary" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    system_logs() {
      if [[ "$2" == *history.json ]]; then printf "%s\n" "{\"entries\":[]}" > "$2"
      else printf "%s\n" "{\"entries\":[{\"resource\":{\"labels\":{\"revision_name\":\"$OBSERVABILITY_REVISION\"}},\"textPayload\":\"Container called exit(42).\"}]}" > "$2"; fi
    }
    private_exit() { printf "exit\n" >> "$CASE_DIR/exits"; }
    poll_health() { :; }; ensure_policy() { printf "projects/test-project/alertPolicies/log\n"; }
    verify_single_condition() { :; }; poll_alert_open() { printf "projects/test-project/alerts/log\n"; }
    result() { printf "%s %s\n" "$1" "$2" >> "$CASE_DIR/results"; }
    run_o9
  ' _ "$live_helper"
test "$(wc -l < "$CASE_DIR/exits" | tr -d ' ')" = 2
grep -qx 'OOM NOT RUN' "$CASE_DIR/results"
if grep -q 'OOM PASS' "$CASE_DIR/results"; then
  fail 'O9 claimed OOM PASS'
fi
pass 'O9 uses exactly two exits only for safe discovery and never claims OOM'

new_case live-o9-ambiguous
if env OBSERVABILITY_REVISION=vlrgg-query-check-o123-1 PROJECT_ID=test-project REGION=test-region \
  SERVICE_NAME=vlrgg-query-check CASE_DIR="$CASE_DIR" \
  GITHUB_STEP_SUMMARY="$CASE_DIR/summary" bash -c '
    source "$1"; evidence="$CASE_DIR/evidence"; mkdir -p "$evidence"
    system_logs() {
      if [[ "$2" == *history.json ]]; then printf "%s\n" "{\"entries\":[]}" > "$2"
      else printf "%s\n" "{\"entries\":[{\"resource\":{\"labels\":{\"revision_name\":\"$OBSERVABILITY_REVISION\"}},\"textPayload\":\"Container called exit(42).\"},{\"resource\":{\"labels\":{\"revision_name\":\"$OBSERVABILITY_REVISION\"}},\"textPayload\":\"Container terminated exit 42\"}]}" > "$2"; fi
    }
    private_exit() { printf "exit\n" >> "$CASE_DIR/exits"; }
    poll_health() { :; }; ensure_policy() { printf "policy\n"; }; verify_single_condition() { :; }
    poll_alert_open() { printf "alert\n"; }; result() { :; }
    run_o9
  ' _ "$live_helper" > /dev/null 2> "$CASE_DIR/stderr"; then
  fail 'ambiguous abnormal-exit discovery unexpectedly succeeded'
fi
test "$(wc -l < "$CASE_DIR/exits" | tr -d ' ')" = 1
pass 'ambiguous O9 discovery fails before policy creation and the second exit'

if grep -Fq '/__observability/internal/other' "$live_helper"; then
  fail 'live driver includes a non-validation endpoint'
fi
if grep -Eq 'result OOM (PASS|RECEIPT)' "$live_helper"; then
  fail 'live driver includes an OOM success claim'
fi
pass 'live driver stays on the validation harness, two Error Reporting groups, and no OOM claim'

new_case expired-deadline
service prepare >/dev/null
expect_fail "$CASE_DIR/deadline.stderr" env PATH="$PATH" PROJECT_ID=test-project REGION=test-region \
  SERVICE_NAME=vlrgg-query-check VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_DEADLINE_EPOCH=1000000000 OBSERVABILITY_HTTP="$work_dir/http" \
  OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
  OBSERVABILITY_CONFIRMED_RECEIVERS=true "$policy_helper" ensure 5xx
! grep -Eq $'^(POST|PATCH|DELETE)\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'expired validation deadline mutated Monitoring state'
pass '90-minute validation deadline stops new policy mutations'

new_case policy-journal-required
expect_fail "$CASE_DIR/no-journal.stderr" policy ensure 5xx
! grep -Eq $'^(POST|PATCH|DELETE)\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'policy mutation without a journal wrote Monitoring state'
service prepare >/dev/null
expect_fail "$CASE_DIR/foreign-journal.stderr" env PATH="$work_dir/bin:$PATH" \
  PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=999-1 OBSERVABILITY_HTTP="$work_dir/http" \
  OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
  OBSERVABILITY_CONFIRMED_RECEIVERS=true "$policy_helper" ensure 5xx
! grep -Eq $'^(POST|PATCH|DELETE)\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'policy mutation for another journal owner wrote Monitoring state'
expect_fail "$CASE_DIR/unrecorded-delete.stderr" policy delete \
  projects/test-project/alertPolicies/not-recorded policy
! grep -Eq $'^(POST|PATCH|DELETE)\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'unrecorded policy delete wrote Monitoring state'
pass 'policy mutations require the exact active journal owner and resource'

new_case cleanup-policy
service prepare >/dev/null
owned_policy="$(policy ensure 5xx)"
jq '.alertPolicies += [{
  name:"projects/test-project/alertPolicies/foreign",
  userLabels:{managed_by:"someone-else",validation_run:"other",resource_kind:"5xx"}
}]' "$CASE_DIR/alertPolicies.json" > "$CASE_DIR/next.json"
mv "$CASE_DIR/next.json" "$CASE_DIR/alertPolicies.json"
: > "$CASE_DIR/http-calls"
env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore
! grep -q $'^DELETE\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'restore deleted resources before health verification'
jq -e '
  . as $service |
  ([$service.traffic[]|select(.percent>0)]|length==1 and .[0].percent==100) and
  $service.invokerIamDisabled==false and $service.ingress!="INGRESS_TRAFFIC_NONE"
' "$CASE_DIR/service.json" >/dev/null
env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
jq -e --arg owned "$owned_policy" '
  (.alertPolicies|length)==1 and .alertPolicies[0].name=="projects/test-project/alertPolicies/foreign" and
  all(.alertPolicies[]; .name!=$owned)
' "$CASE_DIR/alertPolicies.json" >/dev/null
journal="$(service read)"
jq -e '.phase=="verified" and .resources==[] and (has("pending")|not)' <<< "$journal" >/dev/null
delete_line="$(grep -n $'^DELETE\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" | cut -d: -f1)"
test -n "$delete_line" || fail 'cleanup did not delete the recorded owned policy'
service clear >/dev/null
jq -e '.annotations["vlrgg-observability-validation"]==null' "$CASE_DIR/service.json" >/dev/null
clear_line="$(grep -n 'updateMask=annotations' "$CASE_DIR/http-calls" | tail -n1 | cut -d: -f1)"
test "$clear_line" -gt "$delete_line" || fail 'journal cleared before owned resource cleanup'
pass 'cleanup deletes only journal-owned resources and clears last'

new_case cleanup-pending-denied
service prepare >/dev/null
service pending revision vlrgg-query-check-o123-1 >/dev/null
: > "$CASE_DIR/http-calls"
env PATH="$work_dir/bin:$PATH" \
  PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore
touch "$CASE_DIR/gcloud-denied"
expect_fail "$CASE_DIR/pending-denied.stderr" env PATH="$work_dir/bin:$PATH" \
  PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
grep -q 'updateMask=traffic' "$CASE_DIR/http-calls"
jq -e '.pending.kind=="revision" and .phase=="restoring"' <<< "$(service read)" >/dev/null
pass 'cleanup restores traffic before failed pending discovery and retains intent'

rm "$CASE_DIR/gcloud-denied"
env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=999-1 OPERATION=observability-restore \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
jq -e '.phase=="verified" and (has("pending")|not)' <<< "$(service read)" >/dev/null
pass 'restore mode adopts the durable journal owner after authoritative absence'

new_case restore-public-iam
service prepare >/dev/null
printf '%s\n' '{"bindings":[{"role":"roles/run.invoker","members":["allAuthenticatedUsers"]}]}' \
  > "$CASE_DIR/iam-policy.json"
expect_fail "$CASE_DIR/restored-public.stderr" service restore
jq -e '.annotations["vlrgg-observability-validation"]!=null' "$CASE_DIR/service.json" >/dev/null
pass 'restore rejects broadly authenticated IAM and retains recovery marker'

new_case manual-restore-owner
service prepare >/dev/null
stored_run="$(env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_HTTP="$work_dir/http" "$service_helper" read | jq -er '.run')"
env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN="$stored_run" OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore
env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN="$stored_run" OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
env PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN="$stored_run" OBSERVABILITY_HTTP="$work_dir/http" "$service_helper" clear
jq -e '.annotations["vlrgg-observability-validation"]==null' "$CASE_DIR/service.json" >/dev/null
pass 'manual restore carries the durable owner through final clear'

new_case cleanup-shared-image
service prepare >/dev/null
image_tag='test-region-docker.pkg.dev/test-project/repo/query-observability:sha-123-1'
image_digest='test-region-docker.pkg.dev/test-project/repo/query-observability@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'
service pending image "$image_tag" >/dev/null
service resource image "$image_digest" >/dev/null
jq -n --arg package "${image_digest%@sha256:*}" --arg version 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc' \
  --arg owned "$image_tag" '[{package:$package,version:$version,tags:[$owned,"shared"]}]' \
  > "$CASE_DIR/gcloud-images.json"
env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore
expect_fail "$CASE_DIR/shared-image.stderr" env PATH="$work_dir/bin:$PATH" \
  PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
! grep -q '^artifacts docker images delete' "$CASE_DIR/gcloud-calls" \
  || fail 'cleanup deleted a shared image digest'
jq -e --arg image "$image_digest" 'any(.resources[]; .kind=="image" and .name==$image)' \
  <<< "$(service read)" >/dev/null
jq -n --arg package "${image_digest%@sha256:*}" --arg version 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc' \
  '[{package:$package,version:$version,tags:["sha-999-1"]}]' > "$CASE_DIR/gcloud-images.json"
: > "$CASE_DIR/gcloud-calls"
expect_fail "$CASE_DIR/other-run-image.stderr" env PATH="$work_dir/bin:$PATH" \
  PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
! grep -q '^artifacts docker images delete' "$CASE_DIR/gcloud-calls" \
  || fail 'cleanup deleted another run image digest'
jq -n --arg package "${image_digest%@sha256:*}" --arg version 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc' \
  '[{package:$package,version:$version,tags:["sha-123-1"]}]' > "$CASE_DIR/gcloud-images.json"
jq -n --arg image "$image_digest" '[{spec:{containers:[{image:$image}]},status:{imageDigest:$image}}]' \
  > "$CASE_DIR/gcloud-revisions.json"
: > "$CASE_DIR/gcloud-calls"
expect_fail "$CASE_DIR/referenced-image.stderr" env PATH="$work_dir/bin:$PATH" \
  PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
  OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
! grep -q '^artifacts docker images delete' "$CASE_DIR/gcloud-calls" \
  || fail 'cleanup deleted a revision-referenced image digest'
pass 'cleanup retains shared, other-run, and revision-referenced images'

new_case cleanup-owned-image
service prepare >/dev/null
image_tag='test-region-docker.pkg.dev/test-project/repo/query-observability:sha-123-1'
image_digest='test-region-docker.pkg.dev/test-project/repo/query-observability@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
service pending image "$image_tag" >/dev/null
service resource image "$image_digest" >/dev/null
jq -n --arg package "${image_digest%@sha256:*}" --arg version 'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd' \
  '[{package:$package,version:$version,tags:["sha-123-1"]}]' > "$CASE_DIR/gcloud-images.json"
env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore
env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
grep -qx "artifacts docker images delete $image_digest --quiet --delete-tags" "$CASE_DIR/gcloud-calls"
jq -e '.phase=="verified" and .resources==[] and (has("pending")|not)' <<< "$(service read)" >/dev/null
service clear >/dev/null
jq -e '.annotations["vlrgg-observability-validation"]==null' "$CASE_DIR/service.json" >/dev/null
pass 'cleanup deletes one provider-format run-owned image and clears its journal last'

new_case non-root-cwd
service prepare >/dev/null
(cd "$work_dir" && policy ensure 5xx >/dev/null)
(cd "$work_dir" && env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" restore)
(cd "$work_dir" && env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup)
jq -e '.phase=="verified" and .resources==[]' <<< "$(service read)" >/dev/null
pass 'policy and cleanup helpers resolve siblings outside the repository cwd'

new_case sanitized-gcloud-failure
touch "$CASE_DIR/gcloud-denied"
expect_fail "$CASE_DIR/prepare.stderr" service prepare
! grep -Eq 'secret@example|secret\.example|token-SECRET|project-secret' "$CASE_DIR/prepare.stderr" \
  || fail 'prepare leaked raw gcloud stderr'
rm "$CASE_DIR/gcloud-denied"
service prepare >/dev/null
service pending revision vlrgg-query-check-o123-1 >/dev/null
service restore >/dev/null
touch "$CASE_DIR/gcloud-denied"
expect_fail "$CASE_DIR/cleanup.stderr" env PROJECT_ID=test-project REGION=test-region \
  VALIDATION_SERVICE=vlrgg-query-check OBSERVABILITY_RUN=123-1 \
  OBSERVABILITY_HTTP="$work_dir/http" "$cleanup_helper" cleanup
! grep -Eq 'secret@example|secret\.example|token-SECRET|project-secret' "$CASE_DIR/cleanup.stderr" \
  || fail 'cleanup leaked raw gcloud stderr'
pass 'gcloud failures expose only fixed sanitized errors'

# Workflow assertions operate on actual step declarations, so validation and
# restore cannot accidentally reach production deploy, token, traffic, or build/push steps.
grep -q '^  cancel-in-progress: false$' "$workflow"
grep -A3 'name: Deploy the verified image to production' "$workflow" \
  | grep -q "if: inputs.operation == 'deploy'"
grep -A3 'name: Obtain an ID token for the production service' "$workflow" \
  | grep -q "if: inputs.operation == 'deploy'"
grep -A3 'name: Promote the verified revision' "$workflow" \
  | grep -q "if: inputs.operation == 'deploy'"
grep -A3 'name: Build the existing server image before requesting cloud credentials' "$workflow" \
  | grep -q "if: inputs.operation != 'observability-restore'"
grep -A3 'name: Build the validation-only image overlay before requesting cloud credentials' "$workflow" \
  | grep -q "if: inputs.operation == 'observability-validate'"
grep -A3 'name: Push the image and resolve its immutable digest' "$workflow" \
  | grep -q "if: inputs.operation == 'deploy'"
grep -A3 'name: Push the validation-only image and record its ownership' "$workflow" \
  | grep -q "if: inputs.operation == 'observability-validate'"
live_step="$(awk '
  /name: Run bounded private observability provider validation/ {step=1; next}
  step && /^      - name:/ {exit}
  step {print}
' "$workflow")"
grep -q 'bash .github/scripts/observability-live.sh' <<< "$live_step"
grep -q 'started_at="$(gh api' <<< "$live_step"
grep -q 'export OBSERVABILITY_WORKFLOW_STARTED_AT="$started_at"' <<< "$live_step"
grep -q 'attempts/\$GITHUB_RUN_ATTEMPT' <<< "$live_step"
grep -q 'GCP_OBSERVABILITY_NOTIFICATION_CHANNELS_JSON' <<< "$live_step"
! grep -Eq 'GCP_PROJECT_NUMBER|GCP_MONITORING_SERVICE_AGENT' <<< "$live_step" \
  || fail 'workflow still trusts redundant project-number or Google-managed identity secrets'
! grep -q '/__observability/internal' <<< "$live_step" \
  || fail 'workflow retained an endpoint-only inline validation path'
grep -A10 'name: Guard or prepare the validation recovery journal' "$workflow" \
  | grep -q 'OBSERVABILITY_DEADLINE_EPOCH=.*5400'
grep -A6 'name: Restore the shared validation service' "$workflow" \
  | grep -q "steps.preflight.outcome == 'success'"
grep -A6 'name: Restore the shared validation service' "$workflow" \
  | grep -q "steps.cloud-auth.outcome == 'success'"
grep -A6 'name: Restore the shared validation service' "$workflow" \
  | grep -q "steps.gcloud.outcome == 'success'"
grep -A10 'name: Build the existing server image before requesting cloud credentials' "$workflow" \
  | grep -q 'docker-build.log.*2>&1'
grep -A14 'name: Build the validation-only image overlay before requesting cloud credentials' "$workflow" \
  | grep -q 'docker-observability-build.log.*2>&1'
diagnostics="$(awk '
  /^      - name: Show sanitized Cloud Run diagnostics on failure$/ {step=1; next}
  step && /^      - name:/ {exit}
  step {print}
' "$workflow")"
grep -q 'provider output withheld' <<< "$diagnostics"
! grep -Eq 'sed|tail .*\$log|cat .*\$log' <<< "$diagnostics" \
  || fail 'workflow republishes protected provider logs'
pass 'workflow isolates production and restore mutation paths'

echo 'PASS: credential-free observability operations tests'

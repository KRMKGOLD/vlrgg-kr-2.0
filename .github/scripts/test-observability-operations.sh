#!/usr/bin/env bash
# Exercises observability recovery helpers with local REST and gcloud stubs.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'if test "${KEEP_OBSERVABILITY_TEST_TMP:-false}" = true; then echo "$work_dir" >&2; else rm -rf "$work_dir"; fi' EXIT
mkdir -p "$work_dir/bin"

service_helper="$repo_root/.github/scripts/observability-service.sh"
policy_helper="$repo_root/.github/scripts/observability-policies.sh"
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
        traffic) jq --slurpfile patch "$body_file" '.traffic=$patch[0].traffic | .etag="next-etag"' "$CASE_DIR/service.json" ;;
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
  else printf '%s\n' '{"timeSeries":[{"metric":{"labels":{"response_code_class":"5xx"}}}]}'; fi
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
  'iam service-accounts describe')
    project_id=test-project
    test ! -f "$CASE_DIR/foreign-service-agent" || project_id=foreign-project
    jq -n --arg email "${4:-}" --arg project_id "$project_id" \
      '{email:$email,projectId:$project_id,disabled:false}' ;;
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
  local revision='projects/test-project/locations/test-region/services/vlrgg-query-check/revisions/baseline'
  jq -n --arg revision "$revision" '{
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
}

service() {
  env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region VALIDATION_SERVICE=vlrgg-query-check \
    OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" \
    OBSERVABILITY_HOST=vlrgg-query-check-test.run.app OBSERVABILITY_REVISION=validation-r123-1 \
    MONITORING_SERVICE_AGENT=service-123@gcp-sa-monitoring-notification.iam.gserviceaccount.com \
    "$service_helper" "$@"
}

policy() {
  env PATH="$work_dir/bin:$PATH" PROJECT_ID=test-project REGION=test-region SERVICE_NAME=vlrgg-query-check \
    VALIDATION_SERVICE=vlrgg-query-check \
    OBSERVABILITY_RUN=123-1 OBSERVABILITY_HTTP="$work_dir/http" \
    OBSERVABILITY_HOST=vlrgg-query-check-test.run.app OBSERVABILITY_REVISION=validation-r123-1 \
    MONITORING_SERVICE_AGENT=service-123@gcp-sa-monitoring-notification.iam.gserviceaccount.com \
    OBSERVABILITY_NOTIFICATION_CHANNELS_JSON='["projects/test-project/notificationChannels/channel-1"]' \
    OBSERVABILITY_CONFIRMED_RECEIVERS=true "$policy_helper" "$@"
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
  .conditions[0].conditionThreshold.thresholdValue==2 and
  .conditions[0].conditionThreshold.duration=="0s" and
  .conditions[0].conditionThreshold.aggregations[0].alignmentPeriod=="300s" and
  .conditions[0].conditionThreshold.aggregations[0].perSeriesAligner=="ALIGN_SUM" and
  .conditions[0].conditionThreshold.aggregations[0].crossSeriesReducer=="REDUCE_SUM" and
  (.conditions[0].conditionThreshold.filter|contains("response_code_class = \"5xx\"")) and
  .alertStrategy.autoClose=="1800s" and
  .alertStrategy.notificationPrompts==["OPENED","CLOSED"]
' <<< "$five_x" >/dev/null
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
pass 'policy render keeps approved thresholds and exact health body regex'

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

jq '.alertPolicies[0].conditions[0].conditionThreshold.aggregations[0].groupByFields=["resource.label.revision_name"]' \
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
  SYSTEM_LOG_SIGNATURE='Container exited with status 42' "$policy_helper" render log)"
jq -e '.conditions[0].conditionMatchedLog.filter|contains("Container exited with status 42")' \
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
touch "$CASE_DIR/foreign-service-agent"
expect_fail "$CASE_DIR/foreign-agent.stderr" policy ensure uptime
! grep -q '^run services add-iam-policy-binding' "$CASE_DIR/gcloud-calls" \
  || fail 'foreign-project Monitoring service agent received an IAM binding'
! grep -q $'^POST\thttps://monitoring.googleapis.com/' "$CASE_DIR/http-calls" \
  || fail 'foreign-project Monitoring service agent allowed uptime creation'
pass 'Monitoring service agent must belong to the current project'

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

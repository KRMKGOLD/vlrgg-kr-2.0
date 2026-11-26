#!/usr/bin/env bash
# Runs deployment workflow steps against a local gcloud stub; no cloud access.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/bin"

awk '
  /^      - name: Deploy and inspect the private validation service$/ { step = 1; next }
  step && /^      - name:/ { exit }
  step && /^        run: \|$/ { code = 1; next }
  code { sub(/^          /, ""); print }
' "$repo_root/.github/workflows/deploy-server.yml" > "$work_dir/validation.sh"
test -s "$work_dir/validation.sh"
bash -n "$work_dir/validation.sh"

awk '
  /^      - name: Deploy the verified image to production$/ { step = 1; next }
  step && /^      - name:/ { exit }
  step && /^        run: \|$/ { code = 1; next }
  code { sub(/^          /, ""); print }
' "$repo_root/.github/workflows/deploy-server.yml" > "$work_dir/production.sh"
test -s "$work_dir/production.sh"
bash -n "$work_dir/production.sh"

# The cache switch crosses steps through a preflight output; a renamed output would silently turn
# the cache off, so check both ends of the wiring in the workflow source.
awk '
  /^      - name: Require enabled deployment and successful server CI for this main commit$/ { step = 1; next }
  step && /^      - name:/ { exit }
  step { print }
' "$repo_root/.github/workflows/deploy-server.yml" > "$work_dir/preflight-step.yml"
awk '
  /^      - name: Deploy the verified image to production$/ { step = 1; next }
  step && /^      - name:/ { exit }
  step { print }
' "$repo_root/.github/workflows/deploy-server.yml" > "$work_dir/production-step.yml"
wiring_line() {
  grep -qxF -- "$1" "$2" || { echo "FAIL: response cache wiring: $1" >&2; exit 1; }
}
wiring_line '          RESPONSE_CACHE_SETTING: ${{ vars.SERVER_RESPONSE_CACHE_ENABLED }}' "$work_dir/preflight-step.yml"
wiring_line '            echo "response_cache_enabled=true" >> "$GITHUB_OUTPUT"' "$work_dir/preflight-step.yml"
wiring_line '          RESPONSE_CACHE_ENABLED: ${{ steps.preflight.outputs.response_cache_enabled }}' "$work_dir/production-step.yml"
if grep -Eq 'RESPONSE_CACHE|UPSTASH|DEPLOY_SHA|secrets' "$work_dir/validation.sh"; then
  echo 'FAIL: validation service step references response cache settings' >&2
  exit 1
fi
echo 'PASS: response cache wiring'

cache_sha=0123456789abcdef0123456789abcdef01234567
base_env_flag='--update-env-vars=VLRGG_ENABLE_API_DOCUMENTATION=false,GOOGLE_CLOUD_PROJECT=test'
on_secret_flag='--update-secrets=VLRGG_UPSTASH_REDIS_REST_URL=vlrgg-upstash-rest-url-production:latest,VLRGG_UPSTASH_REDIS_REST_TOKEN=vlrgg-upstash-rest-token-production:latest'
cache_on_env="$(jq -cn --arg sha "$cache_sha" '[
  {name:"VLRGG_ENABLE_API_DOCUMENTATION",value:"false"},
  {name:"VLRGG_RESPONSE_CACHE_ENABLED",value:"true"},
  {name:"VLRGG_DEPLOY_SHA",value:$sha},
  {name:"VLRGG_UPSTASH_REDIS_REST_URL",valueFrom:{secretKeyRef:{name:"vlrgg-upstash-rest-url-production",key:"latest"}}},
  {name:"VLRGG_UPSTASH_REDIS_REST_TOKEN",valueFrom:{secretKeyRef:{name:"vlrgg-upstash-rest-token-production",key:"latest"}}}
]')"
cache_off_env='[{"name":"VLRGG_ENABLE_API_DOCUMENTATION","value":"false"},{"name":"VLRGG_RESPONSE_CACHE_ENABLED","value":"false"}]'

cat > "$work_dir/bin/gcloud" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$CASE_DIR/calls"
case "$1 $2 ${3:-}" in
  'run services list') cat "$CASE_DIR/before.json" ;;
  'run services describe')
    if test -f "$CASE_DIR/promoted"; then cat "$CASE_DIR/after-traffic.json"
    elif test -f "$CASE_DIR/deployed"; then cat "$CASE_DIR/after.json"
    else jq '.[0]' "$CASE_DIR/before.json"; fi ;;
  'run revisions describe') cat "$CASE_DIR/revision.json" ;;
  'run services get-iam-policy')
    if test -f "$CASE_DIR/promoted"; then cat "$CASE_DIR/after-traffic-policy.json"
    elif test -f "$CASE_DIR/deployed"; then cat "$CASE_DIR/after-policy.json"
    else cat "$CASE_DIR/before-policy.json"; fi ;;
  run\ deploy\ *)
    if test "$(jq length "$CASE_DIR/before.json")" = 0 && [[ " $* " == *' --no-traffic '* ]]; then
      echo 'Cannot use --no-traffic for a new service.' >&2
      exit 1
    fi
    touch "$CASE_DIR/deployed" ;;
  'run services update-traffic')
    test ! -f "$CASE_DIR/fail-promotion" || exit 1
    touch "$CASE_DIR/promoted" ;;
  *) echo 'Unexpected gcloud call.' >&2; exit 1 ;;
esac
STUB
chmod +x "$work_dir/bin/gcloud"

# Optional globals per case: CACHE_SETTING (preflight output), BEFORE_ENV (current template env),
# AFTER_ENV (template env read back after deploy). Unset means the default, cache-free deployment.
run_case() {
  local scenario="$1" expected="$2" case_id="${3:-$1}" fixture latest_ready='query-test-r123-1'
  local traffic_revision='query-test-r123-1' policy='{"bindings":[]}'
  local case_dir="$work_dir/$case_id" result=0
  mkdir -p "$case_dir"
  : > "$case_dir/calls"
  : > "$case_dir/output"
  fixture='[{"metadata":{"name":"query-test","annotations":{}},"status":{"traffic":[]}}]'
  case "$scenario" in
    new) fixture='[]' ;;
    missing-traffic) fixture="$(jq 'del(.[0].status.traffic)' <<< "$fixture")" ;;
    null-traffic) fixture="$(jq '.[0].status.traffic = null' <<< "$fixture")" ;;
    zero-traffic) fixture="$(jq '.[0].status.traffic = [{percent:0,revisionName:"failed"}]' <<< "$fixture")" ;;
    serving|staged|post-not-ready|post-digest-mismatch)
      fixture="$(jq '.[0].status = {latestReadyRevisionName:"old",traffic:[{percent:100,revisionName:"old"}]}' <<< "$fixture")" ;;
    split)
      fixture="$(jq '.[0].status.traffic = [{percent:50,revisionName:"old"},{percent:50,revisionName:"other"}]' <<< "$fixture")" ;;
    partial) fixture="$(jq '.[0].status.traffic = [{percent:50,revisionName:"old"}]' <<< "$fixture")" ;;
    ready-without-traffic) fixture="$(jq '.[0].status.latestReadyRevisionName = "old"' <<< "$fixture")" ;;
    public) policy='{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}' ;;
    authenticated) policy='{"bindings":[{"role":"roles/run.invoker","members":["allAuthenticatedUsers"]}]}' ;;
    disabled) fixture="$(jq '.[0].metadata.annotations["run.googleapis.com/invoker-iam-disabled"] = "true"' <<< "$fixture")" ;;
    empty-traffic|post-public|post-disabled) ;;
    *) echo "Unknown case: $scenario" >&2; exit 1 ;;
  esac
  if test -n "${BEFORE_ENV:-}"; then
    fixture="$(jq --argjson env "$BEFORE_ENV" \
      'map(.spec.template.spec.containers = [{image:"test@sha256:old",env:$env}])' <<< "$fixture")"
  fi
  printf '%s\n' "$fixture" > "$case_dir/before.json"
  printf '%s\n' "$policy" > "$case_dir/before-policy.json"
  printf '%s\n' '{"bindings":[]}' > "$case_dir/after-policy.json"
  printf '%s\n' '{"bindings":[]}' > "$case_dir/after-traffic-policy.json"
  # A successful untagged --no-traffic deploy can leave latestReady on the
  # serving revision even though the newly created revision itself is Ready.
  if [[ "$scenario" == serving ]]; then
    traffic_revision=old
  elif [[ "$scenario" == staged || "$scenario" == post-not-ready || "$scenario" == post-digest-mismatch ]]; then
    latest_ready=old
    traffic_revision=old
  fi
  jq -n --arg latest_ready "$latest_ready" --arg traffic_revision "$traffic_revision" \
    --argjson env "${AFTER_ENV:-$cache_off_env}" '
    {metadata:{name:"query-test",annotations:{}},
     spec:{template:{spec:{containers:[{image:"test@sha256:fixture",env:$env}]}}},
     status:{url:"https://query.example.invalid",latestCreatedRevisionName:"query-test-r123-1",
             latestReadyRevisionName:$latest_ready,
             traffic:[{percent:100,revisionName:$traffic_revision}]}}
  ' > "$case_dir/after.json"
  jq -n '
    {metadata:{name:"query-test-r123-1"},
     spec:{containers:[{image:"test@sha256:fixture"}]},
     status:{imageDigest:"test@sha256:fixture",conditions:[{type:"Ready",status:"True"}]}}
  ' > "$case_dir/revision.json"
  if [[ "$scenario" == post-not-ready ]]; then
    jq '.status.conditions[0].status = "False"' "$case_dir/revision.json" \
      > "$case_dir/changed.json"
    mv "$case_dir/changed.json" "$case_dir/revision.json"
  elif [[ "$scenario" == post-digest-mismatch ]]; then
    jq '.status.imageDigest = "test@sha256:other"' "$case_dir/revision.json" \
      > "$case_dir/changed.json"
    mv "$case_dir/changed.json" "$case_dir/revision.json"
  fi
  if [[ "$scenario" == post-public ]]; then
    printf '%s\n' '{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}' > "$case_dir/after-policy.json"
  elif [[ "$scenario" == post-disabled ]]; then
    jq '.metadata.annotations["run.googleapis.com/invoker-iam-disabled"] = "true"' \
      "$case_dir/after.json" > "$case_dir/changed.json"
    mv "$case_dir/changed.json" "$case_dir/after.json"
  fi

  env PATH="$work_dir/bin:$PATH" CASE_DIR="$case_dir" RUNNER_TEMP="$case_dir" \
    GITHUB_OUTPUT="$case_dir/output" PROJECT_ID=test REGION=test SERVICE=query-test \
    IMAGE_DIGEST=test@sha256:fixture RUNTIME_SERVICE_ACCOUNT=runtime@example.invalid \
    GITHUB_RUN_ID=123 GITHUB_RUN_ATTEMPT=1 GITHUB_SHA="$cache_sha" \
    RESPONSE_CACHE_ENABLED="${CACHE_SETTING-}" \
    bash --noprofile --norc -e -o pipefail "$work_dir/production.sh" \
      > "$case_dir/stdout" 2> "$case_dir/stderr" || result=$?

  if [[ "$expected" == reject* ]]; then
    test "$result" != 0 || { echo "FAIL: $case_id was accepted" >&2; exit 1; }
    if [[ "$expected" == reject-before-deploy ]]; then
      test ! -f "$case_dir/deployed"
      if grep -q '^first_deployment=' "$case_dir/output"; then
        echo "FAIL: $scenario emitted first_deployment" >&2
        exit 1
      fi
    else
      test -f "$case_dir/deployed"
    fi
  else
    if test "$result" != 0; then
      echo "FAIL: $scenario could not deploy" >&2
      cat "$case_dir/stderr" >&2
      exit 1
    fi
    test -f "$case_dir/deployed"
    if [[ "$scenario" == serving || "$scenario" == staged ]]; then
      grep -qx 'previous_revision=old' "$case_dir/output"
      if grep -q '^first_deployment=' "$case_dir/output"; then
        echo "FAIL: $scenario emitted first_deployment" >&2
        exit 1
      fi
      grep -q -- '--no-traffic' "$case_dir/calls"
    else
      grep -qx 'first_deployment=true' "$case_dir/output"
      if grep -q '^previous_revision=' "$case_dir/output"; then
        echo "FAIL: $scenario emitted previous_revision" >&2
        exit 1
      fi
      if grep -q -- '--no-traffic' "$case_dir/calls"; then
        echo "FAIL: $scenario used --no-traffic" >&2
        exit 1
      fi
    fi
    grep -qx 'revision=query-test-r123-1' "$case_dir/output"
    grep -Eq '^run deploy query-test .* --min=0 --max=1 --min-instances=0 --max-instances=1 --cpu-throttling --cpu-boost( |$)' "$case_dir/calls"
  fi
  echo "PASS: $case_id"
}

# Prints the deploy flags that touch environment variables or secrets, one per line, in order.
deploy_env_flags() {
  grep '^run deploy query-test ' "$work_dir/$1/calls" | tr ' ' '\n' |
    grep -E '^--(update|remove|set|clear)-(env-vars|secrets)' || true
}

expect_env_flags() {
  local case_id="$1" expected="$2"
  if test "$(deploy_env_flags "$case_id")" != "$expected"; then
    echo "FAIL: $case_id deploy flags" >&2
    deploy_env_flags "$case_id" >&2
    exit 1
  fi
  grep -qx "response_cache=$3" "$work_dir/$case_id/output"
  echo "PASS: $case_id flags"
}

for scenario in empty-traffic missing-traffic null-traffic zero-traffic new serving staged; do
  run_case "$scenario" accept
  # Without the repository variable the deploy is the previous cache-free deploy plus an explicit off.
  expect_env_flags "$scenario" "$base_env_flag,VLRGG_RESPONSE_CACHE_ENABLED=false" disabled
done

on_flags="$base_env_flag,VLRGG_RESPONSE_CACHE_ENABLED=true,VLRGG_DEPLOY_SHA=$cache_sha
$on_secret_flag"
off_remove_flags="$base_env_flag,VLRGG_RESPONSE_CACHE_ENABLED=false
--remove-secrets=VLRGG_UPSTASH_REDIS_REST_URL,VLRGG_UPSTASH_REDIS_REST_TOKEN"
literal_url='{"name":"VLRGG_UPSTASH_REDIS_REST_URL","value":"https://example.invalid"}'
secret_flag_env='[{"name":"VLRGG_RESPONSE_CACHE_ENABLED","valueFrom":{"secretKeyRef":{"name":"other","key":"1"}}}]'

CACHE_SETTING=true AFTER_ENV="$cache_on_env" run_case new accept cache-on-new
expect_env_flags cache-on-new "$on_flags" enabled
CACHE_SETTING=true BEFORE_ENV="$cache_off_env" AFTER_ENV="$cache_on_env" run_case serving accept cache-on-from-off
expect_env_flags cache-on-from-off "$on_flags" enabled
CACHE_SETTING=true BEFORE_ENV="$cache_on_env" AFTER_ENV="$cache_on_env" run_case serving accept cache-on-again
expect_env_flags cache-on-again "$on_flags" enabled
CACHE_SETTING=false BEFORE_ENV="$cache_on_env" run_case serving accept cache-off-from-on
expect_env_flags cache-off-from-on "$off_remove_flags" disabled
for setting in TRUE '' yes; do
  CACHE_SETTING="$setting" BEFORE_ENV="$cache_on_env" run_case serving accept "cache-setting-${setting:-empty}"
  expect_env_flags "cache-setting-${setting:-empty}" "$off_remove_flags" disabled
done
CACHE_SETTING=false BEFORE_ENV="$cache_off_env" run_case serving accept cache-off-again
expect_env_flags cache-off-again "$base_env_flag,VLRGG_RESPONSE_CACHE_ENABLED=false" disabled
CACHE_SETTING=false BEFORE_ENV="[$literal_url]" run_case serving accept cache-off-literal-url
expect_env_flags cache-off-literal-url "$base_env_flag,VLRGG_RESPONSE_CACHE_ENABLED=false
--remove-env-vars=VLRGG_UPSTASH_REDIS_REST_URL" disabled
CACHE_SETTING=false BEFORE_ENV="$secret_flag_env" run_case serving accept cache-off-secret-flag
expect_env_flags cache-off-secret-flag "$base_env_flag
--remove-secrets=VLRGG_RESPONSE_CACHE_ENABLED" disabled

expect_error() {
  grep -qF "::error::$2" "$work_dir/$1/stdout" || { echo "FAIL: $1 failed for another reason" >&2; exit 1; }
}

# Applies the env/secret flags a case's deploy received to a template env, the way --remove-* then
# --update-* merge into the template, so a follow-up case can start from the resulting template.
apply_env_flags() {
  local before="$1" flag result="$1"
  while IFS= read -r flag; do
    case "$flag" in
      --remove-env-vars=*|--remove-secrets=*)
        result="$(jq -c --arg names "${flag#*=}" \
          '($names | split(",")) as $n | map(select(.name as $x | $n | index($x) | not))' <<< "$result")" ;;
      --update-env-vars=*)
        result="$(jq -c --arg pairs "${flag#*=}" '
          reduce ($pairs | split(",")[] | capture("^(?<name>[^=]+)=(?<value>.*)$")) as $p (.;
            map(select(.name != $p.name)) + [{name:$p.name,value:$p.value}])' <<< "$result")" ;;
      *) echo "FAIL: apply_env_flags cannot apply $flag" >&2; exit 1 ;;
    esac
  done < <(deploy_env_flags "$2")
  printf '%s\n' "$result"
}

# A secret-sourced deploy SHA blocks enabling. Disabling removes that reference (a literal SHA stays),
# and enabling from the resulting template then succeeds.
secret_sha_env="$(jq -c '.[] |= (if .name == "VLRGG_DEPLOY_SHA"
  then {name, valueFrom:{secretKeyRef:{name:"other",key:"1"}}} else . end)' <<< "$cache_on_env")"
CACHE_SETTING=true BEFORE_ENV="$secret_sha_env" run_case serving reject-before-deploy cache-on-secret-sha
expect_error cache-on-secret-sha 'VLRGG_DEPLOY_SHA already exists with another source'
CACHE_SETTING=false BEFORE_ENV="$secret_sha_env" run_case serving accept cache-off-secret-sha
expect_env_flags cache-off-secret-sha "$base_env_flag,VLRGG_RESPONSE_CACHE_ENABLED=false
--remove-secrets=VLRGG_UPSTASH_REDIS_REST_URL,VLRGG_UPSTASH_REDIS_REST_TOKEN,VLRGG_DEPLOY_SHA" disabled
after_off_env="$(apply_env_flags "$secret_sha_env" cache-off-secret-sha)"
jq --exit-status '
  ([.[] | select(.name | test("^VLRGG_(DEPLOY_SHA|UPSTASH_REDIS_REST_URL|UPSTASH_REDIS_REST_TOKEN)$"))] | length == 0) and
  ([.[] | select(.name == "VLRGG_RESPONSE_CACHE_ENABLED")] == [{name:"VLRGG_RESPONSE_CACHE_ENABLED",value:"false"}])
' <<< "$after_off_env" > /dev/null || { echo "FAIL: cache-off-secret-sha left $after_off_env" >&2; exit 1; }
CACHE_SETTING=true BEFORE_ENV="$after_off_env" AFTER_ENV="$cache_on_env" run_case serving accept cache-on-after-secret-sha
expect_env_flags cache-on-after-secret-sha "$on_flags" enabled
# A literal SHA survives disabling: it is unused while off and overwritten on the next enable.
test "$(apply_env_flags "$cache_on_env" cache-off-from-on | jq -r '.[] | select(.name == "VLRGG_DEPLOY_SHA") | .value')" = "$cache_sha"
CACHE_SETTING=false BEFORE_ENV="$secret_sha_env" AFTER_ENV="$(jq -c '[.[] | select(.name == "VLRGG_DEPLOY_SHA")]' <<< "$secret_sha_env")" \
  run_case serving reject-after-deploy cache-off-secret-sha-not-applied
expect_error cache-off-secret-sha-not-applied 'Production response cache settings were not applied'
echo 'PASS: secret deploy SHA transition'

CACHE_SETTING=true BEFORE_ENV="[$literal_url]" run_case serving reject-before-deploy cache-on-literal-url
expect_error cache-on-literal-url 'VLRGG_UPSTASH_REDIS_REST_URL already exists with another source'
CACHE_SETTING=true BEFORE_ENV="$secret_flag_env" run_case serving reject-before-deploy cache-on-secret-flag
expect_error cache-on-secret-flag 'VLRGG_RESPONSE_CACHE_ENABLED already exists with another source'
# The stub cannot apply flags, so a stale read-back stands in for a template the deploy did not change.
CACHE_SETTING=true BEFORE_ENV="$cache_off_env" run_case serving reject-after-deploy cache-on-not-applied
expect_error cache-on-not-applied 'Production response cache settings were not applied'
CACHE_SETTING=false BEFORE_ENV="$cache_on_env" AFTER_ENV="$cache_on_env" \
  run_case serving reject-after-deploy cache-off-not-applied
expect_error cache-off-not-applied 'Production response cache settings were not applied'
for case_id in cache-on-literal-url cache-on-secret-flag cache-on-not-applied cache-off-not-applied \
  cache-on-secret-sha cache-off-secret-sha-not-applied; do
  # A bare `! grep` is exempt from errexit, so branch on the match explicitly.
  if grep -q '^response_cache=' "$work_dir/$case_id/output"; then
    echo "FAIL: $case_id reported a response cache result after rejection" >&2
    exit 1
  fi
done
for scenario in split partial ready-without-traffic public authenticated disabled; do
  run_case "$scenario" reject-before-deploy
done
for scenario in post-public post-disabled post-not-ready post-digest-mismatch; do
  run_case "$scenario" reject-after-deploy
done

run_validation_case() {
  local scenario="$1" expected="$2" result=0
  local case_dir="$work_dir/validation-$scenario"
  local fixture latest_ready=old policy='{"bindings":[]}'
  local traffic='[{"percent":100,"revisionName":"old"}]'
  mkdir -p "$case_dir"
  : > "$case_dir/calls"
  : > "$case_dir/output"
  fixture='[{"metadata":{"name":"validation-test","annotations":{}},"status":{"latestReadyRevisionName":"old","traffic":[{"percent":100,"revisionName":"old"}]}}]'
  if [[ "$scenario" == first ]]; then
    fixture='[]'
    latest_ready=validation-test-r123-1
    traffic='[]'
  elif [[ "$scenario" == public-existing ]]; then
    policy='{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}'
  elif [[ "$scenario" == disabled-existing ]]; then
    fixture="$(jq '.[0].metadata.annotations["run.googleapis.com/invoker-iam-disabled"] = "true"' <<< "$fixture")"
  fi
  printf '%s\n' "$fixture" > "$case_dir/before.json"
  printf '%s\n' "$policy" > "$case_dir/before-policy.json"
  printf '%s\n' '{"bindings":[]}' > "$case_dir/after-policy.json"
  printf '%s\n' '{"bindings":[]}' > "$case_dir/after-traffic-policy.json"

  jq -n --arg latest_ready "$latest_ready" --argjson traffic "$traffic" '
    {metadata:{name:"validation-test",annotations:{}},
     spec:{template:{spec:{containers:[{image:"test@sha256:fixture"}]}}},
     status:{url:"https://validation.example.invalid",
             latestCreatedRevisionName:"validation-test-r123-1",
             latestReadyRevisionName:$latest_ready,
             traffic:$traffic}}
  ' > "$case_dir/after.json"
  jq '.status.traffic = [{percent:100,revisionName:"validation-test-r123-1"}]' \
    "$case_dir/after.json" > "$case_dir/after-traffic.json"
  jq -n '
    {metadata:{name:"validation-test-r123-1"},
     spec:{containers:[{image:"test@sha256:fixture"}]},
     status:{imageDigest:"test@sha256:fixture",conditions:[{type:"Ready",status:"True"}]}}
  ' > "$case_dir/revision.json"

  case "$scenario" in
    not-ready) jq '.status.conditions[0].status = "False"' "$case_dir/revision.json" > "$case_dir/changed.json" ;;
    digest-mismatch) jq '.status.imageDigest = "test@sha256:other"' "$case_dir/revision.json" > "$case_dir/changed.json" ;;
    promotion-failure) : > "$case_dir/fail-promotion" ;;
    post-public) printf '%s\n' '{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}' > "$case_dir/after-policy.json" ;;
    post-disabled) jq '.metadata.annotations["run.googleapis.com/invoker-iam-disabled"] = "true"' "$case_dir/after.json" > "$case_dir/changed.json" ;;
    post-traffic-public) printf '%s\n' '{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}' > "$case_dir/after-traffic-policy.json" ;;
    first|repeated|public-existing|disabled-existing) ;;
  esac
  if test -f "$case_dir/changed.json"; then
    if [[ "$scenario" == not-ready || "$scenario" == digest-mismatch ]]; then
      mv "$case_dir/changed.json" "$case_dir/revision.json"
    else
      mv "$case_dir/changed.json" "$case_dir/after.json"
    fi
  fi

  env PATH="$work_dir/bin:$PATH" CASE_DIR="$case_dir" RUNNER_TEMP="$case_dir" \
    GITHUB_OUTPUT="$case_dir/output" PROJECT_ID=test REGION=test SERVICE=query-test \
    VALIDATION_SERVICE=validation-test IMAGE_DIGEST=test@sha256:fixture \
    RUNTIME_SERVICE_ACCOUNT=runtime@example.invalid GITHUB_RUN_ID=123 GITHUB_RUN_ATTEMPT=1 \
    GITHUB_SHA="$cache_sha" RESPONSE_CACHE_ENABLED=true \
    bash --noprofile --norc -e -o pipefail "$work_dir/validation.sh" \
      > "$case_dir/stdout" 2> "$case_dir/stderr" || result=$?

  if [[ "$expected" == accept ]]; then
    test "$result" = 0
    test -f "$case_dir/promoted"
    grep -Eq '^run deploy validation-test .* --revision-suffix r123-1( |$)' "$case_dir/calls"
    grep -Eq '^run revisions describe validation-test-r123-1( |$)' "$case_dir/calls"
    grep -q -- '--to-revisions validation-test-r123-1=100' "$case_dir/calls"
    grep -qx 'url=https://validation.example.invalid' "$case_dir/output"
    # The validation service never receives cache settings, even when production enables them.
    if grep -Eq 'RESPONSE_CACHE|UPSTASH|DEPLOY_SHA|-secrets' "$case_dir/calls"; then
      echo "FAIL: validation $scenario received response cache settings" >&2
      exit 1
    fi
  else
    test "$result" != 0 || { echo "FAIL: validation $scenario was accepted" >&2; exit 1; }
    test ! -s "$case_dir/output"
    if [[ "$expected" == reject-before-deploy ]]; then
      test ! -f "$case_dir/deployed"
    elif [[ "$expected" == reject-before-traffic ]]; then
      test -f "$case_dir/deployed"
      test ! -f "$case_dir/promoted"
      if grep -q 'run services update-traffic' "$case_dir/calls"; then
        echo "FAIL: validation $scenario moved traffic after a rejected deploy" >&2
        exit 1
      fi
    elif [[ "$expected" == reject-promotion ]]; then
      test ! -f "$case_dir/promoted"
      grep -q 'run services update-traffic' "$case_dir/calls"
    else
      test -f "$case_dir/promoted"
    fi
  fi
  echo "PASS: validation $scenario"
}

for scenario in first repeated; do
  run_validation_case "$scenario" accept
done
for scenario in public-existing disabled-existing; do
  run_validation_case "$scenario" reject-before-deploy
done
for scenario in not-ready digest-mismatch post-public post-disabled; do
  run_validation_case "$scenario" reject-before-traffic
done
run_validation_case promotion-failure reject-promotion
run_validation_case post-traffic-public reject-after-traffic

awk '
  /^      - name: Show sanitized Cloud Run diagnostics on failure$/ { step = 1; next }
  step && /^      - name:/ { exit }
  step && /^        run: \|$/ { code = 1; next }
  code { sub(/^          /, ""); print }
' "$repo_root/.github/workflows/deploy-server.yml" > "$work_dir/diagnostics.sh"
test -s "$work_dir/diagnostics.sh"
bash -n "$work_dir/diagnostics.sh"

diagnostics_dir="$work_dir/diagnostics"
mkdir -p "$diagnostics_dir"
printf '%s\n' \
  'ERROR: Logs URL: https://console.example.invalid/logs?project=sample' \
  'Service: https://sample.run.app' \
  'Image: region-docker.pkg.dev/sample/repository/image@sha256:fixture' \
  'Revision sample failed safely.' \
  > "$diagnostics_dir/cloud-run-synthetic.log"
RUNNER_TEMP="$diagnostics_dir" bash --noprofile --norc -e -o pipefail \
  "$work_dir/diagnostics.sh" > "$diagnostics_dir/output"
if grep -Eq 'https?://|\.run\.app|-docker\.pkg\.dev/|Revision sample' "$diagnostics_dir/output"; then
  echo 'FAIL: diagnostics exposed provider output' >&2
  exit 1
fi
grep -Fq 'cloud-run-synthetic.log (provider output withheld).' "$diagnostics_dir/output"
echo 'PASS: sanitized diagnostics'

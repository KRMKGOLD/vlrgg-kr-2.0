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

run_case() {
  local scenario="$1" expected="$2" fixture latest_ready='query-test-r123-1'
  local traffic_revision='query-test-r123-1' policy='{"bindings":[]}'
  local case_dir="$work_dir/$scenario" result=0
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
  jq -n --arg latest_ready "$latest_ready" --arg traffic_revision "$traffic_revision" '
    {metadata:{name:"query-test",annotations:{}},
     spec:{template:{spec:{containers:[{image:"test@sha256:fixture"}]}}},
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
    GITHUB_RUN_ID=123 GITHUB_RUN_ATTEMPT=1 \
    bash --noprofile --norc -e -o pipefail "$work_dir/production.sh" \
      > "$case_dir/stdout" 2> "$case_dir/stderr" || result=$?

  if [[ "$expected" == reject* ]]; then
    test "$result" != 0 || { echo "FAIL: $scenario was accepted" >&2; exit 1; }
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
  fi
  echo "PASS: $scenario"
}

for scenario in empty-traffic missing-traffic null-traffic zero-traffic new serving staged; do
  run_case "$scenario" accept
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
    bash --noprofile --norc -e -o pipefail "$work_dir/validation.sh" \
      > "$case_dir/stdout" 2> "$case_dir/stderr" || result=$?

  if [[ "$expected" == accept ]]; then
    test "$result" = 0
    test -f "$case_dir/promoted"
    grep -q -- '--to-revisions validation-test-r123-1=100' "$case_dir/calls"
    grep -qx 'url=https://validation.example.invalid' "$case_dir/output"
  else
    test "$result" != 0 || { echo "FAIL: validation $scenario was accepted" >&2; exit 1; }
    test ! -s "$case_dir/output"
    if [[ "$expected" == reject-before-deploy ]]; then
      test ! -f "$case_dir/deployed"
    elif [[ "$expected" == reject-before-traffic ]]; then
      test -f "$case_dir/deployed"
      test ! -f "$case_dir/promoted"
      ! grep -q 'run services update-traffic' "$case_dir/calls"
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
if grep -Eq 'https?://|\.run\.app|-docker\.pkg\.dev/' "$diagnostics_dir/output"; then
  echo 'FAIL: diagnostics exposed an operational URL or image path' >&2
  exit 1
fi
grep -Fq 'Revision sample failed safely.' "$diagnostics_dir/output"
echo 'PASS: sanitized diagnostics'

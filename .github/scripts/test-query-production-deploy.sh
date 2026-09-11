#!/usr/bin/env bash
# Runs the production workflow step against a local gcloud stub; no cloud access.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/bin"

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
    if test -f "$CASE_DIR/deployed"; then cat "$CASE_DIR/after.json"
    else jq '.[0]' "$CASE_DIR/before.json"; fi ;;
  'run services get-iam-policy')
    if test -f "$CASE_DIR/deployed"; then cat "$CASE_DIR/after-policy.json"
    else cat "$CASE_DIR/before-policy.json"; fi ;;
  "run deploy $SERVICE")
    if test "$(jq length "$CASE_DIR/before.json")" = 0 && [[ " $* " == *' --no-traffic '* ]]; then
      echo 'Cannot use --no-traffic for a new service.' >&2
      exit 1
    fi
    touch "$CASE_DIR/deployed" ;;
  *) echo 'Unexpected gcloud call.' >&2; exit 1 ;;
esac
STUB
chmod +x "$work_dir/bin/gcloud"

run_case() {
  local scenario="$1" expected="$2" fixture policy='{"bindings":[]}'
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
    serving)
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
  jq -n --arg traffic_revision "$([[ "$scenario" == serving ]] && echo old || echo query-test-r123-1)" '
    {metadata:{name:"query-test",annotations:{}},
     spec:{template:{spec:{containers:[{image:"test@sha256:fixture"}]}}},
     status:{url:"https://query.example.invalid",latestReadyRevisionName:"query-test-r123-1",
             traffic:[{percent:100,revisionName:$traffic_revision}]}}
  ' > "$case_dir/after.json"
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
    if [[ "$scenario" == serving ]]; then
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

for scenario in empty-traffic missing-traffic null-traffic zero-traffic new serving; do
  run_case "$scenario" accept
done
for scenario in split partial ready-without-traffic public authenticated disabled; do
  run_case "$scenario" reject-before-deploy
done
for scenario in post-public post-disabled; do
  run_case "$scenario" reject-after-deploy
done

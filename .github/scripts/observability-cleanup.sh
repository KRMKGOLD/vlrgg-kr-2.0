#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Observability cleanup failed: $*" >&2; exit 1; }
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
gcloud_mutate() {
  local output error
  output="$(mktemp)"; error="$(mktemp)"
  if ! gcloud "$@" > "$output" 2> "$error"; then
    rm -f "$output" "$error"
    fail 'Cloud cleanup command failed.'
  fi
  rm -f "$output" "$error"
}
for variable in PROJECT_ID REGION VALIDATION_SERVICE OBSERVABILITY_RUN; do
  test -n "${!variable:-}" || fail "Missing $variable."
done
export SERVICE_NAME="$VALIDATION_SERVICE"
command="${1:-}"
[[ "$command" == restore || "$command" == cleanup ]] \
  || fail 'Usage: observability-cleanup.sh restore|cleanup'

journal="$(.github/scripts/observability-service.sh read)" || fail 'Recovery journal is unavailable.'
if test "${OPERATION:-}" = observability-restore; then
  export OBSERVABILITY_RUN="$(jq -er '.run' <<< "$journal")"
else
  test "$(jq -er '.run' <<< "$journal")" = "$OBSERVABILITY_RUN" || fail 'Recovery journal owner mismatch.'
fi

if test "$command" = restore; then
  .github/scripts/observability-service.sh restore
  exit
fi

jq -e '.phase | IN("restoring","verified")' <<< "$journal" >/dev/null \
  || fail 'Service restoration must be verified before owned resource cleanup.'

if jq -e 'has("pending")' <<< "$journal" >/dev/null; then
  pending_kind="$(jq -er '.pending.kind' <<< "$journal")"
  pending_target="$(jq -er '.pending.target' <<< "$journal")"
  recovered=
  resource_kind=
  case "$pending_kind" in
    5xx|uptime-policy|log)
      recovered="$(.github/scripts/observability-policies.sh find-owned "$pending_kind")"
      resource_kind=policy
      ;;
    uptime)
      recovered="$(.github/scripts/observability-policies.sh find-owned uptime)"
      resource_kind=uptime
      ;;
    traffic)
      .github/scripts/observability-service.sh clear-pending traffic "$pending_target"
      journal="$(.github/scripts/observability-service.sh read)"
      pending_kind=
      ;;
    disable-policy)
      jq -e --arg name "$pending_target" 'any(.resources[]?;
        .kind == "policy" and .name == $name and .owned == true)' <<< "$journal" >/dev/null \
        || fail 'Pending policy disable is not journal-owned.'
      .github/scripts/observability-service.sh clear-pending disable-policy "$pending_target"
      journal="$(.github/scripts/observability-service.sh read)"
      pending_kind=
      ;;
    iam)
      .github/scripts/observability-service.sh clear-pending iam "$pending_target"
      journal="$(.github/scripts/observability-service.sh read)"
      pending_kind=
      ;;
    iam-add)
      policy="$(gcloud_json run services get-iam-policy "$VALIDATION_SERVICE" \
        --project "$PROJECT_ID" --region "$REGION" --format=json)"
      bindings="$(jq -c --arg member "serviceAccount:$pending_target" '
        [.bindings[]? | select(.role == "roles/run.invoker" and any(.members[]?; . == $member))]
      ' <<< "$policy")"
      test "$(jq 'length' <<< "$bindings")" -le 1 || fail 'Pending IAM binding is ambiguous.'
      if test "$(jq 'length' <<< "$bindings")" = 1; then
        jq -e '.[0] | has("condition") | not' <<< "$bindings" >/dev/null \
          || fail 'Pending IAM member appears in a conditional binding.'
        .github/scripts/observability-service.sh iam-added
      else
        .github/scripts/observability-service.sh clear-pending iam-add "$pending_target"
      fi
      journal="$(.github/scripts/observability-service.sh read)"
      pending_kind=
      ;;
    revision)
      revision_list="$(gcloud_json run revisions list --service "$VALIDATION_SERVICE" --project "$PROJECT_ID" --region "$REGION" \
        --filter "metadata.name=$pending_target" --format=json)"
      jq -e --arg name "$pending_target" 'length <= 1 and all(.[]; .metadata.name == $name)' \
        <<< "$revision_list" >/dev/null || fail 'Pending revision inventory is ambiguous.'
      if test "$(jq 'length' <<< "$revision_list")" = 1; then
        revision="$(jq -c '.[0]' <<< "$revision_list")"
        test "$pending_target" = "$VALIDATION_SERVICE-o${OBSERVABILITY_RUN%-*}-${OBSERVABILITY_RUN##*-}" \
          || fail 'Pending revision ownership is ambiguous.'
        recovered="projects/$PROJECT_ID/locations/$REGION/services/$VALIDATION_SERVICE/revisions/$pending_target"
        resource_kind=revision
      fi
      ;;
    image)
      pending_package="${pending_target%:*}"
      pending_tag="${pending_target##*:}"
      image_list="$(gcloud_json artifacts docker images list "$pending_package" --include-tags --format=json)"
      matches="$(jq -c --arg package "$pending_package" --arg tag "$pending_tag" '
        [.[] | select(.package == $package and any(.tags[]?; . == $tag))]
      ' <<< "$image_list")"
      test "$(jq 'length' <<< "$matches")" -le 1 || fail 'Pending image inventory is ambiguous.'
      if test "$(jq 'length' <<< "$matches")" = 1; then
        image="$(jq -c '.[0]' <<< "$matches")"
        jq -e '.version | test("^sha256:[0-9a-f]{64}$")' <<< "$image" >/dev/null \
          || fail 'Pending image digest is invalid.'
        recovered="$(jq -er '.package + "@" + .version' <<< "$image")"
        [[ "$recovered" == "$REGION-docker.pkg.dev/$PROJECT_ID/"*'/query-observability@sha256:'* ]] \
          || fail 'Pending image ownership is ambiguous.'
        resource_kind=image
      fi
      ;;
    *) fail 'Unknown pending mutation kind.' ;;
  esac
  if test -z "$pending_kind"; then
    :
  elif test -n "$recovered"; then
    .github/scripts/observability-service.sh resource "$resource_kind" "$recovered"
  else
    .github/scripts/observability-service.sh clear-pending "$pending_kind" "$pending_target"
  fi
  journal="$(.github/scripts/observability-service.sh read)"
fi

if jq -e '.iam.added == true' <<< "$journal" >/dev/null; then
  principal="$(jq -er '.iam.principal' <<< "$journal")"
  policy="$(gcloud_json run services get-iam-policy "$VALIDATION_SERVICE" --project "$PROJECT_ID" --region "$REGION" --format=json)"
  bindings="$(jq -c --arg member "serviceAccount:$principal" '
    [.bindings[]? | select(.role == "roles/run.invoker" and any(.members[]?; . == $member))]
  ' <<< "$policy")"
  test "$(jq 'length' <<< "$bindings")" -le 1 || fail 'Owned IAM binding is ambiguous.'
  if test "$(jq 'length' <<< "$bindings")" = 1; then
    jq -e '.[0] | has("condition") | not' <<< "$bindings" >/dev/null \
      || fail 'Owned IAM member appears in a conditional binding.'
    gcloud_mutate run services remove-iam-policy-binding "$VALIDATION_SERVICE" --quiet \
      --project "$PROJECT_ID" --region "$REGION" --role roles/run.invoker \
      --member "serviceAccount:$principal"
    policy="$(gcloud_json run services get-iam-policy "$VALIDATION_SERVICE" --project "$PROJECT_ID" --region "$REGION" --format=json)"
    jq -e --arg member "serviceAccount:$principal" '
      all(.bindings[]? | select(.role == "roles/run.invoker") | .members[]?; . != $member)
    ' <<< "$policy" >/dev/null || fail 'Owned IAM binding removal did not persist.'
  fi
  .github/scripts/observability-service.sh iam-cleared
fi

while IFS=$'\t' read -r kind name; do
  case "$kind" in
    policy)
      .github/scripts/observability-policies.sh delete "$name" policy
      ;;
    uptime)
      .github/scripts/observability-policies.sh delete "$name" uptime
      ;;
    revision)
      [[ "$name" == projects/"$PROJECT_ID"/locations/"$REGION"/services/"$VALIDATION_SERVICE"/revisions/* ]] \
        || fail 'Revision ownership path mismatch.'
      short_name="${name##*/}"
      service="$(gcloud_json run services describe "$VALIDATION_SERVICE" --project "$PROJECT_ID" --region "$REGION" --format=json)"
      jq -e --arg revision "$short_name" \
        'all(.status.traffic[]?; .revisionName != $revision and .tag == null)' <<< "$service" >/dev/null \
        || fail 'Owned revision is still referenced.'
      revision_list="$(gcloud_json run revisions list --service "$VALIDATION_SERVICE" --project "$PROJECT_ID" --region "$REGION" \
        --filter "metadata.name=$short_name" --format=json)"
      jq -e --arg name "$short_name" 'length <= 1 and all(.[]; .metadata.name == $name)' \
        <<< "$revision_list" >/dev/null || fail 'Revision inventory is ambiguous.'
      if test "$(jq 'length' <<< "$revision_list")" = 0; then
        .github/scripts/observability-service.sh drop-resource "$kind" "$name"
        continue
      fi
      revision="$(jq -c '.[0]' <<< "$revision_list")"
      test "$short_name" = "$VALIDATION_SERVICE-o${OBSERVABILITY_RUN%-*}-${OBSERVABILITY_RUN##*-}" \
        || fail 'Revision name does not prove journal ownership.'
      owned_image="$(jq -er '[.resources[] | select(.kind == "image") | .name] |
        if length == 1 then .[0] else error("Expected one owned image") end' <<< "$journal")"
      jq -e --arg image "$owned_image" '
        .status.imageDigest == $image and all(.spec.containers[]?; .image == $image)
      ' <<< "$revision" >/dev/null || fail 'Revision does not use the journal-owned overlay image.'
      gcloud_mutate run revisions delete "$short_name" --quiet --project "$PROJECT_ID" --region "$REGION"
      ;;
    image)
      [[ "$name" =~ ^${REGION}-docker\.pkg\.dev/${PROJECT_ID}/[a-z0-9._/-]+/query-observability@sha256:[0-9a-f]{64}$ ]] \
        || fail 'Image ownership path mismatch.'
      images="$(gcloud_json artifacts docker images list "${name%@sha256:*}" --include-tags --format=json)"
      matches="$(jq -c --arg image "$name" '[.[] | select((.package + "@" + .version) == $image)]' <<< "$images")"
      test "$(jq 'length' <<< "$matches")" -le 1 || fail 'Image inventory is ambiguous.'
      if test "$(jq 'length' <<< "$matches")" = 0; then
        .github/scripts/observability-service.sh drop-resource "$kind" "$name"
        continue
      fi
      jq -e --arg suffix "-$OBSERVABILITY_RUN" '
        length == 1 and (.[0].tags | type == "array" and length == 1 and .[0] | endswith($suffix))
      ' <<< "$matches" >/dev/null || fail 'Owned image tag proof is missing or shared.'
      revisions="$(gcloud_json run revisions list --project "$PROJECT_ID" --region "$REGION" --format=json)"
      jq -e --arg image "$name" 'all(.[]; all(.spec.containers[]?; .image != $image) and .status.imageDigest != $image)' \
        <<< "$revisions" >/dev/null || fail 'Owned image is still referenced by a revision.'
      gcloud_mutate artifacts docker images delete "$name" --quiet --delete-tags
      ;;
    *) fail 'Unknown journal resource kind.' ;;
  esac
  .github/scripts/observability-service.sh drop-resource "$kind" "$name"
done < <(jq -r '.resources | sort_by(
  if .kind == "policy" then 0 elif .kind == "uptime" then 1
  elif .kind == "revision" then 2 else 3 end)[] | [.kind,.name] | @tsv' <<< "$journal")

.github/scripts/observability-service.sh phase verified

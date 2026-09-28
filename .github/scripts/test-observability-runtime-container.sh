#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
umask 077

work="$(mktemp -d)"
tag="observability-proof-ci-$(basename "$work" | tr '[:upper:]' '[:lower:]')"
container=''
cleanup() {
  if test -n "$container"; then docker rm "$container" >/dev/null 2>&1 || true; fi
  docker image rm "$tag:one" "$tag:two" "$tag:base" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

mkdir -p "$work/base" "$work/overlay"
cp -R server/build/install/server "$work/base/server"
cp server/build/libs/server-observability-validation.jar "$work/overlay/validation.jar"
cp .github/scripts/observability-validation.Dockerfile "$work/overlay/Dockerfile"
# Reuse the production runtime stage with the distribution already built by CI.
# This exercises the real OS/JRE and overlay without rebuilding Gradle in Docker.
python3 - "$work/base/Dockerfile" <<'PY'
from pathlib import Path
import sys

source = Path('Dockerfile').read_text()
marker = 'FROM eclipse-temurin:21-jre\n'
copy = 'COPY --from=build --chown=app:app /workspace/server/build/install/server ./'
assert source.count(marker) == 1 and source.count(copy) == 1
runtime = marker + source.split(marker, 1)[1]
Path(sys.argv[1]).write_text(runtime.replace(copy, 'COPY --chown=app:app server ./'))
PY

docker build --quiet --tag "$tag:base" "$work/base" >/dev/null
for build in one two; do
  docker build --quiet --no-cache --build-arg "BASE_IMAGE=$tag:base" \
    --tag "$tag:$build" "$work/overlay" >/dev/null
  # Neither the application nor the OOM endpoint is started in this test.
  container="$(docker create "$tag:$build")"
  docker export --output "$work/rootfs.tar" "$container"
  docker image inspect "$tag:$build" > "$work/config.json"
  python3 .github/scripts/observability-runtime-proof.py \
    --rootfs "$work/rootfs.tar" --config "$work/config.json" \
    --dockerfile .github/scripts/observability-validation.Dockerfile \
    --output "$work/$build.json"
  docker rm "$container" >/dev/null
  container=''
done

python3 - "$work" <<'PY'
import json
from pathlib import Path
import sys

root = Path(sys.argv[1])
one, two = (json.loads((root / name).read_text()) for name in ('one.json', 'two.json'))
assert one['applicabilitySha256'] == two['applicabilitySha256'], 'Rebuilt image applicability differs'
entries = {entry['path']: entry for entry in one['rootfsEntries']}
assert entries['app/validation.jar']['contentKind'] == 'normalizedZip'
assert any(path.startswith('app/lib/') and entry.get('contentKind') == 'normalizedZip'
           for path, entry in entries.items())
assert any(path.startswith('opt/java/openjdk/') and entry.get('type') == 'file'
           for path, entry in entries.items())
print('PASS real rebuilt validation images have identical OS/JRE/application applicability; no container started')
PY

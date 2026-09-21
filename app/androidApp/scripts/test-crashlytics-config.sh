#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
cd "$repo_root"
test_dir=$(mktemp -d)
exec 3>&2
trap 'status=$?; if [ "$status" -ne 0 ]; then tail -n 60 "$test_dir"/*.log >&3; fi; rm -rf "$test_dir"' EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
unset FIREBASE_ANDROID_CONFIG_FILE FIREBASE_ANDROID_CONFIG_SOURCE FIREBASE_ANDROID_CONFIG_BASE64
unset FIREBASE_CRASHLYTICS_DEBUG_ENABLED

gradle() {
    ./gradlew --no-daemon --no-configuration-cache --no-build-cache --quiet "$@"
}

check_debug_policy() {
    python3 - "$1" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

expected = sys.argv[1]
root = pathlib.Path('app/androidApp/build')
manifest = ET.parse(root / 'intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml')
android = '{http://schemas.android.com/apk/res/android}'
application = manifest.getroot().find('application')
assert not any(p.get(android + 'name') == 'com.google.firebase.provider.FirebaseInitProvider' for p in application.findall('provider'))
metadata = {m.get(android + 'name'): m.get(android + 'value') for m in application.findall('meta-data')}
assert metadata['firebase_crashlytics_collection_enabled'] == expected
config = (root / 'generated/source/buildConfig/debug/kr/co/cotton/vlrgg_mobile/BuildConfig.java').read_text()
assert f'CRASHLYTICS_COLLECTION_ENABLED = {expected};' in config
PY
}

gradle :app:androidApp:processDebugMainManifest :app:androidApp:generateDebugBuildConfig >"$test_dir/build.log" 2>&1
check_debug_policy false

if FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES gradle :app:androidApp:preDebugBuild >"$test_dir/missing.log" 2>&1; then
    echo 'Expected opted-in Debug without Firebase configuration to fail.' >&2
    exit 1
fi
grep -Fq 'Firebase configuration is required.' "$test_dir/missing.log"
unset FIREBASE_CRASHLYTICS_DEBUG_ENABLED

# Synthetic identifiers exercise the real Gradle plugins without contacting Firebase.
FIREBASE_ANDROID_CONFIG_BASE64=$(python3 - <<'PY'
import base64
import json
config = {
    'project_info': {'project_number': '123456789', 'project_id': 'demo-vlrgg-crashlytics'},
    'client': [{
        'client_info': {'mobilesdk_app_id': '1:123456789:android:0123456789abcdef',
                        'android_client_info': {'package_name': 'kr.co.cotton.vlrgg_mobile'}},
        'api_key': [{'current_key': 'AIzaSySyntheticConfigurationForBuildChecks'}],
    }],
    'configuration_version': '1',
}
print(base64.b64encode(json.dumps(config).encode()).decode())
PY
)
export FIREBASE_ANDROID_CONFIG_BASE64
FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES python3 scripts/firebase/with_config.py android -- \
    ./gradlew --no-daemon --no-configuration-cache --no-build-cache --quiet \
    :app:androidApp:processDebugMainManifest :app:androidApp:generateDebugBuildConfig \
    :app:androidApp:processDebugGoogleServices >"$test_dir/injected.log" 2>&1
check_debug_policy true

unset FIREBASE_ANDROID_CONFIG_BASE64
gradle :app:androidApp:processDebugMainManifest :app:androidApp:generateDebugBuildConfig >"$test_dir/reset.log" 2>&1
check_debug_policy false
test ! -f app/androidApp/google-services.json
printf 'Android Crashlytics configuration checks passed.\n'

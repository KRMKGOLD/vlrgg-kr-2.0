#!/bin/sh

set -eu
umask 077

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
temp_dir=$(mktemp -d)
cleanup() {
    status=$?
    trap - EXIT HUP INT TERM
    rm -rf "$temp_dir"
    exit "$status"
}
trap cleanup EXIT HUP INT TERM

expect_failure() {
    if "$@" >"$temp_dir/stdout" 2>"$temp_dir/stderr"; then
        echo "expected command to fail" >&2
        exit 1
    fi
}

mkdir -p "$temp_dir/build/App.app" "$temp_dir/Build/Products" "$temp_dir/build/App.app.dSYM"
info="$temp_dir/build/App.app/Info.plist"
config="$temp_dir/GoogleService-Info.plist"
cat >"$info" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0"><dict/></plist>
PLIST
cat >"$config" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0"><dict>
<key>BUNDLE_ID</key><string>kr.co.cotton.vlrggmobile</string>
<key>GOOGLE_APP_ID</key><string>1:123:ios:abc</string>
</dict></plist>
PLIST

run_prepare() {
    env \
        TARGET_BUILD_DIR="$temp_dir/build" \
        UNLOCALIZED_RESOURCES_FOLDER_PATH=App.app \
        INFOPLIST_PATH=App.app/Info.plist \
        PRODUCT_BUNDLE_IDENTIFIER=kr.co.cotton.vlrggmobile \
        "$@" "$script_dir/firebase_crashlytics.sh" prepare
}

run_prepare CONFIGURATION=Debug
test ! -e "$temp_dir/build/App.app/GoogleService-Info.plist"
test "$(plutil -extract FIREBASE_CRASHLYTICS_ENABLED raw "$info")" = false

cp "$config" "$temp_dir/build/App.app/GoogleService-Info.plist"
run_prepare CONFIGURATION=Debug
test ! -e "$temp_dir/build/App.app/GoogleService-Info.plist"

run_prepare CONFIGURATION=Debug FIREBASE_IOS_CONFIG_FILE="$config"
test -f "$temp_dir/build/App.app/GoogleService-Info.plist"
test "$(plutil -extract FIREBASE_CRASHLYTICS_ENABLED raw "$info")" = false

run_prepare CONFIGURATION=Debug FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES FIREBASE_IOS_CONFIG_FILE="$config"
test "$(plutil -extract FIREBASE_CRASHLYTICS_ENABLED raw "$info")" = true
expect_failure run_prepare CONFIGURATION=Debug FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES
expect_failure run_prepare CONFIGURATION=Release PLATFORM_NAME=iphoneos CODE_SIGNING_ALLOWED=NO FIREBASE_ALLOW_UNCONFIGURED=YES
run_prepare CONFIGURATION=Release PLATFORM_NAME=iphonesimulator CODE_SIGNING_ALLOWED=NO FIREBASE_ALLOW_UNCONFIGURED=YES
run_prepare CONFIGURATION=Release FIREBASE_IOS_CONFIG_FILE="$config"
test "$(plutil -extract FirebaseCrashlyticsCollectionEnabled raw "$info")" = true

fake_upload="$temp_dir/upload-symbols"
cat >"$fake_upload" <<'SCRIPT'
#!/bin/sh
set -eu
test "$1" = -gsp
test "$2" = "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/GoogleService-Info.plist"
test "$3" = -p
test "$4" = ios
test "$5" = "$DWARF_DSYM_FOLDER_PATH/$DWARF_DSYM_FILE_NAME"
test "${FIREBASE_TEST_UPLOAD_FAIL:-}" != YES
: >"$FIREBASE_TEST_UPLOAD_MARKER"
SCRIPT
chmod +x "$fake_upload"
marker="$temp_dir/uploaded"
run_upload() {
    env \
        TARGET_BUILD_DIR="$temp_dir/build" \
        UNLOCALIZED_RESOURCES_FOLDER_PATH=App.app \
        INFOPLIST_PATH=App.app/Info.plist \
        BUILD_DIR="$temp_dir/Build/Products" \
        DWARF_DSYM_FOLDER_PATH="$temp_dir/build" \
        DWARF_DSYM_FILE_NAME=App.app.dSYM \
        FIREBASE_CRASHLYTICS_UPLOAD_SYMBOLS="$fake_upload" \
        FIREBASE_TEST_UPLOAD_MARKER="$marker" \
        "$@" "$script_dir/firebase_crashlytics.sh" upload
}

run_upload CONFIGURATION=Release
test -f "$marker"
expect_failure run_upload CONFIGURATION=Release FIREBASE_TEST_UPLOAD_FAIL=YES

rm "$marker"
run_upload CONFIGURATION=Debug
test ! -e "$marker"

echo "iOS Firebase Crashlytics checks passed"

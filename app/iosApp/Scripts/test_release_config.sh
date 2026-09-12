#!/bin/sh

set -eu
umask 077

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
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
    test ! -s "$temp_dir/stdout"
}

release_config="$temp_dir/Release.xcconfig"
API_BASE_URL=https://example.invalid \
APP_VERSION=1.2.3 \
APP_BUILD_NUMBER=42 \
IOS_TEAM_ID=ABCDE12345 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$release_config"

test "$(stat -f %Lp "$release_config")" = 600
grep -F 'API_BASE_URL=https:$(SLASH)$(SLASH)example.invalid' "$release_config" >/dev/null
! grep -F 'https://' "$release_config" >/dev/null

build_settings="$temp_dir/build-settings"
xcodebuild \
    -project "$project_dir/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -xcconfig "$release_config" \
    -showBuildSettings >"$build_settings" 2>"$temp_dir/xcodebuild-stderr"

setting() {
    awk -F ' = ' -v key="$1" '$1 ~ "^[[:space:]]*" key "$" { value = $2 } END { print value }' "$build_settings"
}

test "$(setting API_BASE_URL)" = https://example.invalid
test "$(setting MARKETING_VERSION)" = 1.2.3
test "$(setting CURRENT_PROJECT_VERSION)" = 42
test "$(setting PRODUCT_BUNDLE_IDENTIFIER)" = kr.co.cotton.vlrggmobile

for origin in 'https://example.invalid:1' 'https://example.invalid:65535' 'https://[::1]' 'https://[::1]:443'; do
    API_BASE_URL="$origin" APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
        /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/valid-origin.xcconfig"
done

default_release_settings="$temp_dir/default-release-settings"
xcodebuild \
    -project "$project_dir/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -showBuildSettings >"$default_release_settings" 2>"$temp_dir/default-release-stderr"
! grep -E '^[[:space:]]*(API_BASE_URL|MARKETING_VERSION|CURRENT_PROJECT_VERSION) = .+' "$default_release_settings" >/dev/null

debug_settings="$temp_dir/debug-settings"
xcodebuild \
    -project "$project_dir/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Debug \
    -destination 'generic/platform=iOS Simulator' \
    -showBuildSettings >"$debug_settings" 2>"$temp_dir/debug-stderr"
grep -F 'API_BASE_URL = http://127.0.0.1:8080' "$debug_settings" >/dev/null
grep -F 'MARKETING_VERSION = 1.0' "$debug_settings" >/dev/null
grep -F 'CURRENT_PROJECT_VERSION = 1' "$debug_settings" >/dev/null

final_plist="$temp_dir/Info.plist"
cp "$project_dir/iosApp/Info.plist" "$final_plist"
plutil -replace API_BASE_URL -string "$(setting API_BASE_URL)" "$final_plist"
plutil -insert CFBundleShortVersionString -string "$(setting MARKETING_VERSION)" "$final_plist"
plutil -insert CFBundleVersion -string "$(setting CURRENT_PROJECT_VERSION)" "$final_plist"
/usr/bin/xcrun swift "$script_dir/release_config.swift" validate-plist "$final_plist" Release

plutil -replace API_BASE_URL -string http://127.0.0.1:8080 "$final_plist"
/usr/bin/xcrun swift "$script_dir/release_config.swift" validate-plist "$final_plist" Debug
expect_failure /usr/bin/xcrun swift "$script_dir/release_config.swift" validate-plist "$final_plist" Release

expect_failure env -u API_BASE_URL APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/missing.xcconfig"
expect_failure env API_BASE_URL= APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/blank.xcconfig"
expect_failure env API_BASE_URL=not-a-url APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/malformed.xcconfig"
expect_failure env API_BASE_URL=https://example.invalid:0 APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/port-zero.xcconfig"
expect_failure env API_BASE_URL=https://example.invalid:65536 APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/port-large.xcconfig"
expect_failure env API_BASE_URL='https://exa%mple.invalid' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/host-percent.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid/%ZZ' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/path-percent.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid"' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/quote.xcconfig"
expect_failure env API_BASE_URL=http://example.invalid APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/http.xcconfig"
expect_failure env API_BASE_URL='https://user@example.invalid' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/user.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid?query=value' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/query.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid#fragment' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/fragment.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid/$unsafe' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/expansion.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid/(unsafe)' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/parentheses.xcconfig"
expect_failure env API_BASE_URL='https://example.invalid/path' APP_VERSION=1.0 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/path.xcconfig"
expect_failure env API_BASE_URL=https://example.invalid APP_VERSION=1.0 APP_BUILD_NUMBER=0 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/build.xcconfig"
expect_failure env -u APP_VERSION API_BASE_URL=https://example.invalid APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/missing-version.xcconfig"
expect_failure env API_BASE_URL=https://example.invalid APP_VERSION=1.2.3.4 APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/version.xcconfig"
expect_failure env API_BASE_URL=https://example.invalid "APP_VERSION=1.2
" APP_BUILD_NUMBER=1 \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/version-newline.xcconfig"
expect_failure env API_BASE_URL=https://example.invalid APP_VERSION=1.0 APP_BUILD_NUMBER=1 IOS_TEAM_ID=invalid \
    /usr/bin/xcrun swift "$script_dir/release_config.swift" generate "$temp_dir/team.xcconfig"

plutil -lint "$project_dir/iosApp/Info.plist" "$project_dir/iosApp.xcodeproj/project.pbxproj" >/dev/null
grep -F '$(TARGET_BUILD_DIR)/$(INFOPLIST_PATH)' "$project_dir/iosApp.xcodeproj/project.pbxproj" >/dev/null
grep -F '/usr/bin/env -u SDKROOT /usr/bin/xcrun --sdk macosx swift' "$project_dir/iosApp.xcodeproj/project.pbxproj" >/dev/null

echo "iOS release configuration checks passed"

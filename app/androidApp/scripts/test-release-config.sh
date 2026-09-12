#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
gradlew="$repo_root/gradlew"
log_file=$(mktemp)
test_dir=$(mktemp -d)
keystore_file=$(mktemp "$test_dir/release-test.XXXXXX")
trap 'rm -f "$log_file"; rm -rf "$test_dir"' EXIT HUP INT TERM

run_release() {
    env \
        -u API_BASE_URL \
        -u APP_VERSION \
        -u APP_BUILD_NUMBER \
        -u ANDROID_KEYSTORE_PATH \
        -u ANDROID_KEYSTORE_PASSWORD \
        -u ANDROID_KEY_ALIAS \
        -u ANDROID_KEY_PASSWORD \
        "$@"
}

run_valid_signing() {
    api_base_url=$1
    app_version=$2
    app_build_number=$3
    shift 3
    run_release \
        API_BASE_URL="$api_base_url" \
        APP_VERSION="$app_version" \
        APP_BUILD_NUMBER="$app_build_number" \
        ANDROID_KEYSTORE_PATH="$keystore_file" \
        ANDROID_KEYSTORE_PASSWORD=test-password \
        ANDROID_KEY_ALIAS=test-alias \
        ANDROID_KEY_PASSWORD=test-password \
        "$@"
}

expect_failure() {
    label=$1
    expected=$2
    shift 2
    if "$@" >"$log_file" 2>&1; then
        printf 'expected failure: %s\n' "$label" >&2
        exit 1
    fi
    if ! grep -Fq -- "$expected" "$log_file"; then
        printf 'wrong failure for %s\n' "$label" >&2
        sed -n '1,120p' "$log_file" >&2
        exit 1
    fi
    if grep -Fq -- 'test-password' "$log_file"; then
        printf 'sensitive input was echoed for %s\n' "$label" >&2
        exit 1
    fi
}

validation_task=:app:androidApp:validateReleaseConfiguration
url_error='Android Release API_BASE_URL must be a valid HTTPS URL without credentials, query, or fragment.'
signing_error='Android Release requires all four ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD values.'

expect_failure "missing URL" 'Android Release requires API_BASE_URL.' \
    run_valid_signing '' 1.0 1 "$gradlew" --quiet :app:androidApp:bundleRelease
expect_failure "malformed URL" "$url_error" \
    run_valid_signing 'not a URL' 1.0 1 "$gradlew" --quiet "$validation_task"
expect_failure "HTTP URL" "$url_error" \
    run_valid_signing 'http://example.invalid' 1.0 1 "$gradlew" --quiet "$validation_task"
expect_failure "URL credentials" "$url_error" \
    run_valid_signing 'https://user@example.invalid' 1.0 1 "$gradlew" --quiet "$validation_task"
expect_failure "URL query" "$url_error" \
    run_valid_signing 'https://example.invalid?debug=true' 1.0 1 "$gradlew" --quiet "$validation_task"
expect_failure "URL fragment" "$url_error" \
    run_valid_signing 'https://example.invalid#fragment' 1.0 1 "$gradlew" --quiet "$validation_task"
for port in 0 65536; do
    expect_failure "invalid URL port" "$url_error" \
        run_valid_signing "https://example.invalid:$port" 1.0 1 "$gradlew" --quiet "$validation_task"
done
expect_failure "missing version" 'Android Release requires a valid APP_VERSION.' \
    run_valid_signing 'https://example.invalid' '' 1 "$gradlew" --quiet "$validation_task"
expect_failure "invalid build number" 'Android Release requires APP_BUILD_NUMBER as a positive integer.' \
    run_valid_signing 'https://example.invalid' 1.0 0 "$gradlew" --quiet "$validation_task"
expect_failure "Play build number ceiling" 'Android Release requires APP_BUILD_NUMBER as a positive integer.' \
    run_valid_signing 'https://example.invalid' 1.0 2100000001 "$gradlew" --quiet "$validation_task"
run_valid_signing 'https://example.invalid:65535' 1.0 2100000000 \
    "$gradlew" --quiet "$validation_task"
expect_failure "partial signing input" "$signing_error" \
    run_release \
        API_BASE_URL='https://example.invalid' \
        APP_VERSION=1.0 \
        APP_BUILD_NUMBER=1 \
        ANDROID_KEYSTORE_PATH="$keystore_file" \
        "$gradlew" --quiet "$validation_task"
expect_failure "missing keystore file" 'Android Release keystore file is missing or invalid.' \
    run_release \
        API_BASE_URL='https://example.invalid' \
        APP_VERSION=1.0 \
        APP_BUILD_NUMBER=1 \
        ANDROID_KEYSTORE_PATH="$test_dir/missing.keystore" \
        ANDROID_KEYSTORE_PASSWORD=test-password \
        ANDROID_KEY_ALIAS=test-alias \
        ANDROID_KEY_PASSWORD=test-password \
        "$gradlew" --quiet "$validation_task"

run_valid_signing 'https://example.invalid' 2.3 42 \
    "$gradlew" --quiet --rerun-tasks :app:androidApp:generateReleaseBuildConfig

build_config="$repo_root/app/androidApp/build/generated/source/buildConfig/release/kr/co/cotton/vlrgg_mobile/BuildConfig.java"
grep -Fq 'public static final String API_BASE_URL = "https://example.invalid";' "$build_config"
grep -Fq 'public static final int VERSION_CODE = 42;' "$build_config"
grep -Fq 'public static final String VERSION_NAME = "2.3";' "$build_config"

run_release API_BASE_URL='https://example.invalid' \
    "$gradlew" --quiet --rerun-tasks :app:androidApp:generateDebugBuildConfig
debug_build_config="$repo_root/app/androidApp/build/generated/source/buildConfig/debug/kr/co/cotton/vlrgg_mobile/BuildConfig.java"
grep -Fq 'public static final String API_BASE_URL = "https://example.invalid";' "$debug_build_config"

run_release API_BASE_URL='https://example.invalid/"\path' \
    "$gradlew" --quiet --rerun-tasks :app:androidApp:generateDebugBuildConfig
grep -Fq 'public static final String API_BASE_URL = "https://example.invalid/\"\\path";' "$debug_build_config"
javac -d "$test_dir" "$debug_build_config"

printf 'Android Release configuration checks passed.\n'

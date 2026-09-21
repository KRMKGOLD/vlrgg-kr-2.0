#!/bin/sh

set -eu

built_resources="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
built_config="$built_resources/GoogleService-Info.plist"
built_info="$TARGET_BUILD_DIR/$INFOPLIST_PATH"

is_simulator() {
    test "${PLATFORM_NAME:-}" = iphonesimulator ||
        test "${EFFECTIVE_PLATFORM_NAME:-}" = -iphonesimulator ||
        case "${SDK_NAME:-}" in iphonesimulator*) return 0 ;; *) return 1 ;; esac
}

allows_unconfigured_release() {
    test "${FIREBASE_ALLOW_UNCONFIGURED:-}" = YES &&
        test "${CODE_SIGNING_ALLOWED:-}" = NO &&
        is_simulator
}

debug_collection_enabled() {
    test "$CONFIGURATION" = Debug &&
        test "${FIREBASE_CRASHLYTICS_DEBUG_ENABLED:-}" = YES
}

validate_config() {
    config=$1
    case "$config" in /*) ;; *) echo "error: FIREBASE_IOS_CONFIG_FILE must be an absolute path." >&2; exit 1 ;; esac
    test -f "$config" || { echo "error: Firebase iOS configuration is not a regular file." >&2; exit 1; }
    plutil -lint "$config" >/dev/null
    bundle_id=$(/usr/libexec/PlistBuddy -c 'Print :BUNDLE_ID' "$config" 2>/dev/null || true)
    google_app_id=$(/usr/libexec/PlistBuddy -c 'Print :GOOGLE_APP_ID' "$config" 2>/dev/null || true)
    test -n "$google_app_id" || { echo "error: Firebase iOS configuration is missing GOOGLE_APP_ID." >&2; exit 1; }
    test "$bundle_id" = "$PRODUCT_BUNDLE_IDENTIFIER" || {
        echo "error: Firebase BUNDLE_ID does not match PRODUCT_BUNDLE_IDENTIFIER." >&2
        exit 1
    }
}

prepare() {
    rm -f "$built_config"

    enabled=false
    if test -n "${FIREBASE_IOS_CONFIG_FILE:-}"; then
        validate_config "$FIREBASE_IOS_CONFIG_FILE"
        mkdir -p "$built_resources"
        cp "$FIREBASE_IOS_CONFIG_FILE" "$built_config"
        chmod 0644 "$built_config"
        if test "$CONFIGURATION" = Release || debug_collection_enabled; then
            enabled=true
        fi
    elif debug_collection_enabled; then
        echo "error: Firebase configuration is required when Debug Crashlytics validation is enabled." >&2
        exit 1
    elif test "$CONFIGURATION" = Release && ! allows_unconfigured_release; then
        echo "error: Firebase configuration is required for Release device builds." >&2
        exit 1
    fi

    plutil -replace FirebaseCrashlyticsCollectionEnabled -bool "$enabled" "$built_info"
    plutil -replace FIREBASE_CRASHLYTICS_ENABLED -bool "$enabled" "$built_info"
}

upload() {
    if test "$CONFIGURATION" = Debug && ! debug_collection_enabled; then
        exit 0
    fi
    if test ! -f "$built_config"; then
        allows_unconfigured_release && exit 0
        echo "error: Firebase configuration is missing from the built app." >&2
        exit 1
    fi

    dsym="$DWARF_DSYM_FOLDER_PATH/$DWARF_DSYM_FILE_NAME"
    test -d "$dsym" || { echo "error: Crashlytics dSYM was not found." >&2; exit 1; }
    upload_symbols=${FIREBASE_CRASHLYTICS_UPLOAD_SYMBOLS:-"${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/upload-symbols"}
    test -x "$upload_symbols" || { echo "error: Firebase Crashlytics upload-symbols was not found." >&2; exit 1; }
    "$upload_symbols" -gsp "$built_config" -p ios "$dsym"
}

case "${1:-}" in
    prepare) prepare ;;
    upload) upload ;;
    *) echo "error: Usage: firebase_crashlytics.sh prepare|upload" >&2; exit 1 ;;
esac

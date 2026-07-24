package kr.co.cotton.vlrgg_mobile.common.http

/** Canonical positive-decimal identifier accepted by public VLR path routes. */
internal const val POSITIVE_DECIMAL_ID_REGEX = "[1-9][0-9]{0,9}"
internal const val POSITIVE_DECIMAL_ID_OPENAPI_PATTERN = "^$POSITIVE_DECIMAL_ID_REGEX$"

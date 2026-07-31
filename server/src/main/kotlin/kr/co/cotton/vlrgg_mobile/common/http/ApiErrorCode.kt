package kr.co.cotton.vlrgg_mobile.common.http

import kotlinx.serialization.Serializable

/** Stable, machine-readable codes for every server error response. */
@Serializable
enum class ApiErrorCode {
    INVALID_REQUEST,
    NOT_FOUND,
    UPSTREAM_NETWORK_FAILURE,
    SOURCE_PARSING_FAILURE,
    INTERNAL_ERROR,
    REQUEST_TOO_LARGE,
    REVISION_CONFLICT,
    REVISION_EXHAUSTED,
    SUBSCRIPTION_LIMIT,
    ACTIVE_MATCH_CAPACITY_EXCEEDED,
    APP_ATTESTATION_FAILED,
    TARGET_AUTHENTICATION_FAILED,
}

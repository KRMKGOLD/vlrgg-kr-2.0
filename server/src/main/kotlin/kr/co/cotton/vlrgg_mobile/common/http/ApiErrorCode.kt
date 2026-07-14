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
}

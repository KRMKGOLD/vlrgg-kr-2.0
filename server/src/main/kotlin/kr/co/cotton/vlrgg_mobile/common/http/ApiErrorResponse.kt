package kr.co.cotton.vlrgg_mobile.common.http

import kotlinx.serialization.Serializable

/** A safe error envelope that does not expose upstream or exception details. */
@Serializable
data class ApiErrorResponse(
    val code: ApiErrorCode,
    val message: String,
)

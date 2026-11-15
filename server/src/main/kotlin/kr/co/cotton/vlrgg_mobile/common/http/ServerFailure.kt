package kr.co.cotton.vlrgg_mobile.common.http

import io.ktor.http.*

private const val PRIMARY_VLR_UPSTREAM_HOST = "www.vlr.gg"
private const val ALTERNATE_VLR_UPSTREAM_HOST = "vlr.gg"

internal sealed class ServerFailure(cause: Exception? = null) : RuntimeException(null, cause) {
    abstract val errorCode: ApiErrorCode
    abstract val status: HttpStatusCode
    abstract val safeMessage: String
}

internal class InvalidInputFailure(cause: Exception? = null) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.INVALID_REQUEST
    override val status = HttpStatusCode.BadRequest
    override val safeMessage = "Request input is invalid."
}

internal class UpstreamNetworkFailure(cause: Exception? = null) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.UPSTREAM_NETWORK_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to retrieve data from the upstream source."
}

internal class SourceParsingFailure(cause: Exception) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.SOURCE_PARSING_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to parse data from the upstream source."
}

internal class InternalServerFailure(cause: Exception) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.INTERNAL_ERROR
    override val status = HttpStatusCode.InternalServerError
    override val safeMessage = "An unexpected server error occurred."
}

internal sealed class RetryableServerFailure(cause: Exception? = null) : ServerFailure(cause = cause) {
    abstract val retryAfterSeconds: Int
}

internal class RateLimitedFailure : RetryableServerFailure() {
    override val errorCode = ApiErrorCode.RATE_LIMITED
    override val status = HttpStatusCode.TooManyRequests
    override val safeMessage = "Request rate is temporarily limited."
    override val retryAfterSeconds = 1
}

internal class ServerBusyFailure : RetryableServerFailure() {
    override val errorCode = ApiErrorCode.SERVER_BUSY
    override val status = HttpStatusCode.ServiceUnavailable
    override val safeMessage = "Server capacity is temporarily unavailable."
    override val retryAfterSeconds = 2
}

internal class RequestTooLargeFailure : ServerFailure() {
    override val errorCode = ApiErrorCode.REQUEST_TOO_LARGE
    override val status = HttpStatusCode.PayloadTooLarge
    override val safeMessage = "Request input is too large."
}

internal class RequestHeadersTooLargeFailure : ServerFailure() {
    override val errorCode = ApiErrorCode.REQUEST_TOO_LARGE
    override val status = HttpStatusCode(431, "Request Header Fields Too Large")
    override val safeMessage = "Request headers are too large."
}

internal class RequestDeadlineFailure : ServerFailure() {
    override val errorCode = ApiErrorCode.REQUEST_TIMEOUT
    override val status = HttpStatusCode.GatewayTimeout
    override val safeMessage = "Request processing timed out."
}

internal class PublicResponseTooLargeFailure(cause: Exception) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.RESPONSE_TOO_LARGE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Response data is too large."
}

internal fun ServerFailure.toApiErrorResponse() = ApiErrorResponse(
    code = errorCode,
    message = safeMessage,
)

/** Both VLR.GG HTTPS origins are valid direct targets; redirects between them are never followed. */
internal fun Url.isAllowedVlrUpstreamUrl(): Boolean =
    protocol == URLProtocol.HTTPS &&
        port == URLProtocol.HTTPS.defaultPort &&
        user.isNullOrEmpty() &&
        password.isNullOrEmpty() &&
        (host.equals(PRIMARY_VLR_UPSTREAM_HOST, ignoreCase = true) ||
            host.equals(ALTERNATE_VLR_UPSTREAM_HOST, ignoreCase = true))

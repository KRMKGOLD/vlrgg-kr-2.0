package kr.co.cotton.vlrgg_mobile.common.http

import io.ktor.http.*

private const val CANONICAL_VLR_UPSTREAM_LOG_URL = "https://www.vlr.gg/"
private const val PRIMARY_VLR_UPSTREAM_HOST = "www.vlr.gg"
private const val ALTERNATE_VLR_UPSTREAM_HOST = "vlr.gg"

internal sealed class ServerFailure(
    upstreamUrl: Url? = null,
    cause: Exception? = null,
) : RuntimeException(null, cause) {
    internal val canonicalUpstreamUrl: String? = upstreamUrl?.toSafeCanonicalUpstreamUrl()
    abstract val errorCode: ApiErrorCode
    abstract val status: HttpStatusCode
    abstract val safeMessage: String
}

internal class InvalidInputFailure(cause: Exception? = null) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.INVALID_REQUEST
    override val status = HttpStatusCode.BadRequest
    override val safeMessage = "Request input is invalid."
}

internal class UpstreamNetworkFailure(
    upstreamUrl: Url,
    cause: Exception? = null,
) : ServerFailure(upstreamUrl = upstreamUrl, cause = cause) {
    override val errorCode = ApiErrorCode.UPSTREAM_NETWORK_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to retrieve data from the upstream source."
}

internal class SourceParsingFailure(
    upstreamUrl: Url,
    cause: Exception,
) : ServerFailure(upstreamUrl = upstreamUrl, cause = cause) {
    init {
        require(upstreamUrl.host.isNotBlank()) { "Upstream URL must include a host." }
    }

    override val errorCode = ApiErrorCode.SOURCE_PARSING_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to parse data from the upstream source."
}

internal class InternalServerFailure(cause: Exception) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.INTERNAL_ERROR
    override val status = HttpStatusCode.InternalServerError
    override val safeMessage = "An unexpected server error occurred."
}

internal fun ServerFailure.toApiErrorResponse() = ApiErrorResponse(
    code = errorCode,
    message = safeMessage,
)

/**
 * Redacts request-derived URL components before they enter a server log.
 *
 * The common server only contacts VLR.GG.  A fixed primary origin keeps user-info, query,
 * fragment, and path values out of logs even if a future feature builds a URL from input.
 */
internal fun Url.toSafeCanonicalUpstreamUrl(): String = CANONICAL_VLR_UPSTREAM_LOG_URL

/** Both VLR.GG HTTPS origins are valid direct targets; redirects between them are never followed. */
internal fun Url.isAllowedVlrUpstreamUrl(): Boolean =
    protocol == URLProtocol.HTTPS &&
        port == URLProtocol.HTTPS.defaultPort &&
        user.isNullOrEmpty() &&
        password.isNullOrEmpty() &&
        (host.equals(PRIMARY_VLR_UPSTREAM_HOST, ignoreCase = true) ||
            host.equals(ALTERNATE_VLR_UPSTREAM_HOST, ignoreCase = true))

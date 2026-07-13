package kr.co.cotton.vlrgg_mobile.common.http

import io.ktor.http.*

internal sealed class ServerFailure(
    internal open val canonicalUpstreamUrl: String? = null,
    cause: Exception? = null,
) : RuntimeException(null, cause) {
    abstract val errorCode: ApiErrorCode
    abstract val status: HttpStatusCode
    abstract val safeMessage: String
}

internal class InvalidInputFailure : ServerFailure() {
    override val errorCode = ApiErrorCode.INVALID_REQUEST
    override val status = HttpStatusCode.BadRequest
    override val safeMessage = "Request input is invalid."
}

internal class UpstreamNetworkFailure(
    override val canonicalUpstreamUrl: String,
    cause: Exception? = null,
) : ServerFailure(canonicalUpstreamUrl, cause) {
    override val errorCode = ApiErrorCode.UPSTREAM_NETWORK_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to retrieve data from the upstream source."
}

internal class SourceParsingFailure(
    upstreamUrl: Url,
    cause: Exception,
) : ServerFailure(upstreamUrl.toSafeCanonicalUpstreamUrl(), cause) {
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

internal fun Url.toSafeCanonicalUpstreamUrl(): String = buildString {
    append(protocol.name)
    append("://")
    append(host)
    if (port != protocol.defaultPort) {
        append(':')
        append(port)
    }
    append(encodedPath)
}

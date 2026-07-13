package kr.co.cotton.vlrgg_mobile.common.http

import io.ktor.http.*

internal sealed class ServerFailure(
    internal open val canonicalUpstreamUrl: String? = null,
    cause: Throwable? = null,
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
    cause: Throwable? = null,
) : ServerFailure(canonicalUpstreamUrl, cause) {
    override val errorCode = ApiErrorCode.UPSTREAM_NETWORK_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to retrieve data from the upstream source."
}

internal class SourceParsingFailure(
    override val canonicalUpstreamUrl: String? = null,
    cause: Throwable? = null,
) : ServerFailure(canonicalUpstreamUrl, cause) {
    override val errorCode = ApiErrorCode.SOURCE_PARSING_FAILURE
    override val status = HttpStatusCode.BadGateway
    override val safeMessage = "Unable to parse data from the upstream source."
}

internal class InternalServerFailure(cause: Throwable? = null) : ServerFailure(cause = cause) {
    override val errorCode = ApiErrorCode.INTERNAL_ERROR
    override val status = HttpStatusCode.InternalServerError
    override val safeMessage = "An unexpected server error occurred."
}

internal fun ServerFailure.toApiErrorResponse() = ApiErrorResponse(
    code = errorCode,
    message = safeMessage,
)

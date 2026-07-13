package kr.co.cotton.vlrgg_mobile.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.InternalServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.toApiErrorResponse

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<ServerFailure> { call, failure ->
            call.application.logFailure(call, failure)
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }

            val failure = InternalServerFailure(cause)
            call.application.logFailure(call, failure)
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiErrorResponse(
                    code = ApiErrorCode.NOT_FOUND,
                    message = "Requested resource was not found.",
                ),
            )
        }
    }
}

private fun Application.logFailure(call: ApplicationCall, failure: ServerFailure) {
    log.warn(
        "Request failed: code={}, method={}, path={}, upstream={}, cause={}",
        failure.errorCode,
        call.request.httpMethod.value,
        call.request.path(),
        failure.canonicalUpstreamUrl ?: "none",
        failure.cause?.javaClass?.simpleName ?: "none",
    )
}

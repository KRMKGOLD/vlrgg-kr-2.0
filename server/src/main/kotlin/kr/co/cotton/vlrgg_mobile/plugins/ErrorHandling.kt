package kr.co.cotton.vlrgg_mobile.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.InternalServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.RetryableServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.toApiErrorResponse
import kr.co.cotton.vlrgg_mobile.protection.recordPublicRequestCompletion
import kr.co.cotton.vlrgg_mobile.protection.recordPublicRequestRejection

private val failureDiagnostics = FailureDiagnostics()

internal fun Application.configureErrorHandling(observability: PublicApiObservability? = null) {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            val failure = InvalidInputFailure(cause)
            observability?.let { call.recordPublicRequestRejection(it, failure) }
            this@configureErrorHandling.logFailureDiagnostic(failure)
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        exception<ServerFailure> { call, failure ->
            observability?.let { call.recordPublicRequestRejection(it, failure) }
            this@configureErrorHandling.logFailureDiagnostic(failure)
            if (failure is RetryableServerFailure) {
                call.response.headers.append(HttpHeaders.RetryAfter, failure.retryAfterSeconds.toString())
            }
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }

            val failure = InternalServerFailure(cause)
            observability?.let { call.recordPublicRequestRejection(it, failure) }
            this@configureErrorHandling.logFailureDiagnostic(failure)
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        status(HttpStatusCode.NotFound) { call, status ->
            if (call.response.status() != null) {
                return@status
            }

            call.respond(
                status,
                ApiErrorResponse(
                    code = ApiErrorCode.NOT_FOUND,
                    message = "Requested resource was not found.",
                ),
            )
            observability?.let { call.recordPublicRequestCompletion(it, status.value) }
        }
    }
}

private fun Application.logFailureDiagnostic(failure: ServerFailure) {
    failureDiagnostics.sample(failure)?.let { diagnostic ->
        log.warn(
            "public_api_failure code={} status={} upstream={} cause_category={} cause_class={}",
            diagnostic.errorCode,
            diagnostic.status,
            diagnostic.canonicalUpstreamUrl,
            diagnostic.causeCategory,
            diagnostic.causeClass,
        )
    }
}

/** Safe, fixed-size samples preserve operational context without request-derived or exception text. */
internal class FailureDiagnostics(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxSamplesPerWindow: Int = MAX_SAMPLES_PER_WINDOW,
) {
    private val lock = Any()
    private var windowStartedAtMillis = Long.MIN_VALUE
    private var sampleCount = 0

    fun sample(failure: ServerFailure): Diagnostic? = synchronized(lock) {
        val now = nowMillis()
        if (windowStartedAtMillis == Long.MIN_VALUE || now - windowStartedAtMillis >= SAMPLE_WINDOW_MILLIS) {
            windowStartedAtMillis = now
            sampleCount = 0
        }
        if (sampleCount >= maxSamplesPerWindow) return null
        sampleCount += 1
        Diagnostic(
            errorCode = failure.errorCode.name,
            status = failure.status.value,
            canonicalUpstreamUrl = failure.canonicalUpstreamUrl ?: "none",
            causeCategory = if (failure.cause == null) "none" else "exception",
            causeClass = failure.cause?.javaClass?.simpleName?.take(MAX_CAUSE_CLASS_LENGTH) ?: "none",
        )
    }

    internal data class Diagnostic(
        val errorCode: String,
        val status: Int,
        val canonicalUpstreamUrl: String,
        val causeCategory: String,
        val causeClass: String,
    )

    private companion object {
        const val SAMPLE_WINDOW_MILLIS = 60_000L
        const val MAX_SAMPLES_PER_WINDOW = 4
        const val MAX_CAUSE_CLASS_LENGTH = 80
    }
}

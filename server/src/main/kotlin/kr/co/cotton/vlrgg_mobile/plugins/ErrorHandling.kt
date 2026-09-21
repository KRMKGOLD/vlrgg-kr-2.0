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
import org.slf4j.LoggerFactory

internal fun Application.configureErrorHandling(
    observability: PublicApiObservability? = null,
    failureDiagnostics: FailureDiagnostics = FailureDiagnostics(),
    failureEventFormatter: FailureEventFormatter = FailureEventFormatter(),
    failureEventSink: (FailureEvent) -> Unit = ::emitFailureEvent,
) {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            val failure = InvalidInputFailure(cause)
            logFailureDiagnostic(call, failure, observability, failureDiagnostics, failureEventFormatter, failureEventSink)
            observability?.let { call.recordPublicRequestRejection(it, failure) }
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        exception<ServerFailure> { call, failure ->
            logFailureDiagnostic(call, failure, observability, failureDiagnostics, failureEventFormatter, failureEventSink)
            observability?.let { call.recordPublicRequestRejection(it, failure) }
            if (failure is RetryableServerFailure) {
                call.response.headers.append(HttpHeaders.RetryAfter, failure.retryAfterSeconds.toString())
            }
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) throw cause

            val failure = InternalServerFailure(cause)
            logFailureDiagnostic(call, failure, observability, failureDiagnostics, failureEventFormatter, failureEventSink)
            observability?.let { call.recordPublicRequestRejection(it, failure) }
            call.respond(failure.status, failure.toApiErrorResponse())
        }
        status(HttpStatusCode.NotFound) { call, status ->
            if (call.response.status() != null) return@status

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

private fun logFailureDiagnostic(
    call: ApplicationCall,
    failure: ServerFailure,
    observability: PublicApiObservability?,
    diagnostics: FailureDiagnostics,
    formatter: FailureEventFormatter,
    sink: (FailureEvent) -> Unit,
) {
    val category = failure.diagnosticCategory()
    if (!diagnostics.admit(category)) {
        observability?.diagnosticSuppressed(category)
        return
    }

    try {
        val traceHeaders = call.request.headers.getAll(CLOUD_TRACE_HEADER).orEmpty()
        sink(formatter.format(failure, traceHeaders))
        observability?.diagnosticEmitted(category)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        observability?.telemetryFailed()
    }
}

private val failureLogger = LoggerFactory.getLogger("server.failure")

private fun emitFailureEvent(event: FailureEvent) {
    if (event.severity == FailureSeverity.ERROR) failureLogger.error(event.json) else failureLogger.warn(event.json)
}

/** Fixed-category, application-scoped admission avoids attacker-controlled cardinality. */
internal class FailureDiagnostics(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxSamplesPerWindow: Int = MAX_SAMPLES_PER_WINDOW,
) {
    private val lock = Any()
    private val windows = Array(FailureCategory.entries.size) { Window() }

    fun admit(category: FailureCategory): Boolean = synchronized(lock) {
        val now = nowMillis()
        val window = windows[category.ordinal]
        if (window.startedAtMillis == Long.MIN_VALUE || now - window.startedAtMillis >= SAMPLE_WINDOW_MILLIS) {
            window.startedAtMillis = now
            window.samples = 0
        }
        if (window.samples >= maxSamplesPerWindow) return false
        window.samples += 1
        true
    }

    private class Window(var startedAtMillis: Long = Long.MIN_VALUE, var samples: Int = 0)

    private companion object {
        const val SAMPLE_WINDOW_MILLIS = 60_000L
        const val MAX_SAMPLES_PER_WINDOW = 4
    }
}

internal fun ServerFailure.diagnosticCategory(): FailureCategory = when (errorCode) {
    ApiErrorCode.UPSTREAM_NETWORK_FAILURE -> FailureCategory.UPSTREAM_NETWORK
    ApiErrorCode.INTERNAL_ERROR -> FailureCategory.INTERNAL
    ApiErrorCode.SOURCE_PARSING_FAILURE -> FailureCategory.SOURCE_PARSING
    else -> FailureCategory.EXPECTED
}

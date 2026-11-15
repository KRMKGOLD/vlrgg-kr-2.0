package kr.co.cotton.vlrgg_mobile.plugins

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kr.co.cotton.vlrgg_mobile.common.http.ServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure

internal const val CLOUD_TRACE_HEADER = "X-Cloud-Trace-Context"

internal enum class FailureCategory { EXPECTED, UPSTREAM_NETWORK, INTERNAL, SOURCE_PARSING }
internal enum class FailureSeverity { WARN, ERROR }
internal data class FailureEvent(val severity: FailureSeverity, val json: String)

/**
 * Formats one failure as a single JSON log line for Cloud Logging.
 *
 * Internal and parsing failures are ERROR events that Error Reporting groups by their stack.
 * The stack carries exception types and code locations only: exception messages can hold
 * upstream HTML, URLs, or selectors and are never logged.
 */
internal class FailureEventFormatter(environment: Map<String, String> = System.getenv()) {
    private val service = environment["K_SERVICE"] ?: "vlrgg-server-local"
    private val version = environment["K_REVISION"] ?: "local"
    private val project = environment["GOOGLE_CLOUD_PROJECT"]

    fun format(failure: ServerFailure, traceHeaders: List<String> = emptyList()): FailureEvent {
        val category = failure.diagnosticCategory()
        val severity = if (category == FailureCategory.INTERNAL || category == FailureCategory.SOURCE_PARSING) {
            FailureSeverity.ERROR
        } else {
            FailureSeverity.WARN
        }
        val errorCode = failure.errorCode.name
        val stack = if (severity == FailureSeverity.ERROR) (failure.cause ?: failure).stackWithoutMessages(errorCode) else null
        val json = buildJsonObject {
            put("severity", severity.name)
            put("error_code", errorCode)
            put("category", category.name)
            put("http_status", failure.status.value)
            put("canonical_upstream", failure.canonicalUpstream())
            putJsonObject("serviceContext") {
                put("service", service)
                put("version", version)
            }
            put("message", stack ?: "$errorCode server failure")
            if (severity == FailureSeverity.ERROR) {
                put("@type", REPORTED_ERROR_TYPE)
                // The JVM can omit frames for repeated implicit exceptions; Error Reporting then needs a location.
                if (stack == null) {
                    putJsonObject("context") {
                        putJsonObject("reportLocation") {
                            put("filePath", "ErrorHandling.kt")
                            put("functionName", "logFailureDiagnostic")
                        }
                    }
                }
            }
            traceHeaders.validCloudTrace(project)?.let { put(TRACE_FIELD, it) }
        }.toString()
        return FailureEvent(severity, json)
    }

    // The server only contacts VLR.GG, so upstream failures log its fixed origin and never a request URL.
    private fun ServerFailure.canonicalUpstream(): String =
        if (this is UpstreamNetworkFailure || this is SourceParsingFailure) CANONICAL_UPSTREAM else "none"

    private companion object {
        const val CANONICAL_UPSTREAM = "https://www.vlr.gg/"
        const val REPORTED_ERROR_TYPE = "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent"
        const val TRACE_FIELD = "logging.googleapis.com/trace"
    }
}

/**
 * Exception types and frames of this throwable and its causes, or null when none has a frame.
 * The frame and cause limits keep one event a small single stdout line.
 */
private fun Throwable.stackWithoutMessages(errorCode: String): String? {
    var remainingFrames = MAX_FRAMES
    val chain = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH + 1).withIndex()
    val text = chain.joinToString("\n") { (index, throwable) ->
        val frames = throwable.stackTrace.take(remainingFrames)
        remainingFrames -= frames.size
        buildString {
            if (index > 0) append("Caused by: ")
            append(throwable.javaClass.name).append(": ").append(errorCode)
            frames.forEach { append("\n\tat ").append(it) }
        }
    }
    return text.takeIf { remainingFrames < MAX_FRAMES }
}

/** The trace header is request input, so only one fully valid value is promoted into the log. */
private fun List<String>.validCloudTrace(project: String?): String? {
    if (project == null || size != 1) return null
    val value = single()
    val match = TRACE_PATTERN.matchEntire(value) ?: return null
    val span = match.groupValues[1]
    if (span.isNotEmpty() && span.toULongOrNull() == null) return null
    return "projects/$project/traces/${value.substring(0, 32).lowercase()}"
}

private const val MAX_FRAMES = 32
private const val MAX_CAUSE_DEPTH = 3
private val TRACE_PATTERN = Regex("[0-9A-Fa-f]{32}(?:/([0-9]{1,20}))?(?:;o=[01])?")

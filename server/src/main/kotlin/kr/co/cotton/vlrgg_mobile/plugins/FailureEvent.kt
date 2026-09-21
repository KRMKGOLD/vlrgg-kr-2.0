package kr.co.cotton.vlrgg_mobile.plugins

import java.nio.charset.StandardCharsets
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ServerFailure

internal const val CLOUD_TRACE_HEADER = "X-Cloud-Trace-Context"

internal enum class FailureCategory { EXPECTED, UPSTREAM_NETWORK, INTERNAL, SOURCE_PARSING }
internal enum class FailureSeverity { WARN, ERROR }
internal data class FailureEvent(val severity: FailureSeverity, val json: String)

internal class FailureEventFormatter(
    environment: Map<String, String> = System.getenv(),
    private val classLoader: ClassLoader = FailureEventFormatter::class.java.classLoader,
) {
    private val service = environment["K_SERVICE"].safeMetadata() ?: "vlrgg-server-local"
    private val version = environment["K_REVISION"].safeMetadata() ?: "local"
    private val project = environment["GOOGLE_CLOUD_PROJECT"].safeProjectId()

    fun format(failure: ServerFailure, traceHeaders: List<String> = emptyList()): FailureEvent {
        val category = failure.diagnosticCategory()
        val severity = if (category == FailureCategory.INTERNAL || category == FailureCategory.SOURCE_PARSING) {
            FailureSeverity.ERROR
        } else {
            FailureSeverity.WARN
        }
        val stack = if (severity == FailureSeverity.ERROR) safeStack(failure) else SafeStack()
        val state = EventState(failure, category, severity, traceHeaders.validCloudTrace(project), stack)
        var json = state.json()
        while (json.utf8Size() > MAX_JSON_BYTES && state.removeLastFrameOrCause()) {
            state.byteLimitReached = true
            json = state.json()
        }
        check(json.utf8Size() <= MAX_JSON_BYTES)
        return FailureEvent(severity, json)
    }

    private inner class EventState(
        private val failure: ServerFailure,
        private val category: FailureCategory,
        private val severity: FailureSeverity,
        private val trace: String?,
        private val stack: SafeStack,
    ) {
        var byteLimitReached = false

        fun removeLastFrameOrCause(): Boolean {
            val section = stack.sections.lastOrNull() ?: return false
            if (section.frames.isNotEmpty()) {
                section.frames.removeLast()
                return true
            }
            if (stack.sections.size > 1) {
                stack.sections.removeLast()
                return true
            }
            return false
        }

        fun json(): String = buildJsonObject {
            put("severity", JsonPrimitive(severity.name))
            put("error_code", JsonPrimitive(failure.errorCode.name))
            put("category", JsonPrimitive(category.name))
            put("http_status", JsonPrimitive(failure.status.value))
            put("canonical_upstream", JsonPrimitive(failure.canonicalUpstreamUrl ?: "none"))
            put("serviceContext", buildJsonObject {
                put("service", JsonPrimitive(service))
                put("version", JsonPrimitive(version))
            })
            put("message", JsonPrimitive(stack.message(failure.errorCode)))
            put("truncation", buildJsonObject {
                put("frames", JsonPrimitive(stack.frameLimitReached))
                put("causes", JsonPrimitive(stack.causeLimitReached))
                put("candidates", JsonPrimitive(stack.candidateLimitReached))
                put("cycle", JsonPrimitive(stack.cycleDetected))
                put("accessor_failure", JsonPrimitive(stack.accessorFailure))
                put("bytes", JsonPrimitive(byteLimitReached))
            })
            if (severity == FailureSeverity.ERROR) {
                put("@type", JsonPrimitive(REPORTED_ERROR_TYPE))
                if (stack.sections.all { it.frames.isEmpty() }) {
                    put("context", buildJsonObject {
                        put("reportLocation", buildJsonObject {
                            put("filePath", JsonPrimitive("ErrorHandling.kt"))
                            put("functionName", JsonPrimitive("logFailureDiagnostic"))
                        })
                    })
                }
            }
            trace?.let { put(TRACE_FIELD, JsonPrimitive(it)) }
        }.toString()
    }

    private fun safeStack(failure: ServerFailure): SafeStack {
        val result = SafeStack()
        val initial = safeCause(failure, result) ?: failure
        val seen = IdentityHashMap<Throwable, Unit>()
        var current: Throwable? = initial
        var depth = 0
        var candidates = 0
        var frames = 0

        while (current != null && depth <= MAX_CAUSE_DEPTH) {
            if (seen.put(current, Unit) != null) {
                result.cycleDetected = true
                break
            }
            val section = StackSection(safeType(current, result))
            result.sections += section
            val rawFrames = safeFrames(current, result)
            for (raw in rawFrames) {
                if (candidates >= MAX_FRAME_CANDIDATES) {
                    result.candidateLimitReached = true
                    break
                }
                candidates += 1
                val frame = safeFrame(raw, result) ?: continue
                if (frames >= MAX_FRAMES) {
                    result.frameLimitReached = true
                    continue
                }
                section.frames += frame
                frames += 1
            }
            if (candidates >= MAX_FRAME_CANDIDATES && rawFrames.size > candidates) result.candidateLimitReached = true

            val next = safeCause(current, result) ?: break
            if (depth == MAX_CAUSE_DEPTH) {
                result.causeLimitReached = true
                break
            }
            current = next
            depth += 1
        }
        return result
    }

    private fun safeType(throwable: Throwable, result: SafeStack): String {
        val actual = throwable.javaClass
        val name = actual.name
        if (!name.isSafeClassName()) return SAFE_THROWABLE_TYPE
        return try {
            if (Class.forName(name, false, classLoader) == actual) name else SAFE_THROWABLE_TYPE
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            result.accessorFailure = true
            SAFE_THROWABLE_TYPE
        } catch (_: LinkageError) {
            result.accessorFailure = true
            SAFE_THROWABLE_TYPE
        }
    }

    private fun safeFrame(raw: StackTraceElement, result: SafeStack): String? {
        val className = raw.className.takeIf { it.isSafeClassName() } ?: return null
        val methodName = raw.methodName.takeIf { it.isSafeMethodName() } ?: return null
        return try {
            val type = Class.forName(className, false, classLoader)
            if (!type.hasMember(methodName)) return null
            val safeFile = raw.fileName.safeSourceName(type)
            val location = when {
                raw.isNativeMethod -> "Native Method"
                safeFile != null && raw.lineNumber > 0 -> "$safeFile:${raw.lineNumber}"
                safeFile != null -> safeFile
                else -> "Unknown Source"
            }
            "\tat $className.$methodName($location)"
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            result.accessorFailure = true
            null
        } catch (_: LinkageError) {
            result.accessorFailure = true
            null
        }
    }

    private fun Class<*>.hasMember(name: String): Boolean = when (name) {
        "<init>" -> declaredConstructors.isNotEmpty()
        else -> declaredMethods.any { it.name == name } || methods.any { it.name == name }
    }

    private fun String?.safeSourceName(type: Class<*>): String? {
        val raw = this ?: return null
        val base = type.name.substringAfterLast('.').substringBefore('$').removeSuffix("Kt")
        return raw.takeIf { it == "$base.kt" || it == "$base.java" }
    }

    private fun safeFrames(throwable: Throwable, result: SafeStack): Array<StackTraceElement> = try {
        throwable.stackTrace
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        result.accessorFailure = true
        emptyArray()
    } catch (_: LinkageError) {
        result.accessorFailure = true
        emptyArray()
    }

    private fun safeCause(throwable: Throwable, result: SafeStack): Throwable? = try {
        throwable.cause
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        result.accessorFailure = true
        null
    } catch (_: LinkageError) {
        result.accessorFailure = true
        null
    }

    private companion object {
        const val MAX_FRAMES = 32
        const val MAX_CAUSE_DEPTH = 3
        const val MAX_FRAME_CANDIDATES = 128
        const val MAX_RECORD_BYTES = 16 * 1024
        val MAX_JSON_BYTES = MAX_RECORD_BYTES - System.lineSeparator().toByteArray(StandardCharsets.UTF_8).size
        const val REPORTED_ERROR_TYPE = "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent"
        const val TRACE_FIELD = "logging.googleapis.com/trace"
        const val SAFE_THROWABLE_TYPE = "java.lang.RuntimeException"
    }
}

private class SafeStack(
    val sections: MutableList<StackSection> = mutableListOf(),
    var frameLimitReached: Boolean = false,
    var causeLimitReached: Boolean = false,
    var candidateLimitReached: Boolean = false,
    var cycleDetected: Boolean = false,
    var accessorFailure: Boolean = false,
) {
    fun message(errorCode: ApiErrorCode): String {
        if (sections.all { it.frames.isEmpty() }) return "${errorCode.name} server failure"
        return sections.mapIndexed { index, section ->
            buildString {
                if (index > 0) append("Caused by: ")
                append(section.type).append(": ").append(errorCode.name)
                section.frames.forEach { append('\n').append(it) }
            }
        }.joinToString("\n")
    }
}

private data class StackSection(val type: String, val frames: MutableList<String> = mutableListOf())

private fun String.isSafeClassName(): Boolean =
    length in 1..160 && split('.').all { it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*")) }

private fun String.isSafeMethodName(): Boolean =
    length in 1..160 && (this == "<init>" || matches(Regex("[A-Za-z_$][A-Za-z0-9_$-]*")))

private fun String?.safeMetadata(): String? =
    this?.takeIf { it.length in 1..63 && it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) }

private fun String?.safeProjectId(): String? =
    this?.lowercase()?.takeIf { it.length in 6..63 && it.matches(Regex("[a-z][a-z0-9-]*[a-z0-9]")) }

private fun List<String>.validCloudTrace(project: String?): String? {
    if (project == null || size != 1) return null
    val value = single()
    val match = TRACE_PATTERN.matchEntire(value) ?: return null
    val span = match.groupValues[1]
    if (span.isNotEmpty() && span.toULongOrNull() == null) return null
    return "projects/$project/traces/${value.substring(0, 32).lowercase()}"
}

private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size

private val TRACE_PATTERN = Regex("[0-9A-Fa-f]{32}(?:/([0-9]{1,20}))?(?:;o=[01])?")

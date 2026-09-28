package kr.co.cotton.vlrgg_mobile.observability.validation

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureMonitoring
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kr.co.cotton.vlrgg_mobile.protection.configurePublicRequestProtection
import kr.co.cotton.vlrgg_mobile.protection.createPublicApiProtection

/** Opt-in test artifact: never included by installDist or the production Dockerfile. */
fun main() {
    val environment = System.getenv()
    check(environment["VLRGG_OBSERVABILITY_ALLOW_EXIT"] != "true" || environment["VLRGG_OBSERVABILITY_ALLOW_OOM"] != "true") {
        "Observability fault flags are mutually exclusive."
    }
    val service = environment["K_SERVICE"]
    val local = service == null && environment["VLRGG_OBSERVABILITY_LOCAL"] == "true"
    check(local || (service == "vlrgg-query-check" && environment["VLRGG_OBSERVABILITY_VALIDATION"] == "true")) {
        "Observability validation is restricted to the private validation service or explicit local mode."
    }
    val port = environment["PORT"]?.toIntOrNull() ?: 18081
    require(port in 1..65535)
    embeddedServer(Netty, host = if (local) "127.0.0.1" else "0.0.0.0", port = port) {
        val healthy = AtomicBoolean(true)
        val observability = configureMonitoring()
        configureSerialization()
        configureErrorHandling(observability)
        val config = PublicApiProtectionConfig.fromEnvironment(environment)
        configurePublicRequestProtection(createPublicApiProtection(config, observability), config)
        routing {
            get("/health") {
                val ok = healthy.get()
                call.respondText(
                    if (ok) "{\"status\":\"ok\"}" else "{\"status\":\"unavailable\"}",
                    ContentType.Application.Json,
                    if (ok) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                )
            }
            post("/__observability/health/fail") {
                healthy.set(false)
                call.respondText("{\"status\":\"configured\"}", ContentType.Application.Json)
            }
            post("/__observability/health/restore") {
                healthy.set(true)
                call.respondText("{\"status\":\"configured\"}", ContentType.Application.Json)
            }
            get("/__observability/internal") { validationInternal() }
            get("/__observability/internal/other") { validationOtherInternal() }
            get("/__observability/parsing") { validationParsing() }
            get("/__observability/upstream") {
                throw UpstreamNetworkFailure(Url("https://www.vlr.gg/"), ValidationNetworkFailure())
            }
            get("/__observability/expected") { throw InvalidInputFailure() }
            post("/__observability/log-canary") {
                println("OBSERVABILITY_LOG_DELIVERY_CANARY")
                System.out.flush()
                call.respondText("{\"status\":\"emitted\"}", ContentType.Application.Json)
            }
            if (environment["VLRGG_OBSERVABILITY_ALLOW_EXIT"] == "true") {
                post("/__observability/exit") {
                    Runtime.getRuntime().halt(42)
                }
            }
            if (oomFixtureEnabled(environment)) {
                post("/__observability/oom") {
                    val failure = runContainerOomFixture()
                    call.respondText(
                        failure.toJson(),
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }
        }
    }.start(wait = true)
}

internal const val OOM_DIRECT_BUFFER_BYTES = 16 * 1024 * 1024
internal const val OOM_MAX_BUFFERS = 64
internal const val OOM_PAGE_BYTES = 4096
internal const val OOM_ALLOCATION_BUDGET_NANOS = 10_000_000_000L
internal const val OOM_MAX_ELAPSED_MILLIS = 25_000L

internal enum class OomFixtureFailure { BYTE_LIMIT, TIME_LIMIT, ALLOCATION_ERROR }

internal data class OomFixtureResult(
    val reason: OomFixtureFailure,
    val allocatedBytes: Long,
    val elapsedMillis: Long,
)

internal fun oomFixtureEnabled(environment: Map<String, String>): Boolean =
    environment["K_SERVICE"] == "vlrgg-query-check" &&
        environment["VLRGG_OBSERVABILITY_VALIDATION"] == "true" &&
        environment["VLRGG_OBSERVABILITY_ALLOW_OOM"] == "true" &&
        environment["VLRGG_OBSERVABILITY_LOCAL"] != "true" &&
        environment["VLRGG_OBSERVABILITY_ALLOW_EXIT"] != "true"

internal fun runContainerOomFixture(
    allocator: (Int) -> ByteBuffer = ByteBuffer::allocateDirect,
    toucher: (ByteBuffer, Int) -> Unit = ::touchDirectBuffer,
    nanoTime: () -> Long = System::nanoTime,
): OomFixtureResult {
    val started = nanoTime()
    val retained = ArrayList<ByteBuffer>(OOM_MAX_BUFFERS)
    var allocatedBytes = 0L
    fun result(reason: OomFixtureFailure) = OomFixtureResult(
        reason = reason,
        allocatedBytes = allocatedBytes,
        elapsedMillis = ((nanoTime() - started).coerceAtLeast(0L) / 1_000_000L)
            .coerceAtMost(OOM_MAX_ELAPSED_MILLIS),
    )
    return try {
        repeat(OOM_MAX_BUFFERS) { blockIndex ->
            if (nanoTime() - started >= OOM_ALLOCATION_BUDGET_NANOS) {
                return result(OomFixtureFailure.TIME_LIMIT)
            }
            try {
                val buffer = allocator(OOM_DIRECT_BUFFER_BYTES)
                retained += buffer
                allocatedBytes += OOM_DIRECT_BUFFER_BYTES
                toucher(buffer, blockIndex)
            } catch (_: OutOfMemoryError) {
                return result(OomFixtureFailure.ALLOCATION_ERROR)
            }
        }
        result(OomFixtureFailure.BYTE_LIMIT)
    } finally {
        Reference.reachabilityFence(retained)
    }
}

internal fun touchDirectBuffer(buffer: ByteBuffer, blockIndex: Int) {
    require(buffer.capacity() % OOM_PAGE_BYTES == 0)
    var pageIndex = 0
    for (offset in 0 until buffer.capacity() step OOM_PAGE_BYTES) {
        val token = ((blockIndex + 1).toLong() shl 32) or (pageIndex + 1).toLong()
        buffer.putLong(offset, token)
        pageIndex++
    }
}

internal fun OomFixtureResult.toJson(): String =
    "{\"status\":\"fixture_failed\",\"reason\":\"${reason.name.lowercase()}\"," +
        "\"allocatedBytes\":$allocatedBytes,\"elapsedMillis\":$elapsedMillis}"

private const val SECRET_SENTINEL = "OBSERVABILITY_RAW_SECRET_SENTINEL?token=never-log-this"
private class ValidationInternalFailure : IllegalStateException(SECRET_SENTINEL)
private class ValidationOtherInternalFailure : IllegalArgumentException(SECRET_SENTINEL)
private class ValidationParsingFailure : IllegalStateException(SECRET_SENTINEL)
private class ValidationNetworkFailure : IllegalStateException(SECRET_SENTINEL)

private fun validationInternal(): Nothing = throw ValidationInternalFailure()
private fun validationOtherInternal(): Nothing = throw ValidationOtherInternalFailure()
private fun validationParsing(): Nothing = throw SourceParsingFailure(Url("https://www.vlr.gg/"), ValidationParsingFailure())

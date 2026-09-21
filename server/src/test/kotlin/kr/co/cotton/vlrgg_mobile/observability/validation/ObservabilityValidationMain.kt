package kr.co.cotton.vlrgg_mobile.observability.validation

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            if (environment["VLRGG_OBSERVABILITY_ALLOW_EXIT"] == "true") {
                post("/__observability/exit") {
                    call.respondText("{\"status\":\"accepted\"}", ContentType.Application.Json, HttpStatusCode.Accepted)
                    this@embeddedServer.launch {
                        delay(250)
                        Runtime.getRuntime().halt(42)
                    }
                }
            }
        }
    }.start(wait = true)
}

private const val SECRET_SENTINEL = "OBSERVABILITY_RAW_SECRET_SENTINEL?token=never-log-this"
private class ValidationInternalFailure : IllegalStateException(SECRET_SENTINEL)
private class ValidationOtherInternalFailure : IllegalArgumentException(SECRET_SENTINEL)
private class ValidationParsingFailure : IllegalStateException(SECRET_SENTINEL)
private class ValidationNetworkFailure : IllegalStateException(SECRET_SENTINEL)

private fun validationInternal(): Nothing = throw ValidationInternalFailure()
private fun validationOtherInternal(): Nothing = throw ValidationOtherInternalFailure()
private fun validationParsing(): Nothing = throw SourceParsingFailure(Url("https://www.vlr.gg/"), ValidationParsingFailure())

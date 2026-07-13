package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.matches.configureMatchesFeature

internal fun Application.configureRouting(upstreamHtmlTransport: UpstreamHtmlTransport) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
    }
    configureMatchesFeature(upstreamHtmlTransport)
}

@Serializable
private data class HealthResponse(
    val status: String,
)

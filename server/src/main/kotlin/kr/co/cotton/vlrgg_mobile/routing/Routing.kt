package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.feature.news.NewsService
import kr.co.cotton.vlrgg_mobile.feature.news.configureNewsRoutes

internal fun Application.configureRouting(newsService: NewsService) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        configureNewsRoutes(newsService)
    }
}

@Serializable
private data class HealthResponse(
    val status: String,
)

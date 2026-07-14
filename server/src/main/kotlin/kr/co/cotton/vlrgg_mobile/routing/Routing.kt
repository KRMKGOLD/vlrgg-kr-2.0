package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
    }
}

@Serializable
private data class HealthResponse(
    val status: String,
)

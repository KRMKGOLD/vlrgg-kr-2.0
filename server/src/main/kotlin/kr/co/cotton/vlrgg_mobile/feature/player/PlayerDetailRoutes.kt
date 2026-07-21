package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

internal fun Route.configurePlayerDetailRoutes(service: PlayerDetailService) {
    get("/api/v1/players") { throw InvalidInputFailure() }
    get("/api/v1/players/") { throw InvalidInputFailure() }
    get("/api/v1/players/{playerId}/") { throw InvalidInputFailure() }
    get("/api/v1/players/{playerId}/{...}") { throw InvalidInputFailure() }
    get("/api/v1/players/{playerId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        call.respond(service.get(PlayerId.fromPath(call.parameters["playerId"])))
    }
}

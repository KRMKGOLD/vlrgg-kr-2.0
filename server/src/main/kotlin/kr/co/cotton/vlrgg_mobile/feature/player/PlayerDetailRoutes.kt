package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet
import kr.co.cotton.vlrgg_mobile.routing.hideFromOpenApi

internal fun Route.configurePlayerDetailRoutes(service: PlayerDetailService) {
    get("/api/v1/players") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/players/") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/players/{playerId}/") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/players/{playerId}/{...}") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/players/{playerId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        call.respond(service.get(PlayerId.fromPath(call.parameters["playerId"])))
    }.describePublicGet<PlayerDetailResponse>(
        operationId = "getPlayerDetail",
        summary = "Get player details",
        operationDescription = "Returns the current player profile, team, agent statistics, and recent matches. Query parameters are not accepted.",
        tag = "Players",
    ) {
        path("playerId") {
            description = "Positive decimal player ID containing up to 10 digits and no leading zeroes."
            required = true
        }
    }
}

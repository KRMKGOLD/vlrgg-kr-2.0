package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet
import kr.co.cotton.vlrgg_mobile.routing.hideFromOpenApi

internal fun Route.configureTeamDetailRoutes(service: TeamDetailService) {
    get("/api/v1/teams") {
        throw InvalidInputFailure()
    }.hideFromOpenApi()
    get("/api/v1/teams/") {
        throw InvalidInputFailure()
    }.hideFromOpenApi()
    get("/api/v1/teams/{teamId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        call.respond(service.get(TeamId.fromPath(call.parameters["teamId"])))
    }.describePublicGet<TeamDetailResponse>(
        operationId = "getTeamDetail",
        summary = "Get team details",
        operationDescription = "Returns the current team profile, matches, roster, and related news. Query parameters are not accepted.",
        tag = "Teams",
    ) {
        path("teamId") {
            description = "Positive decimal team ID containing up to 10 digits and no leading zeroes."
            required = true
        }
    }
}

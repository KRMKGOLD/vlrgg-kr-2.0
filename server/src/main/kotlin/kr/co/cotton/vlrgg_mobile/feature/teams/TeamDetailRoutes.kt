package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

internal fun Route.configureTeamDetailRoutes(service: TeamDetailService) {
    get("/api/v1/teams/{teamId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        call.respond(service.get(TeamId.fromPath(call.parameters["teamId"])))
    }
}

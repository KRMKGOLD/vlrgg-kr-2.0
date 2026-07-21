package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

internal fun Route.configureSeriesRoutes(service: SeriesService) {
    get("/api/v1/series") { throw InvalidInputFailure() }
    get("/api/v1/series/") { throw InvalidInputFailure() }
    get("/api/v1/series/{seriesId}/") { throw InvalidInputFailure() }
    get("/api/v1/series/{seriesId}/{...}") { throw InvalidInputFailure() }
    get("/api/v1/series/{seriesId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        call.respond(service.get(SeriesId.fromPath(call.parameters["seriesId"])))
    }
}

package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet
import kr.co.cotton.vlrgg_mobile.routing.hideFromOpenApi
import kr.co.cotton.vlrgg_mobile.routing.positiveDecimalIdPath

internal fun Route.configureSeriesRoutes(service: SeriesService) {
    get("/api/v1/series") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/{seriesId}/") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/{seriesId}/{...}") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/{seriesId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        call.respond(service.get(SeriesId.fromPath(call.parameters["seriesId"])))
    }.describePublicGet<SeriesResponse>(
        operationId = "getSeriesDetail",
        summary = "Get series details",
        operationDescription = "Returns the series and its upcoming and completed events. Query parameters are not accepted.",
        tag = "Series",
    ) {
        positiveDecimalIdPath("seriesId")
    }
}

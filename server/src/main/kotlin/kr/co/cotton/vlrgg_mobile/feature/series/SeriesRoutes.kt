package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.cache.ResponseCache
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheTtl
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet
import kr.co.cotton.vlrgg_mobile.routing.hideFromOpenApi
import kr.co.cotton.vlrgg_mobile.routing.positiveDecimalIdPath
import kr.co.cotton.vlrgg_mobile.protection.respondCachedPublicJson

internal fun Route.configureSeriesRoutes(service: SeriesService, cache: ResponseCache = ResponseCache.DISABLED) {
    get("/api/v1/series") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/{seriesId}/") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/{seriesId}/{...}") { throw InvalidInputFailure() }.hideFromOpenApi()
    get("/api/v1/series/{seriesId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        val seriesId = SeriesId.fromPath(call.parameters["seriesId"])
        call.respondCachedPublicJson(
            cache,
            ResponseCacheRoute.SERIES_DETAIL,
            seriesId.value,
            ttlSeconds = { _: SeriesResponse -> ResponseCacheTtl.DEFAULT_SECONDS },
        ) { service.get(seriesId) }
    }.describePublicGet<SeriesResponse>(
        operationId = "getSeriesDetail",
        summary = "Get series details",
        operationDescription = "Returns the series and its upcoming and completed events. Query parameters are not accepted.",
        tag = "Series",
    ) {
        positiveDecimalIdPath("seriesId")
    }
}

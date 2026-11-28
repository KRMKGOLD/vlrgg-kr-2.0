package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.cache.ResponseCache
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheTtl
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.POSITIVE_DECIMAL_ID_REGEX
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet
import kr.co.cotton.vlrgg_mobile.routing.positiveDecimalIdPath
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.protection.respondCachedPublicJson

private val eventIdPattern = Regex(POSITIVE_DECIMAL_ID_REGEX)

internal fun Route.configureEventsRoutes(eventsService: EventsService, cache: ResponseCache = ResponseCache.DISABLED) {
    route("/api/v1/events") {
        get {
            call.requireNoQueryParameters()
            call.respondCachedPublicJson(
                cache,
                ResponseCacheRoute.EVENTS_LIST,
                EVENTS_LIST_CACHE_IDENTIFIER,
                ttlSeconds = { _: EventListResponse -> ResponseCacheTtl.DEFAULT_SECONDS },
            ) { eventsService.getEventList() }
        }.describePublicGet<EventListResponse>(
            operationId = "getEvents",
            summary = "Get events",
            operationDescription = "Returns current events grouped by status. Query parameters are not accepted.",
            tag = "Events",
        )
        get("/{eventId}/matches") {
            val eventId = call.validatedEventId()
            call.respondCachedPublicJson(
                cache,
                ResponseCacheRoute.EVENT_MATCHES,
                eventId,
                ttlSeconds = ::eventMatchesTtlSeconds,
            ) { eventsService.getEventMatches(eventId) }
        }.describePublicGet<EventMatchesResponse>(
            operationId = "getEventMatches",
            summary = "Get event matches",
            operationDescription = "Returns matches for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
        get("/{eventId}/news") {
            val eventId = call.validatedEventId()
            call.respondCachedPublicJson(
                cache,
                ResponseCacheRoute.EVENT_NEWS,
                eventId,
                ttlSeconds = { _: EventNewsListResponse -> ResponseCacheTtl.DEFAULT_SECONDS },
            ) { eventsService.getEventNews(eventId) }
        }.describePublicGet<EventNewsListResponse>(
            operationId = "getEventNews",
            summary = "Get event news",
            operationDescription = "Returns news articles for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
        get("/{eventId}/stats") {
            val eventId = call.validatedEventId()
            call.respondCachedPublicJson(
                cache,
                ResponseCacheRoute.EVENT_STATS,
                eventId,
                ttlSeconds = { _: EventStatsResponse -> ResponseCacheTtl.DEFAULT_SECONDS },
            ) { eventsService.getEventStats(eventId) }
        }.describePublicGet<EventStatsResponse>(
            operationId = "getEventStats",
            summary = "Get event player statistics",
            operationDescription = "Returns available player statistics for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
        get("/{eventId}") {
            val eventId = call.validatedEventId()
            call.respondCachedPublicJson(
                cache,
                ResponseCacheRoute.EVENT_DETAIL,
                eventId,
                ttlSeconds = { _: EventDetailResponse -> ResponseCacheTtl.DEFAULT_SECONDS },
            ) { eventsService.getEventDetail(eventId) }
        }.describePublicGet<EventDetailResponse>(
            operationId = "getEventDetail",
            summary = "Get event details",
            operationDescription = "Returns details for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
    }
}

/** The Events list has no request parameter, so its key uses this fixed identifier. */
private const val EVENTS_LIST_CACHE_IDENTIFIER = "all"

/** A live match changes scores and status quickly, so any LIVE item shortens the whole list's TTL. */
internal fun eventMatchesTtlSeconds(response: EventMatchesResponse): Int =
    if (response.items.any { it.status == MatchStatus.LIVE }) ResponseCacheTtl.LIVE_SECONDS else ResponseCacheTtl.DEFAULT_SECONDS

private fun ApplicationCall.validatedEventId(): String {
    requireNoQueryParameters()
    return parameters["eventId"]
        ?.takeIf(eventIdPattern::matches)
        ?: throw InvalidInputFailure()
}

private fun ApplicationCall.requireNoQueryParameters() {
    if (request.queryParameters.names().isNotEmpty()) {
        throw InvalidInputFailure()
    }
}

private fun io.ktor.openapi.Parameters.Builder.eventIdParameter() {
    positiveDecimalIdPath("eventId")
}

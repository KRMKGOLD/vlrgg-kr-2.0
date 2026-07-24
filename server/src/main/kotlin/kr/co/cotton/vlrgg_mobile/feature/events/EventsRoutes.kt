package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.POSITIVE_DECIMAL_ID_REGEX
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet
import kr.co.cotton.vlrgg_mobile.routing.positiveDecimalIdPath

private val eventIdPattern = Regex(POSITIVE_DECIMAL_ID_REGEX)

internal fun Route.configureEventsRoutes(eventsService: EventsService) {
    route("/api/v1/events") {
        get {
            call.requireNoQueryParameters()
            call.respond(eventsService.getEventList())
        }.describePublicGet<EventListResponse>(
            operationId = "getEvents",
            summary = "Get events",
            operationDescription = "Returns current events grouped by status. Query parameters are not accepted.",
            tag = "Events",
        )
        get("/{eventId}/matches") {
            call.respond(eventsService.getEventMatches(call.validatedEventId()))
        }.describePublicGet<EventMatchesResponse>(
            operationId = "getEventMatches",
            summary = "Get event matches",
            operationDescription = "Returns matches for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
        get("/{eventId}/news") {
            call.respond(eventsService.getEventNews(call.validatedEventId()))
        }.describePublicGet<EventNewsListResponse>(
            operationId = "getEventNews",
            summary = "Get event news",
            operationDescription = "Returns news articles for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
        get("/{eventId}/stats") {
            call.respond(eventsService.getEventStats(call.validatedEventId()))
        }.describePublicGet<EventStatsResponse>(
            operationId = "getEventStats",
            summary = "Get event player statistics",
            operationDescription = "Returns available player statistics for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
        get("/{eventId}") {
            call.respond(eventsService.getEventDetail(call.validatedEventId()))
        }.describePublicGet<EventDetailResponse>(
            operationId = "getEventDetail",
            summary = "Get event details",
            operationDescription = "Returns details for one event. Query parameters are not accepted.",
            tag = "Events",
        ) { eventIdParameter() }
    }
}

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

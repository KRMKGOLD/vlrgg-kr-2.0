package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

private val eventIdPattern = Regex("[1-9][0-9]{0,9}")

internal fun Route.configureEventsRoutes(eventsService: EventsService) {
    route("/api/v1/events") {
        get {
            call.requireNoQueryParameters()
            call.respond(eventsService.getEventList())
        }
        get("/{eventId}/matches") {
            call.respond(eventsService.getEventMatches(call.validatedEventId()))
        }
        get("/{eventId}/news") {
            call.respond(eventsService.getEventNews(call.validatedEventId()))
        }
        get("/{eventId}/stats") {
            call.respond(eventsService.getEventStats(call.validatedEventId()))
        }
        get("/{eventId}") {
            call.respond(eventsService.getEventDetail(call.validatedEventId()))
        }
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

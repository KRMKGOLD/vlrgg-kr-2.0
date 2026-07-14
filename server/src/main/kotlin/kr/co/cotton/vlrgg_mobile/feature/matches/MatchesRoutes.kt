package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

internal fun Application.configureMatchesRoutes(service: MatchesService) {
    routing {
        route(MATCHES_API_PATH) {
            get("upcoming") {
                call.respond(service.getMatches(MatchListCategory.UPCOMING, call.validatedPage()))
            }
            get("results") {
                call.respond(service.getMatches(MatchListCategory.RESULTS, call.validatedPage()))
            }
            get("{matchId}") {
                call.respond(service.getMatch(call.validatedMatchId()))
            }
        }
    }
}

private fun ApplicationCall.validatedPage(): Int {
    val parameters = request.queryParameters
    if (parameters.names().any { it != PAGE_PARAMETER }) {
        throw InvalidInputFailure()
    }
    val values = parameters.getAll(PAGE_PARAMETER)
    if (values == null) {
        return DEFAULT_PAGE
    }
    if (values.size != SINGLE_PARAMETER_VALUE) {
        throw InvalidInputFailure()
    }
    val page = values.single().toIntOrNull()
    if (page == null || page !in MINIMUM_PAGE..MAXIMUM_PAGE || values.single() != page.toString()) {
        throw InvalidInputFailure()
    }
    return page
}

private fun ApplicationCall.validatedMatchId(): String {
    val matchId = parameters["matchId"] ?: throw InvalidInputFailure()
    if (!MATCH_ID_REGEX.matches(matchId)) {
        throw InvalidInputFailure()
    }
    return matchId
}

private const val MATCHES_API_PATH = "/api/v1/matches"
private const val PAGE_PARAMETER = "page"
private const val DEFAULT_PAGE = 1
private const val MINIMUM_PAGE = 1
private const val MAXIMUM_PAGE = 1_000
private const val SINGLE_PARAMETER_VALUE = 1
private val MATCH_ID_REGEX = Regex("[1-9]\\d{0,9}")

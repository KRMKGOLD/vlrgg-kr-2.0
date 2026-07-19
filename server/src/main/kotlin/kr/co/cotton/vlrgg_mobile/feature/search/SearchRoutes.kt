package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

private const val MAX_SEARCH_QUERY_LENGTH = 80

internal fun Route.configureSearchRoutes(searchService: SearchService) {
    get("/api/v1/search") {
        val query = call.validatedSearchQuery()
        call.respond(searchService.search(query))
    }
}

private fun ApplicationCall.validatedSearchQuery(): String {
    val parameters = request.queryParameters
    if (parameters.names() != setOf("q")) throw InvalidInputFailure()

    val values = parameters.getAll("q") ?: throw InvalidInputFailure()
    if (values.size != 1) throw InvalidInputFailure()

    val rawQuery = values.single()
    val normalizedQuery = rawQuery.trim()
    if (
        normalizedQuery.isEmpty() ||
        normalizedQuery.length > MAX_SEARCH_QUERY_LENGTH ||
        rawQuery.any(Char::isISOControl) ||
        normalizedQuery.none(Char::isLetterOrDigit)
    ) {
        throw InvalidInputFailure()
    }

    return normalizedQuery
}

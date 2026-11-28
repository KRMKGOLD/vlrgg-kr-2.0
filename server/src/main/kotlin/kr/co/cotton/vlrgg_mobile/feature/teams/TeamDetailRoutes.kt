package kr.co.cotton.vlrgg_mobile.feature.teams

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

internal fun Route.configureTeamDetailRoutes(service: TeamDetailService, cache: ResponseCache = ResponseCache.DISABLED) {
    get("/api/v1/teams") {
        throw InvalidInputFailure()
    }.hideFromOpenApi()
    get("/api/v1/teams/") {
        throw InvalidInputFailure()
    }.hideFromOpenApi()
    get("/api/v1/teams/{teamId}") {
        if (call.request.queryParameters.names().isNotEmpty()) throw InvalidInputFailure()
        val teamId = TeamId.fromPath(call.parameters["teamId"])
        call.respondCachedPublicJson(
            cache,
            ResponseCacheRoute.TEAM_DETAIL,
            teamId.value,
            ttlSeconds = ::teamDetailTtlSeconds,
        ) { service.get(teamId) }
    }.describePublicGet<TeamDetailResponse>(
        operationId = "getTeamDetail",
        summary = "Get team details",
        operationDescription = "Returns the current team profile, matches, roster, and related news. Query parameters are not accepted.",
        tag = "Teams",
    ) {
        positiveDecimalIdPath("teamId")
    }
}

/**
 * Team matches have no status enum; vlr.gg shows an in-progress match as the literal `LIVE` in the result slot.
 * A missing or any other text (a countdown, a score) is not treated as LIVE.
 */
internal fun teamDetailTtlSeconds(response: TeamDetailResponse): Int =
    if ((response.upcomingMatches + response.recentMatches).any { it.statusText.isLiveStatusText() }) {
        ResponseCacheTtl.LIVE_SECONDS
    } else {
        ResponseCacheTtl.DEFAULT_SECONDS
    }

private fun String?.isLiveStatusText(): Boolean = this?.trim().equals(LIVE_STATUS_TEXT, ignoreCase = true)

private const val LIVE_STATUS_TEXT = "LIVE"

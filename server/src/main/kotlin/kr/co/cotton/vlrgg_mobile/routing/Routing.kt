package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.events.EventsService
import kr.co.cotton.vlrgg_mobile.feature.events.configureEventsRoutes
import kr.co.cotton.vlrgg_mobile.feature.matches.configureMatchesFeature
import kr.co.cotton.vlrgg_mobile.feature.news.NewsService
import kr.co.cotton.vlrgg_mobile.feature.news.configureNewsRoutes
import kr.co.cotton.vlrgg_mobile.feature.search.SearchService
import kr.co.cotton.vlrgg_mobile.feature.search.configureSearchRoutes
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamDetailService
import kr.co.cotton.vlrgg_mobile.feature.teams.configureTeamDetailRoutes

internal fun Application.configureRouting(
    upstreamHtmlTransport: UpstreamHtmlTransport,
    newsService: NewsService,
    eventsService: EventsService,
    searchService: SearchService,
    teamDetailService: TeamDetailService,
) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        configureNewsRoutes(newsService)
        configureEventsRoutes(eventsService)
        configureSearchRoutes(searchService)
        configureTeamDetailRoutes(teamDetailService)
    }
    configureMatchesFeature(upstreamHtmlTransport)
}

@Serializable
private data class HealthResponse(
    val status: String,
)

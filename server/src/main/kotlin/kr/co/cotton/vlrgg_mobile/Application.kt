package kr.co.cotton.vlrgg_mobile

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kr.co.cotton.vlrgg_mobile.common.scraping.createUpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.events.EventsService
import kr.co.cotton.vlrgg_mobile.feature.events.createEventsService
import kr.co.cotton.vlrgg_mobile.feature.news.NewsService
import kr.co.cotton.vlrgg_mobile.feature.news.createDefaultNewsService
import kr.co.cotton.vlrgg_mobile.feature.player.PlayerDetailService
import kr.co.cotton.vlrgg_mobile.feature.player.createPlayerDetailService
import kr.co.cotton.vlrgg_mobile.feature.search.SearchService
import kr.co.cotton.vlrgg_mobile.feature.search.createSearchService
import kr.co.cotton.vlrgg_mobile.feature.series.SeriesService
import kr.co.cotton.vlrgg_mobile.feature.series.createSeriesService
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamDetailService
import kr.co.cotton.vlrgg_mobile.feature.teams.createTeamDetailService
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureMonitoring
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.routing.configureRouting

private const val API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE = "VLRGG_ENABLE_API_DOCUMENTATION"

fun main() {
    val enableApiDocumentation = System.getenv(API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE) == "true"
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = {
        module(enableApiDocumentation = enableApiDocumentation)
    })
        .start(wait = true)
}

internal fun Application.module(
    newsService: NewsService? = null,
    eventsService: EventsService? = null,
    searchService: SearchService? = null,
    seriesService: SeriesService? = null,
    teamDetailService: TeamDetailService? = null,
    playerDetailService: PlayerDetailService? = null,
    enableApiDocumentation: Boolean = false,
) {
    configureSerialization()
    configureMonitoring()
    configureErrorHandling()
    val upstreamHtmlTransport = createUpstreamHtmlTransport()
    configureRouting(
        upstreamHtmlTransport = upstreamHtmlTransport,
        newsService = newsService ?: createDefaultNewsService(upstreamHtmlTransport),
        eventsService = eventsService ?: createEventsService(upstreamHtmlTransport),
        searchService = searchService ?: createSearchService(upstreamHtmlTransport),
        seriesService = seriesService ?: createSeriesService(upstreamHtmlTransport),
        teamDetailService = teamDetailService ?: createTeamDetailService(upstreamHtmlTransport),
        playerDetailService = playerDetailService ?: createPlayerDetailService(upstreamHtmlTransport),
        enableApiDocumentation = enableApiDocumentation,
    )
}

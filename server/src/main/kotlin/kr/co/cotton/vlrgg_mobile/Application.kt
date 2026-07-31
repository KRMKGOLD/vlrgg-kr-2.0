package kr.co.cotton.vlrgg_mobile

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kr.co.cotton.vlrgg_mobile.common.scraping.createUpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.events.createEventsService
import kr.co.cotton.vlrgg_mobile.feature.events.EventsService
import kr.co.cotton.vlrgg_mobile.feature.matches.DefaultMatchesService
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesMapper
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesParser
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesScraper
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.ServerListenerConfiguration
import kr.co.cotton.vlrgg_mobile.feature.news.createDefaultNewsService
import kr.co.cotton.vlrgg_mobile.feature.news.NewsService
import kr.co.cotton.vlrgg_mobile.feature.player.createPlayerDetailService
import kr.co.cotton.vlrgg_mobile.feature.player.PlayerDetailService
import kr.co.cotton.vlrgg_mobile.feature.search.createSearchService
import kr.co.cotton.vlrgg_mobile.feature.search.SearchService
import kr.co.cotton.vlrgg_mobile.feature.series.createSeriesService
import kr.co.cotton.vlrgg_mobile.feature.series.SeriesService
import kr.co.cotton.vlrgg_mobile.feature.teams.createTeamDetailService
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamDetailService
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureMonitoring
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.routing.configureRouting

private const val API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE = "VLRGG_ENABLE_API_DOCUMENTATION"

/** Packaged runtime deliberately has no notification provider, verifier, store, or scheduler route. */
fun main() {
    val listener = ServerListenerConfiguration.fromEnvironment(System.getenv())
    embeddedServer(Netty, host = listener.host, port = listener.port) {
        module(enableApiDocumentation = System.getenv(API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE) == "true")
    }.start(wait = true)
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
    configureSerialization(); configureMonitoring(); configureErrorHandling()
    val transport = createUpstreamHtmlTransport()
    val matches = DefaultMatchesService(VlrMatchesScraper(transport), VlrMatchesParser(), MatchesMapper())
    configureRouting(
        upstreamHtmlTransport = transport,
        newsService = newsService ?: createDefaultNewsService(transport), eventsService = eventsService ?: createEventsService(transport),
        searchService = searchService ?: createSearchService(transport), seriesService = seriesService ?: createSeriesService(transport),
        teamDetailService = teamDetailService ?: createTeamDetailService(transport), playerDetailService = playerDetailService ?: createPlayerDetailService(transport),
        matchesService = matches, enableApiDocumentation = enableApiDocumentation,
    )
}

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
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.ServerListenerConfiguration
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.NotificationConfiguration

private const val API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE = "VLRGG_ENABLE_API_DOCUMENTATION"

fun main() {
    val environment = System.getenv()
    val listenerConfiguration = ServerListenerConfiguration.fromEnvironment(environment)
    // Pure preflight shares this exact listener value; disabled mode allocates no notification resources.
    NotificationConfiguration.fromEnvironment(environment, listenerConfiguration)
    val enableApiDocumentation = environment[API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE] == "true"
    embeddedServer(Netty, port = listenerConfiguration.port, host = listenerConfiguration.host, module = {
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

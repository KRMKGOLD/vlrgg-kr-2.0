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
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.NotificationStore
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.configureNotificationRoutes
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.FixedDelayMatchPolling
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.MatchTracker
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.MatchObservationProvider
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.MatchesServiceObservationProvider
import kr.co.cotton.vlrgg_mobile.feature.matches.notification.OwnedTrackingJob
import kr.co.cotton.vlrgg_mobile.feature.matches.DefaultMatchesService
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesService
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesMapper
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesParser
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE = "VLRGG_ENABLE_API_DOCUMENTATION"

fun main() {
    val environment = System.getenv()
    val listenerConfiguration = ServerListenerConfiguration.fromEnvironment(environment)
    // Pure preflight shares this exact listener value; disabled mode allocates no notification resources.
    val notificationConfiguration = NotificationConfiguration.fromEnvironment(environment, listenerConfiguration)
    val enableApiDocumentation = environment[API_DOCUMENTATION_ENABLED_ENVIRONMENT_VARIABLE] == "true"
    embeddedServer(Netty, port = listenerConfiguration.port, host = listenerConfiguration.host, module = {
        module(
            enableApiDocumentation = enableApiDocumentation,
            notificationConfiguration = notificationConfiguration,
            notificationStore = if (notificationConfiguration.enabled) NotificationStore.open(notificationConfiguration) else null,
            startNotificationTracking = notificationConfiguration.enabled,
        )
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
    notificationConfiguration: NotificationConfiguration? = null,
    notificationStore: NotificationStore? = null,
    matchesService: MatchesService? = null,
    observationProvider: MatchObservationProvider? = null,
    startNotificationTracking: Boolean = false,
) {
    configureSerialization()
    configureMonitoring()
    configureErrorHandling()
    val upstreamHtmlTransport = createUpstreamHtmlTransport()
    val resolvedMatchesService = matchesService ?: DefaultMatchesService(
        scraper = VlrMatchesScraper(upstreamHtmlTransport), parser = VlrMatchesParser(), mapper = MatchesMapper(),
    )
    configureRouting(
        upstreamHtmlTransport = upstreamHtmlTransport,
        newsService = newsService ?: createDefaultNewsService(upstreamHtmlTransport),
        eventsService = eventsService ?: createEventsService(upstreamHtmlTransport),
        searchService = searchService ?: createSearchService(upstreamHtmlTransport),
        seriesService = seriesService ?: createSeriesService(upstreamHtmlTransport),
        teamDetailService = teamDetailService ?: createTeamDetailService(upstreamHtmlTransport),
        playerDetailService = playerDetailService ?: createPlayerDetailService(upstreamHtmlTransport),
        matchesService = resolvedMatchesService,
        enableApiDocumentation = enableApiDocumentation,
    )
    if (notificationConfiguration?.apiEnabled == true) {
        requireNotNull(notificationStore) { "enabled notification API requires the validated local store" }
        configureNotificationRoutes(notificationStore, notificationConfiguration.requestBodyBytes)
    }
    if (notificationStore != null) {
        val trackingOwner = if (startNotificationTracking) CoroutineScope(SupervisorJob() + Dispatchers.Default).let { scope ->
            val job = FixedDelayMatchPolling(
                MatchTracker(notificationStore, observationProvider ?: MatchesServiceObservationProvider(resolvedMatchesService)),
                requireNotNull(notificationConfiguration).pollDelayMillis,
            ).start(scope)
            OwnedTrackingJob(job, notificationStore::close)
        } else null
        environment.monitor.subscribe(ApplicationStopped) { trackingOwner?.stopWithoutBlockingLifecycleThread() ?: notificationStore.close() }
    }
}

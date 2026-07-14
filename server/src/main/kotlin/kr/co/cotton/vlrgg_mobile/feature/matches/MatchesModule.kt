package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.server.application.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Explicit feature composition keeps routes free from transport/client construction. */
internal fun Application.configureMatchesFeature(transport: UpstreamHtmlTransport) {
    configureMatchesRoutes(
        DefaultMatchesService(
            scraper = VlrMatchesScraper(transport),
            parser = VlrMatchesParser(),
            mapper = MatchesMapper(),
        ),
    )
}

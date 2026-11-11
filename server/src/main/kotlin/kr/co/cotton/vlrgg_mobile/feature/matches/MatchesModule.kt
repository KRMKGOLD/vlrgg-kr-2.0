package kr.co.cotton.vlrgg_mobile.feature.matches

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Feature-local manual composition; transport ownership remains at the application lifecycle boundary. */
internal fun createMatchesService(transport: UpstreamHtmlTransport): MatchesService = DefaultMatchesService(
    scraper = VlrMatchesScraper(transport),
    parser = VlrMatchesParser(),
    mapper = MatchesMapper(),
)

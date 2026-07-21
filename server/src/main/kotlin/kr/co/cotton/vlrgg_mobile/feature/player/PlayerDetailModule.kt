package kr.co.cotton.vlrgg_mobile.feature.player

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Feature-local composition; Application owns the shared transport lifecycle. */
internal fun createPlayerDetailService(transport: UpstreamHtmlTransport): PlayerDetailService = PlayerDetailService(
    scraper = PlayerDetailScraper(transport),
    parser = PlayerDetailParser(),
    mapper = PlayerDetailMapper(),
)

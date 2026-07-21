package kr.co.cotton.vlrgg_mobile.feature.series

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Feature-local composition; Application owns the shared transport lifecycle. */
internal fun createSeriesService(transport: UpstreamHtmlTransport): SeriesService = SeriesService(
    scraper = VlrSeriesScraper(transport),
    parser = SeriesParser(),
    mapper = SeriesMapper(),
)

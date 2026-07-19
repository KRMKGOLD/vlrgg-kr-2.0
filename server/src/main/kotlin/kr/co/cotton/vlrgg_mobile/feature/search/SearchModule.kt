package kr.co.cotton.vlrgg_mobile.feature.search

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Feature-local manual composition; the application owns and passes the shared transport. */
internal fun createSearchService(upstreamHtmlTransport: UpstreamHtmlTransport): SearchService =
    SearchService(
        scraper = VlrSearchScraper(
            upstreamHtmlTransport = upstreamHtmlTransport,
            parser = SearchParser(),
        ),
        mapper = SearchMapper(),
    )

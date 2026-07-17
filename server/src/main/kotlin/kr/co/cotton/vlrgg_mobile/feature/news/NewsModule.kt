package kr.co.cotton.vlrgg_mobile.feature.news

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Creates the production News feature service from the shared upstream transport. */
internal fun createDefaultNewsService(transport: UpstreamHtmlTransport): NewsService =
    NewsService(
        scraper = NewsScraper(transport),
        parser = NewsParser(),
        mapper = NewsMapper(),
    )

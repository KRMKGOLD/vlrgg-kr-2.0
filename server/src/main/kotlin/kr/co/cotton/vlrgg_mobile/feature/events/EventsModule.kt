package kr.co.cotton.vlrgg_mobile.feature.events

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Feature-local manual composition; transport ownership remains at the application lifecycle boundary. */
internal fun createEventsService(transport: UpstreamHtmlTransport): EventsService = DefaultEventsService(
    scraper = VlrEventsScraper(transport),
    parser = EventsParser(),
    mapper = EventsMapper(),
)

package kr.co.cotton.vlrgg_mobile.feature.events

/** Performs a fresh upstream request for every call and never serves a stale fallback. */
internal class EventsService(
    private val scraper: EventsScraper,
    private val parser: EventsParser,
    private val mapper: EventsMapper,
) {
    suspend fun getEventList(): EventListResponse = mapper.toEventListResponse(
        parser.parseEventList(scraper.fetchEventList()),
    )

    suspend fun getEventDetail(eventId: String): EventDetailResponse = mapper.toEventDetailResponse(
        parser.parseEventDetail(scraper.fetchEventDetail(eventId), eventId),
    )

    suspend fun getEventMatches(eventId: String): EventMatchesResponse = mapper.toEventMatchesResponse(
        parser.parseEventMatches(scraper.fetchEventMatches(eventId), eventId),
    )

    suspend fun getEventNews(eventId: String): EventNewsListResponse = mapper.toEventNewsListResponse(
        parser.parseEventNews(scraper.fetchEventNews(eventId)),
    )

    suspend fun getEventStats(eventId: String): EventStatsResponse = mapper.toEventStatsResponse(
        parser.parseEventStats(scraper.fetchEventStats(eventId)),
    )
}

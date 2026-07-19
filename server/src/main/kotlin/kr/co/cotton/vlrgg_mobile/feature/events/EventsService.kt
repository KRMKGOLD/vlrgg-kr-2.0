package kr.co.cotton.vlrgg_mobile.feature.events

internal interface EventsService {
    suspend fun getEventList(): EventListResponse

    suspend fun getEventDetail(eventId: String): EventDetailResponse

    suspend fun getEventMatches(eventId: String): EventMatchesResponse

    suspend fun getEventNews(eventId: String): EventNewsListResponse

    suspend fun getEventStats(eventId: String): EventStatsResponse
}

/** Performs a fresh upstream request for every call and never serves a stale fallback. */
internal class DefaultEventsService(
    private val scraper: EventsScraper,
    private val parser: EventsParser,
    private val mapper: EventsMapper,
) : EventsService {
    override suspend fun getEventList(): EventListResponse = mapper.toEventListResponse(
        parser.parseEventList(scraper.fetchEventList()),
    )

    override suspend fun getEventDetail(eventId: String): EventDetailResponse = mapper.toEventDetailResponse(
        parser.parseEventDetail(scraper.fetchEventDetail(eventId), eventId),
    )

    override suspend fun getEventMatches(eventId: String): EventMatchesResponse = mapper.toEventMatchesResponse(
        parser.parseEventMatches(scraper.fetchEventMatches(eventId), eventId),
    )

    override suspend fun getEventNews(eventId: String): EventNewsListResponse = mapper.toEventNewsListResponse(
        parser.parseEventNews(scraper.fetchEventNews(eventId)),
    )

    override suspend fun getEventStats(eventId: String): EventStatsResponse = mapper.toEventStatsResponse(
        parser.parseEventStats(scraper.fetchEventStats(eventId)),
    )
}

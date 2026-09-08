package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventMatchesResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsResponseDto
import kr.co.cotton.vlrgg_mobile.network.getPublicJson

@Inject
internal class RemoteEventDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteEventDataSource {

    override suspend fun getEvents(): EventListResponseDto = httpClient.getPublicJson(EVENTS_PATH)

    override suspend fun getEventDetail(eventId: String): EventDetailResponseDto =
        httpClient.getPublicJson("$EVENTS_PATH/$eventId")

    override suspend fun getEventMatches(eventId: String): EventMatchesResponseDto =
        httpClient.getPublicJson("$EVENTS_PATH/$eventId/matches")

    override suspend fun getEventNews(eventId: String): EventNewsListResponseDto =
        httpClient.getPublicJson("$EVENTS_PATH/$eventId/news")

    override suspend fun getEventStats(eventId: String): EventStatsResponseDto =
        httpClient.getPublicJson("$EVENTS_PATH/$eventId/stats")

    private companion object {
        const val EVENTS_PATH = "/api/v1/events"
    }
}

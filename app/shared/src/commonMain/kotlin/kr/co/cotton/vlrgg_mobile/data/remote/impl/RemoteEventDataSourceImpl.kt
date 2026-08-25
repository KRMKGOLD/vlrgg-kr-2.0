package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventMatchesResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsResponseDto

@Inject
internal class RemoteEventDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteEventDataSource {

    override suspend fun getEvents(): EventListResponseDto = httpClient.get(EVENTS_PATH).body()

    override suspend fun getEventDetail(eventId: String): EventDetailResponseDto =
        httpClient.get("$EVENTS_PATH/$eventId").body()

    override suspend fun getEventMatches(eventId: String): EventMatchesResponseDto =
        httpClient.get("$EVENTS_PATH/$eventId/matches").body()

    override suspend fun getEventNews(eventId: String): EventNewsListResponseDto =
        httpClient.get("$EVENTS_PATH/$eventId/news").body()

    override suspend fun getEventStats(eventId: String): EventStatsResponseDto =
        httpClient.get("$EVENTS_PATH/$eventId/stats").body()

    private companion object {
        const val EVENTS_PATH = "/api/v1/events"
    }
}

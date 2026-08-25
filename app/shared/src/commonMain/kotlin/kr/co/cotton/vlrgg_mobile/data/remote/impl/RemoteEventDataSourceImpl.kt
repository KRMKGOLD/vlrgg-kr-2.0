package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto

@Inject
internal class RemoteEventDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteEventDataSource {

    override suspend fun getEvents(): EventListResponseDto = httpClient.get(EVENTS_PATH).body()

    private companion object {
        const val EVENTS_PATH = "/api/v1/events"
    }
}

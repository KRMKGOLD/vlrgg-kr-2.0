package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository

@Inject
internal class EventRepositoryImpl(
    private val remoteEventDataSource: RemoteEventDataSource,
) : EventRepository {

    override suspend fun getEvents(): AppResult<EventList> = wrapAsAppResult {
        remoteEventDataSource.getEvents().toDomain()
    }
}

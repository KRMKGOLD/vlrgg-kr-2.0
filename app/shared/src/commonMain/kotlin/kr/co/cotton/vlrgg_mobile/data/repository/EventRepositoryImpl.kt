package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository

@Inject
internal class EventRepositoryImpl(
    private val remoteEventDataSource: RemoteEventDataSource,
) : EventRepository {

    override suspend fun getEvents(): AppResult<EventList> = wrapAsAppResult {
        remoteEventDataSource.getEvents().toDomain()
    }

    override suspend fun getEventDetail(eventId: String): AppResult<EventDetail> = wrapAsAppResult {
        remoteEventDataSource.getEventDetail(eventId).toDomain()
    }

    override suspend fun getEventMatches(eventId: String): AppResult<List<MatchSummary>> = wrapAsAppResult {
        remoteEventDataSource.getEventMatches(eventId).toDomain()
    }

    override suspend fun getEventNews(eventId: String): AppResult<List<NewsSummary>> = wrapAsAppResult {
        remoteEventDataSource.getEventNews(eventId).toDomain()
    }

    override suspend fun getEventStats(eventId: String): AppResult<EventStats> = wrapAsAppResult {
        remoteEventDataSource.getEventStats(eventId).toDomain()
    }
}

package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventMatchesResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsResponseDto

internal interface RemoteEventDataSource {

    suspend fun getEvents(): EventListResponseDto

    suspend fun getEventDetail(eventId: String): EventDetailResponseDto

    suspend fun getEventMatches(eventId: String): EventMatchesResponseDto

    suspend fun getEventNews(eventId: String): EventNewsListResponseDto

    suspend fun getEventStats(eventId: String): EventStatsResponseDto
}

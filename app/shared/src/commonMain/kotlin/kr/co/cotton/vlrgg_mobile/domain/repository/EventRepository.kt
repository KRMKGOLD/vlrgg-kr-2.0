package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventDetail
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStats
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary

interface EventRepository {

    suspend fun getEvents(): AppResult<EventList>

    suspend fun getEventDetail(eventId: String): AppResult<EventDetail>

    suspend fun getEventMatches(eventId: String): AppResult<List<MatchSummary>>

    suspend fun getEventNews(eventId: String): AppResult<List<NewsSummary>>

    suspend fun getEventStats(eventId: String): AppResult<EventStats>
}

package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList

interface EventRepository {

    suspend fun getEvents(): AppResult<EventList>
}

package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto

internal interface RemoteEventDataSource {

    suspend fun getEvents(): EventListResponseDto
}

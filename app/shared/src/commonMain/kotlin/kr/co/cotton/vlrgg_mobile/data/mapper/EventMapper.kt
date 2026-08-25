package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary

internal fun EventListResponseDto.toDomain(): EventList = EventList(
    ongoing = ongoing.map(EventSummaryDto::toDomain),
    upcoming = upcoming.map(EventSummaryDto::toDomain),
    completedOrPaused = completedOrPaused.map(EventSummaryDto::toDomain),
)

private fun EventSummaryDto.toDomain(): EventSummary = EventSummary(
    id = id,
    name = name,
    status = status.toDomain(),
    dateLabel = dateLabel,
    regionCode = regionCode,
    imageUrl = imageUrl,
)

private fun EventStatusDto.toDomain(): EventStatus = when (this) {
    EventStatusDto.ONGOING -> EventStatus.ONGOING
    EventStatusDto.UPCOMING -> EventStatus.UPCOMING
    EventStatusDto.COMPLETED -> EventStatus.COMPLETED
    EventStatusDto.PAUSED -> EventStatus.PAUSED
}

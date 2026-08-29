package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.series.SeriesDetailResponseDto
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail

internal fun SeriesDetailResponseDto.toDomain(): SeriesDetail = SeriesDetail(
    id = id,
    name = name,
    description = description,
    upcomingEvents = upcomingEvents.map { it.toDomain() },
    completedEvents = completedEvents.map { it.toDomain() },
)

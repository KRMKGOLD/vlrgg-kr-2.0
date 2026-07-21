package kr.co.cotton.vlrgg_mobile.feature.series

import kr.co.cotton.vlrgg_mobile.feature.events.EventStatus
import kr.co.cotton.vlrgg_mobile.feature.events.EventSummaryResponse

/** Maps the parser-owned source model to the public Series and Event summary contract. */
internal class SeriesMapper {
    fun map(source: SeriesSource): SeriesResponse = SeriesResponse(
        id = source.id,
        name = source.name,
        description = source.description,
        upcomingEvents = source.events
            .filter { it.status == SeriesEventStatusSource.ONGOING || it.status == SeriesEventStatusSource.UPCOMING }
            .map(::toEventSummary),
        completedEvents = source.events
            .filter { it.status == SeriesEventStatusSource.COMPLETED || it.status == SeriesEventStatusSource.PAUSED }
            .map(::toEventSummary),
    )

    private fun toEventSummary(source: SeriesEventSource): EventSummaryResponse = EventSummaryResponse(
        id = source.id,
        name = source.name,
        status = source.status.toEventStatus(),
        dateLabel = source.dateLabel,
        regionCode = source.regionCode,
        imageUrl = source.imageUrl,
    )

    private fun SeriesEventStatusSource.toEventStatus(): EventStatus = when (this) {
        SeriesEventStatusSource.ONGOING -> EventStatus.ONGOING
        SeriesEventStatusSource.UPCOMING -> EventStatus.UPCOMING
        SeriesEventStatusSource.COMPLETED -> EventStatus.COMPLETED
        SeriesEventStatusSource.PAUSED -> EventStatus.PAUSED
    }
}

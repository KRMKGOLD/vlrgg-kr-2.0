package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary

/** Consecutive event matches on the same [date]. A null [date] group is shown without a date header. */
internal data class EventMatchDay(
    val date: LocalDate?,
    val matches: List<MatchSummary>,
)

/**
 * Groups consecutive matches that share [MatchSummary.date] without reordering the response (the
 * source is already in date order). Matches without a date stay in their own run, so a response
 * without dates becomes one header-less group, the same list as before dates existed.
 */
internal fun List<MatchSummary>.groupByConsecutiveDate(): List<EventMatchDay> {
    val days = mutableListOf<EventMatchDay>()
    for (match in this) {
        val last = days.lastOrNull()
        if (last != null && last.date == match.date) {
            days[days.lastIndex] = last.copy(matches = last.matches + match)
        } else {
            days += EventMatchDay(match.date, listOf(match))
        }
    }
    return days
}

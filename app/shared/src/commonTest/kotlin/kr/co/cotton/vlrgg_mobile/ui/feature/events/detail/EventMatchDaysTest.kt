package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import kotlinx.datetime.LocalDate
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kotlin.test.Test
import kotlin.test.assertEquals

class EventMatchDaysTest {

    @Test
    fun consecutiveMatchesOnTheSameDateShareOneGroupInResponseOrder() {
        val days = listOf(
            match("a", SEP_13),
            match("b", SEP_13),
            match("c", SEP_14),
            match("d", SEP_15),
            match("e", SEP_15),
        ).groupByConsecutiveDate()

        assertEquals(listOf(SEP_13, SEP_14, SEP_15), days.map { it.date })
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d", "e")), days.map { day -> day.matches.map { it.id } })
    }

    @Test
    fun aDateThatReturnsLaterStartsANewGroupWithoutReordering() {
        val days = listOf(match("a", SEP_13), match("b", SEP_14), match("c", SEP_13)).groupByConsecutiveDate()

        assertEquals(listOf(SEP_13, SEP_14, SEP_13), days.map { it.date })
        assertEquals(listOf("a", "b", "c"), days.flatMap { day -> day.matches.map { it.id } })
    }

    @Test
    fun responseWithoutDatesIsOneHeaderlessGroup() {
        val days = listOf(match("a", null), match("b", null)).groupByConsecutiveDate()

        assertEquals(listOf(EventMatchDay(null, listOf(match("a", null), match("b", null)))), days)
    }

    @Test
    fun matchesWithoutADateStayInTheirOwnRunBetweenDatedGroups() {
        val days = listOf(match("a", SEP_13), match("b", null), match("c", null), match("d", SEP_13))
            .groupByConsecutiveDate()

        assertEquals(listOf(SEP_13, null, SEP_13), days.map { it.date })
        assertEquals(listOf(listOf("a"), listOf("b", "c"), listOf("d")), days.map { day -> day.matches.map { it.id } })
    }

    @Test
    fun emptyResponseHasNoGroups() {
        assertEquals(emptyList(), emptyList<MatchSummary>().groupByConsecutiveDate())
    }

    private fun match(id: String, date: LocalDate?) = MatchSummary(
        id = id,
        status = MatchStatus.UPCOMING,
        timeLabel = "6:00 PM",
        time = null,
        relativeTimeLabel = null,
        homeTeam = MatchTeam("Alpha", null),
        awayTeam = MatchTeam("Beta", null),
        homeScore = null,
        awayScore = null,
        event = MatchEvent("Masters Seoul", "Playoffs", "100"),
        date = date,
    )

    private companion object {
        val SEP_13 = LocalDate(2026, 9, 13)
        val SEP_14 = LocalDate(2026, 9, 14)
        val SEP_15 = LocalDate(2026, 9, 15)
    }
}

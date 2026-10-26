package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchSummary
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.MatchContentItemLeading
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.components.toMatchContentItem
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.remainingTimeLabel
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.scheduleLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class MatchDateTimeLabelTest {

    @Test
    fun dateHeaderUsesKoreanDateAndFallsBackToSourceLabel() {
        val group = MatchDateGroup(dateLabel = "Tue, October 27, 2026", matches = emptyList(), date = LocalDate(2026, 10, 27))

        assertEquals("10월 27일 (화)", group.displayDateLabel())
        assertEquals("Tue, October 27, 2026", group.copy(date = null).displayDateLabel())
    }

    @Test
    fun upcomingCardUsesKoreanTimeAndRemainingTime() {
        val leading = summary(time = LocalTime(3, 0), relativeTimeLabel = "1d 1h").leading()

        assertEquals(MatchContentItemLeading.Time(timeLabel = "오전 3:00", relativeTimeLabel = "1일 1시간 후"), leading)
    }

    @Test
    fun upcomingCardFallsBackToSourceTimeAndHidesUnknownRelativeTime() {
        val leading = summary(time = null, relativeTimeLabel = "IN 2 HOURS").leading()

        assertEquals(MatchContentItemLeading.Time(timeLabel = "3:00 AM", relativeTimeLabel = null), leading)
        assertEquals(
            MatchContentItemLeading.Time(timeLabel = "TBD", relativeTimeLabel = null),
            summary(timeLabel = "TBD", time = null, relativeTimeLabel = null).leading(),
        )
    }

    @Test
    fun nonUpcomingCardsKeepStatusLabelAndNeverShowRelativeTime() {
        assertEquals(
            MatchContentItemLeading.Status(status = StatusChipStatus.Completed, label = "종료"),
            summary(status = MatchStatus.COMPLETED, relativeTimeLabel = "10h 43m").leading(),
        )
        assertEquals(
            MatchContentItemLeading.Status(status = StatusChipStatus.Live, label = "LIVE"),
            summary(status = MatchStatus.LIVE, relativeTimeLabel = "22m").leading(),
        )
    }

    @Test
    fun detailScheduleUsesDeviceTimeZoneWhenScheduledAtExists() {
        val match = detail(scheduledAt = Instant.parse("2026-10-26T18:00:00Z"))

        assertEquals("2026년 10월 27일 (화) 오전 3:00", match.scheduleLabel(TimeZone.of("Asia/Seoul")))
        assertEquals("2026년 10월 26일 (월) 오후 2:00", match.scheduleLabel(TimeZone.of("America/New_York")))
    }

    @Test
    fun detailScheduleFallsBackToSourceTimeLabelWithoutScheduledAt() {
        assertEquals(
            "Tuesday, October 27 3:00 AM KST",
            detail(scheduledAt = null).scheduleLabel(TimeZone.of("Asia/Seoul")),
        )
    }

    @Test
    fun detailRemainingTimeIsOnlyShownForUpcomingMatches() {
        assertEquals("22분 후", detail(relativeTimeLabel = "22m").remainingTimeLabel())
        assertNull(detail(status = MatchStatus.COMPLETED, relativeTimeLabel = "22m").remainingTimeLabel())
        assertNull(detail(status = MatchStatus.LIVE, relativeTimeLabel = "22m").remainingTimeLabel())
        assertNull(detail(relativeTimeLabel = "final").remainingTimeLabel())
        assertNull(detail(relativeTimeLabel = null).remainingTimeLabel())
    }

    private fun MatchSummary.leading() = toMatchContentItem(showEventName = true).leadingContent

    private fun summary(
        status: MatchStatus = MatchStatus.UPCOMING,
        timeLabel: String = "3:00 AM",
        time: LocalTime? = LocalTime(3, 0),
        relativeTimeLabel: String? = null,
    ) = MatchSummary(
        id = "match-1",
        status = status,
        timeLabel = timeLabel,
        time = time,
        relativeTimeLabel = relativeTimeLabel,
        homeTeam = MatchTeam(name = "Home", id = null),
        awayTeam = MatchTeam(name = "Away", id = null),
        homeScore = null,
        awayScore = null,
        event = MatchEvent(name = "VCT", series = null, id = null),
    )

    private fun detail(
        status: MatchStatus = MatchStatus.UPCOMING,
        relativeTimeLabel: String? = null,
        scheduledAt: Instant? = null,
    ) = MatchDetail(
        id = "match-1",
        status = status,
        timeLabel = "Tuesday, October 27 3:00 AM KST",
        relativeTimeLabel = relativeTimeLabel,
        scheduledAt = scheduledAt,
        homeTeam = MatchTeam(name = "Home", id = null),
        awayTeam = MatchTeam(name = "Away", id = null),
        homeScore = null,
        awayScore = null,
        event = MatchEvent(name = "VCT", series = null, id = null),
        description = null,
        seriesFormat = null,
        maps = emptyList(),
        headToHead = emptyList(),
        pastMatches = emptyList(),
    )
}

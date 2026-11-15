package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class MatchDetailScoreboardTest {

    @Test
    fun upcomingScheduleAddsRemainingTimeLineAndOtherStatusesKeepOneLine() {
        val seoul = TimeZone.of("Asia/Seoul")
        val upcoming = detail(relativeTimeLabel = "2d 2h")
        assertEquals(listOf("2026년 9월 24일 (목) 오후 6:00", "2일 2시간 후 · Bo3"), upcoming.scheduleLines(seoul))
        assertEquals(
            listOf("2026년 9월 24일 (목) 오후 6:00 · Bo3"),
            detail(status = MatchStatus.COMPLETED, relativeTimeLabel = "10h 43m").scheduleLines(seoul),
        )
        // 남은 시간 원문을 알 수 없으면 예정 경기도 한 줄이다.
        assertEquals(listOf("2026년 9월 24일 (목) 오후 6:00 · Bo3"), detail(relativeTimeLabel = "TBD").scheduleLines(seoul))
        assertEquals(
            listOf("2026년 9월 24일 (목) 오후 6:00"),
            detail(status = MatchStatus.UNAVAILABLE, seriesFormat = null).scheduleLines(seoul),
        )
        assertEquals(listOf("2일 2시간 후"), detail(relativeTimeLabel = "2d 2h", seriesFormat = null, scheduledAtUtc = null, timeLabel = "").scheduleLines(seoul))
    }

    @Test
    fun onlyLiveAndCompletedMatchesWithAScoreShowNumbers() {
        assertTrue(detail(status = MatchStatus.LIVE, homeScore = 1, awayScore = 0).showsScoreboardScore())
        assertTrue(detail(status = MatchStatus.COMPLETED, homeScore = 2, awayScore = null).showsScoreboardScore())
        assertFalse(detail(status = MatchStatus.COMPLETED).showsScoreboardScore())
        listOf(MatchStatus.UPCOMING, MatchStatus.POSTPONED, MatchStatus.CANCELLED, MatchStatus.UNAVAILABLE).forEach {
            assertFalse(detail(status = it, homeScore = 1, awayScore = 0).showsScoreboardScore(), it.name)
        }
    }

    @Test
    fun scoreboardWinnerIsOnlyDecidedForCompletedMatchesWithDifferentScores() {
        assertEquals(MatchSide.Home, detail(status = MatchStatus.COMPLETED, homeScore = 2, awayScore = 1).scoreboardWinner())
        assertEquals(MatchSide.Away, detail(status = MatchStatus.COMPLETED, homeScore = 0, awayScore = 2).scoreboardWinner())
        assertNull(detail(status = MatchStatus.COMPLETED, homeScore = 1, awayScore = 1).scoreboardWinner())
        assertNull(detail(status = MatchStatus.COMPLETED, homeScore = 2, awayScore = null).scoreboardWinner())
        assertNull(detail(status = MatchStatus.LIVE, homeScore = 1, awayScore = 0).scoreboardWinner())
    }

    @Test
    fun scoreDescriptionsBundleTeamNamesWithScores() {
        assertEquals(
            "KIWOOM DRX 2, Paper Rex 1",
            detail(status = MatchStatus.COMPLETED, homeScore = 2, awayScore = 1).scoreAccessibilityLabel(),
        )
        assertEquals("KIWOOM DRX 2, Paper Rex —", detail(homeScore = 2).scoreAccessibilityLabel())
        assertEquals("경기 상세: A 2, B 0", RelatedMatch("1", "A", "B", 2, 0).accessibilityLabel())
        assertEquals("경기 상세: A 대 B", RelatedMatch("1", "A", "B", 0, null).accessibilityLabel())
    }

    @Test
    fun mapWinnerSkipsMapsStillInPlay() {
        assertEquals(MatchSide.Home, MatchMap("Haven", 13, 8).winner(MatchStatus.COMPLETED))
        assertEquals(MatchSide.Away, MatchMap("Ascent", 10, 13).winner(MatchStatus.COMPLETED))
        assertNull(MatchMap("Lotus", 0, null).winner(MatchStatus.COMPLETED))
        assertEquals(MatchSide.Home, MatchMap("Haven", 13, 9).winner(MatchStatus.LIVE))
        assertEquals(MatchSide.Away, MatchMap("Haven", 12, 14).winner(MatchStatus.LIVE))
        assertNull(MatchMap("Ascent", 8, 6).winner(MatchStatus.LIVE))
        assertNull(MatchMap("Ascent", 13, 12).winner(MatchStatus.LIVE))
        assertNull(MatchMap("Ascent", 13, 8).winner(MatchStatus.UNAVAILABLE))
    }

    private fun detail(
        status: MatchStatus = MatchStatus.UPCOMING,
        relativeTimeLabel: String? = null,
        homeScore: Int? = null,
        awayScore: Int? = null,
        seriesFormat: String? = "Bo3",
        scheduledAtUtc: Instant? = Instant.parse("2026-09-24T09:00:00Z"),
        timeLabel: String = "Thursday, September 24 6:00 PM KST",
    ) = MatchDetail(
        id = "match-1",
        status = status,
        timeLabel = timeLabel,
        relativeTimeLabel = relativeTimeLabel,
        scheduledAtUtc = scheduledAtUtc,
        homeTeam = MatchTeam(name = "KIWOOM DRX", id = null),
        awayTeam = MatchTeam(name = "Paper Rex", id = null),
        homeScore = homeScore,
        awayScore = awayScore,
        event = MatchEvent(name = "VCT", series = null, id = null),
        description = null,
        seriesFormat = seriesFormat,
        maps = emptyList(),
        headToHead = emptyList(),
        pastMatches = emptyList(),
    )
}

package kr.co.cotton.vlrgg_mobile.domain.model.matches

import kotlinx.datetime.LocalTime

data class MatchSummary(
    val id: String,
    val status: MatchStatus,
    val timeLabel: String,
    /** [timeLabel]의 시각. vlr.gg 표시 시간대(KST) 기준이며 instant가 아니다. */
    val time: LocalTime?,
    val relativeTimeLabel: String?,
    val homeTeam: MatchTeam,
    val awayTeam: MatchTeam,
    val homeScore: Int?,
    val awayScore: Int?,
    val event: MatchEvent,
)

package kr.co.cotton.vlrgg_mobile.domain.model.matches

import kotlinx.datetime.LocalDate
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
    /**
     * 경기가 속한 날짜 그룹의 달력 날짜(KST 표시 기준, instant가 아님). 대회 경기 응답 item만 채우고,
     * 경기 목록은 [MatchDateGroup.date]를 쓴다. 서버가 주지 않거나 형식이 맞지 않으면 null이다.
     */
    val date: LocalDate? = null,
)

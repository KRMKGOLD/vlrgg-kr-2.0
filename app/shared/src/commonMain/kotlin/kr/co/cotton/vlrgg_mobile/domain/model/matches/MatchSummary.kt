package kr.co.cotton.vlrgg_mobile.domain.model.matches

data class MatchSummary(
    val id: String,
    val status: MatchStatus,
    val timeLabel: String,
    val relativeTimeLabel: String?,
    val homeTeam: MatchTeam,
    val awayTeam: MatchTeam,
    val homeScore: Int?,
    val awayScore: Int?,
    val event: MatchEvent,
)

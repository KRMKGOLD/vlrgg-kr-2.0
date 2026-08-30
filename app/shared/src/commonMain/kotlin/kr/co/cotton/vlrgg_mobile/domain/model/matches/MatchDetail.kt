package kr.co.cotton.vlrgg_mobile.domain.model.matches

data class MatchDetail(
    val id: String,
    val status: MatchStatus,
    val timeLabel: String,
    val relativeTimeLabel: String?,
    val scheduledAt: String?,
    val homeTeam: MatchTeam,
    val awayTeam: MatchTeam,
    val homeScore: Int?,
    val awayScore: Int?,
    val event: MatchEvent,
    val description: String?,
    val seriesFormat: String?,
    val maps: List<MatchMap>,
    val headToHead: List<RelatedMatch>,
    val pastMatches: List<RelatedMatch>,
)

data class MatchMap(
    val name: String,
    val homeScore: Int?,
    val awayScore: Int?,
)

data class RelatedMatch(
    val id: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int?,
    val awayScore: Int?,
)

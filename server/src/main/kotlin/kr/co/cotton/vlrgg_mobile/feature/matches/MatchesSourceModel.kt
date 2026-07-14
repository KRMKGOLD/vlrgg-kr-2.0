package kr.co.cotton.vlrgg_mobile.feature.matches

/** Internal representation of the VLR.GG markup. It must not cross the HTTP boundary. */
internal data class MatchesPageSource(
    val groups: List<MatchDateGroupSource>,
)

internal data class MatchDateGroupSource(
    val dateLabel: String,
    val matches: List<MatchSummarySource>,
)

internal data class MatchSummarySource(
    val id: String,
    val status: MatchStatusSource,
    val timeLabel: String,
    val relativeTimeLabel: String?,
    val homeTeam: MatchTeamSource,
    val awayTeam: MatchTeamSource,
    val homeScore: Int?,
    val awayScore: Int?,
    val event: MatchEventSource,
)

internal data class MatchDetailSource(
    val summary: MatchSummarySource,
    val scheduledAt: String?,
    val description: String?,
    val seriesFormat: String?,
    val maps: List<MatchMapSource>,
    val headToHead: List<RelatedMatchSource>,
    val pastMatches: List<RelatedMatchSource>,
)

internal data class MatchTeamSource(
    val name: String,
    val id: String? = null,
)

internal data class MatchEventSource(
    val name: String,
    val series: String?,
    val id: String? = null,
)

internal data class MatchMapSource(
    val name: String,
    val homeScore: Int?,
    val awayScore: Int?,
)

/** Source-backed related-match data. It is never serialized directly. */
internal data class RelatedMatchSource(
    val id: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int?,
    val awayScore: Int?,
)

internal enum class MatchStatusSource {
    UPCOMING,
    LIVE,
    COMPLETED,
    POSTPONED,
    CANCELLED,
    UNAVAILABLE,
}

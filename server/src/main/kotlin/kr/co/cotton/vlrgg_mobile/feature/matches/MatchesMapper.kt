package kr.co.cotton.vlrgg_mobile.feature.matches

internal class MatchesMapper {
    fun toPageResponse(
        category: MatchListCategory,
        page: Int,
        source: MatchesPageSource,
    ): MatchesPageResponse = MatchesPageResponse(
        category = category,
        page = page,
        groups = source.groups.map { group ->
            MatchDateGroupResponse(
                dateLabel = group.dateLabel,
                matches = group.matches.map(::toSummaryResponse),
            )
        },
    )

    fun toDetailResponse(source: MatchDetailSource): MatchDetailResponse = source.summary.let { summary ->
        MatchDetailResponse(
            id = summary.id,
            status = summary.status.toResponse(),
            timeLabel = summary.timeLabel,
            relativeTimeLabel = summary.relativeTimeLabel,
            scheduledAt = source.scheduledAt,
            homeTeam = summary.homeTeam.toResponse(),
            awayTeam = summary.awayTeam.toResponse(),
            homeScore = summary.homeScore,
            awayScore = summary.awayScore,
            event = summary.event.toResponse(),
            description = source.description,
            seriesFormat = source.seriesFormat,
            maps = source.maps.map { map ->
                MatchMapResponse(
                    name = map.name,
                    homeScore = map.homeScore,
                    awayScore = map.awayScore,
                )
            },
            headToHead = source.headToHead.map(::toRelatedMatchResponse),
            pastMatches = source.pastMatches.map(::toRelatedMatchResponse),
        )
    }

    private fun toSummaryResponse(source: MatchSummarySource): MatchSummaryResponse = MatchSummaryResponse(
        id = source.id,
        status = source.status.toResponse(),
        timeLabel = source.timeLabel,
        relativeTimeLabel = source.relativeTimeLabel,
        homeTeam = source.homeTeam.toResponse(),
        awayTeam = source.awayTeam.toResponse(),
        homeScore = source.homeScore,
        awayScore = source.awayScore,
        event = source.event.toResponse(),
    )

    private fun MatchTeamSource.toResponse() = MatchTeamResponse(name = name, id = id)

    private fun MatchEventSource.toResponse() = MatchEventResponse(name = name, series = series, id = id)

    private fun toRelatedMatchResponse(source: RelatedMatchSource) = RelatedMatchResponse(
        id = source.id,
        homeTeamName = source.homeTeamName,
        awayTeamName = source.awayTeamName,
        homeScore = source.homeScore,
        awayScore = source.awayScore,
    )

    private fun MatchStatusSource.toResponse(): MatchStatus = when (this) {
        MatchStatusSource.UPCOMING -> MatchStatus.UPCOMING
        MatchStatusSource.LIVE -> MatchStatus.LIVE
        MatchStatusSource.COMPLETED -> MatchStatus.COMPLETED
        MatchStatusSource.POSTPONED -> MatchStatus.POSTPONED
        MatchStatusSource.CANCELLED -> MatchStatus.CANCELLED
        MatchStatusSource.UNAVAILABLE -> MatchStatus.UNAVAILABLE
    }
}

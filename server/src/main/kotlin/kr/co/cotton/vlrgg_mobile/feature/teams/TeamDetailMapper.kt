package kr.co.cotton.vlrgg_mobile.feature.teams

/** Converts VLR.GG-shaped internal source data into the stable v1 app contract. */
internal class TeamDetailMapper {
    fun map(teamId: TeamId, source: TeamDetailSource): TeamDetailResponse = TeamDetailResponse(
        id = teamId.value,
        name = source.profile.name,
        tag = source.profile.tag,
        country = source.profile.country,
        upcomingMatches = source.upcomingMatches.map { it.toResponse() },
        recentMatches = source.recentMatches.map { it.toResponse() },
        players = source.players.map { it.toResponse() },
        staff = source.staff.map { it.toResponse() },
        news = source.news.map { it.toResponse() },
    )

    private fun TeamMatchSource.toResponse() = TeamMatchResponse(
        id, eventName, eventStage, teamName, opponentName, statusText, scheduledAtText,
    )

    private fun TeamRosterMemberSource.toResponse() = TeamRosterMemberResponse(
        id, handle, realName, roleLabels,
    )

    private fun TeamNewsSource.toResponse() = TeamNewsResponse(reference.value, title, publishedDateText)
}

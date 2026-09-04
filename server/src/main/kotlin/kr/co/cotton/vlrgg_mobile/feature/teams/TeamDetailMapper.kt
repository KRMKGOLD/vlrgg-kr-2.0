package kr.co.cotton.vlrgg_mobile.feature.teams

/** Converts VLR.GG-shaped internal source data into the stable v1 app contract. */
internal class TeamDetailMapper {
    fun map(teamId: TeamId, source: TeamDetailSource): TeamDetailResponse = TeamDetailResponse(
        id = teamId.value,
        name = source.profile.name,
        tag = source.profile.tag,
        country = source.profile.country,
        logoUrl = source.profile.logoUrl,
        upcomingMatches = source.upcomingMatches.map { it.toResponse() },
        recentMatches = source.recentMatches.map { it.toResponse() },
        players = source.players.map { it.toResponse() },
        staff = source.staff.map { it.toResponse() },
        news = source.news.map { it.toResponse() },
    )

    private fun TeamMatchSource.toResponse() = TeamMatchResponse(
        id = id,
        eventName = eventName,
        eventStage = eventStage,
        teamName = teamName,
        opponentName = opponentName,
        statusText = statusText,
        scheduledAtText = scheduledAtText,
    )

    private fun TeamRosterMemberSource.toResponse() = TeamRosterMemberResponse(
        id = id,
        handle = handle,
        realName = realName,
        roleLabels = roleLabels,
        imageUrl = imageUrl,
    )

    private fun TeamNewsSource.toResponse() = TeamNewsResponse(
        reference = reference.value,
        title = title,
        publishedDateText = publishedDateText,
    )
}

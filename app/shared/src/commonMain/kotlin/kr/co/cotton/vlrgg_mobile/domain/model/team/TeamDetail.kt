package kr.co.cotton.vlrgg_mobile.domain.model.team

data class TeamDetail(
    val id: String,
    val name: String,
    val tag: String?,
    val country: String?,
    val upcomingMatches: List<TeamMatch>,
    val recentMatches: List<TeamMatch>,
    val players: List<TeamRosterMember>,
    val staff: List<TeamRosterMember>,
    val news: List<TeamNews>,
)

data class TeamMatch(
    val id: String,
    val eventName: String?,
    val eventStage: String?,
    val teamName: String,
    val opponentName: String,
    val statusText: String?,
    val scheduledAtText: String?,
)

data class TeamRosterMember(
    val id: String,
    val handle: String,
    val realName: String?,
    val roleLabels: List<String>,
)

data class TeamNews(
    val articleId: String,
    val slug: String,
    val title: String,
    val publishedDateText: String?,
)

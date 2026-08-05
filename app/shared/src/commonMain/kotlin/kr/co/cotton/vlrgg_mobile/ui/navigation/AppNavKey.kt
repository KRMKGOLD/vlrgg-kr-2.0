package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey

@Serializable
sealed interface RootNavKey : AppNavKey

@Serializable
data object NewsRoot : RootNavKey

@Serializable
data object MatchesRoot : RootNavKey

@Serializable
data object MyPageRoot : RootNavKey

@Serializable
data object EventsRoot : RootNavKey

@Serializable
data object AboutRoot : RootNavKey

@Serializable
data object Search : AppNavKey

@Serializable
data class NewsDetail(
    val articleId: String,
    val slug: String,
) : AppNavKey

@Serializable
data class MatchDetail(
    val matchId: String,
) : AppNavKey

@Serializable
data class EventDetail(
    val eventId: String,
) : AppNavKey

@Serializable
data class TeamDetail(
    val teamId: String,
) : AppNavKey

@Serializable
data class PlayerDetail(
    val playerId: String,
) : AppNavKey

@Serializable
data class SeriesDetail(
    val seriesId: String,
) : AppNavKey

val rootNavKeys: List<RootNavKey> = listOf(
    NewsRoot,
    MatchesRoot,
    MyPageRoot,
    EventsRoot,
    AboutRoot,
)

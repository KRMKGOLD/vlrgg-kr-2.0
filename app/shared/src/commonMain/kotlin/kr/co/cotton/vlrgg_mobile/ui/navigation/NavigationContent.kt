package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.co.cotton.vlrgg_mobile.ui.feature.events.EventsScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses.OpenSourceLicensesScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.EventDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.MatchesScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MatchDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.news.detail.NewsDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.NewsScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.search.SearchScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.TeamDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.player.detail.PlayerDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.series.detail.SeriesDetailScreen
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult

@Composable
fun NavigationContent(
    destination: AppNavKey,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    aboutPlatform: AboutPlatform = UnavailableAboutPlatform,
    readLicenseDefinitions: suspend () -> String = ::unavailableLicenseDefinitions,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        MyPageRoot -> MyPageScreen(
            onSearch = onSearch,
            onTeamClick = { teamId -> onPush(TeamDetail(teamId)) },
            onPlayerClick = { playerId -> onPush(PlayerDetail(playerId)) },
            modifier = modifier.fillMaxSize(),
        )

        NewsRoot -> NewsScreen(
            onSearch = onSearch,
            onNewsClick = { articleId, slug ->
                onPush(
                    NewsDetail(
                        articleId = articleId,
                        slug = slug,
                    )
                )
            },
            modifier = modifier.fillMaxSize(),
        )

        MatchesRoot -> MatchesScreen(
            onSearch = onSearch,
            onMatchClick = { matchId -> onPush(MatchDetail(matchId = matchId)) },
            modifier = modifier.fillMaxSize(),
        )

        EventsRoot -> EventsScreen(
            onSearch = onSearch,
            onEventClick = { eventId -> onPush(EventDetail(eventId = eventId)) },
            modifier = modifier.fillMaxSize(),
        )

        AboutRoot -> AboutScreen(
            platform = aboutPlatform,
            onSearch = onSearch,
            onOpenSourceLicensesClick = { onPush(OpenSourceLicenses) },
            modifier = modifier,
        )

        OpenSourceLicenses -> OpenSourceLicensesScreen(
            readLicenseDefinitions = readLicenseDefinitions,
            onBack = onBack,
            modifier = modifier,
        )

        Search -> SearchScreen(
            onBack = onBack,
            onResultClick = { result -> onPush(destinationForSearchResult(result)) },
            modifier = modifier,
        )

        is NewsDetail -> NewsDetailScreen(
            articleId = destination.articleId,
            slug = destination.slug,
            onBack = onBack,
            onTeamClick = { teamId -> onPush(TeamDetail(teamId = teamId)) },
            onPlayerClick = { playerId -> onPush(PlayerDetail(playerId = playerId)) },
            modifier = Modifier.fillMaxSize(),
        )

        is EventDetail -> EventDetailScreen(
            eventId = destination.eventId,
            onBack = onBack,
            onMatchClick = { matchId -> onPush(MatchDetail(matchId)) },
            onNewsClick = { articleId, slug -> onPush(NewsDetail(articleId, slug)) },
            onPlayerClick = { playerId -> onPush(PlayerDetail(playerId)) },
            modifier = modifier.fillMaxSize(),
        )

        is TeamDetail -> TeamDetailScreen(
            teamId = destination.teamId,
            onBack = onBack,
            onMatchClick = { matchId -> onPush(MatchDetail(matchId)) },
            onPlayerClick = { playerId -> onPush(PlayerDetail(playerId)) },
            onNewsClick = { articleId, slug -> onPush(NewsDetail(articleId, slug)) },
            modifier = modifier.fillMaxSize(),
        )

        is PlayerDetail -> PlayerDetailScreen(
            playerId = destination.playerId,
            onBack = onBack,
            onTeamClick = { teamId -> onPush(TeamDetail(teamId)) },
            onMatchClick = { matchId -> onPush(MatchDetail(matchId)) },
            modifier = modifier.fillMaxSize(),
        )

        is SeriesDetail -> SeriesDetailScreen(
            seriesId = destination.seriesId,
            onBack = onBack,
            onEventClick = { eventId -> onPush(EventDetail(eventId)) },
            modifier = modifier.fillMaxSize(),
        )

        is MatchDetail -> MatchDetailScreen(
            matchId = destination.matchId,
            onBack = onBack,
            onEventClick = { eventId -> onPush(EventDetail(eventId)) },
            onTeamClick = { teamId -> onPush(TeamDetail(teamId)) },
            onMatchClick = { matchId -> onPush(MatchDetail(matchId)) },
            modifier = modifier.fillMaxSize(),
        )
    }
}

private object UnavailableAboutPlatform : AboutPlatform {
    override val buildVersion: String? = null

    override fun openUrl(url: String, onResult: (Boolean) -> Unit) = onResult(false)
}

private fun unavailableLicenseDefinitions(): String = error("License definitions are unavailable.")

internal fun destinationForSearchResult(result: SearchResult): AppNavKey = when (result) {
    is SeriesSearchResult -> SeriesDetail(result.id)
    is EventSearchResult -> EventDetail(result.id)
    is TeamSearchResult -> TeamDetail(result.id)
    is PlayerSearchResult -> PlayerDetail(result.id)
}

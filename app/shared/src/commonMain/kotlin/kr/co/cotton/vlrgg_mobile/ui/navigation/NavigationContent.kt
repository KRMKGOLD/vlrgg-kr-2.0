package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.vectorResource
import kr.co.cotton.vlrgg_mobile.ui.feature.events.EventsScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.EventDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.MatchesScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.news.detail.NewsDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.NewsScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.search.SearchScreen
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search

@Composable
fun NavigationContent(
    destination: AppNavKey,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        MyPageRoot -> MyPageScreen(
            onSearch = onSearch,
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

        AboutRoot,
            -> RootContent(
            destination = destination,
            onSearch = onSearch,
            modifier = modifier,
        ) {
            DestinationMarker(destination)
        }

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

        is MatchDetail,
        is TeamDetail,
        is PlayerDetail,
        is SeriesDetail,
            -> PushedContent(
            destination = destination,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

internal fun destinationForSearchResult(result: SearchResult): AppNavKey = when (result) {
    is SeriesSearchResult -> SeriesDetail(result.id)
    is EventSearchResult -> EventDetail(result.id)
    is TeamSearchResult -> TeamDetail(result.id)
    is PlayerSearchResult -> PlayerDetail(result.id)
}

@Composable
private fun RootContent(
    destination: AppNavKey,
    onSearch: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VlrTheme.colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = VlrDimensions.Space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = destination.destinationDescriptor.title,
                    modifier = Modifier.weight(1f),
                    style = VlrTheme.typography.pageTitle,
                    color = VlrTheme.colors.textPrimary,
                )
                VlrIconButton(
                    contentDescription = "검색",
                    onClick = onSearch,
                    icon = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                )
            }
            HorizontalDivider(
                thickness = VlrDimensions.OutlineWidth,
                color = VlrTheme.colors.outline,
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun PushedContent(
    destination: AppNavKey,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DestinationMarker(destination)
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun DestinationMarker(destination: AppNavKey) {
    Text(
        text = destination.destinationDescriptor.marker,
        style = MaterialTheme.typography.bodyLarge,
    )
}

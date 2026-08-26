package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.feature.events.EventsScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.events.detail.EventDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.MatchesScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.news.detail.NewsDetailScreen
import kr.co.cotton.vlrgg_mobile.ui.feature.news.list.NewsScreen

@Composable
fun NavigationContent(
    destination: AppNavKey,
    onSearch: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        MyPageRoot -> RootContent(
            destination = destination,
            onSearch = onSearch,
            modifier = modifier,
        ) {
            MyPageScreen(
                modifier = Modifier.fillMaxSize(),
            )
        }

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

        Search -> SearchContent(
            onBack = onBack,
            onPush = onPush,
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

@Composable
private fun RootContent(
    destination: AppNavKey,
    onSearch: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = destination.destinationDescriptor.title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(onClick = onSearch) {
            Text("Search")
        }
        content()
    }
}

@Composable
private fun SearchContent(
    onBack: () -> Unit,
    onPush: (AppNavKey) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = Search.destinationDescriptor.title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(onClick = onBack) {
            Text("Back")
        }
        Button(onClick = { onPush(NewsDetail(articleId = "1", slug = "news")) }) {
            Text("News Detail")
        }
        Button(onClick = { onPush(MatchDetail(matchId = "1")) }) {
            Text("Match Detail")
        }
        Button(onClick = { onPush(EventDetail(eventId = "1")) }) {
            Text("Event Detail")
        }
        Button(onClick = { onPush(TeamDetail(teamId = "1")) }) {
            Text("Team Detail")
        }
        Button(onClick = { onPush(PlayerDetail(playerId = "1")) }) {
            Text("Player Detail")
        }
        Button(onClick = { onPush(SeriesDetail(seriesId = "1")) }) {
            Text("Series Detail")
        }
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

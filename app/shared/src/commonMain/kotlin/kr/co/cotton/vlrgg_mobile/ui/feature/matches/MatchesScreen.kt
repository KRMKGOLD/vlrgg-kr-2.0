package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun MatchesScreen(
    onSearch: () -> Unit,
    onMatchClick: (matchId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatchesViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val upcomingLiveListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()

    MatchesContent(
        uiState = uiState,
        upcomingLiveListState = upcomingLiveListState,
        resultsListState = resultsListState,
        onSearch = onSearch,
        onSelectTab = viewModel::selectTab,
        onMatchClick = onMatchClick,
        onRefresh = { viewModel.refresh(uiState.selectedTab) },
        onRetryInitial = { viewModel.retryInitial(uiState.selectedTab) },
        onLoadMore = { viewModel.loadMore(uiState.selectedTab) },
        onRetryLoadMore = { viewModel.retryLoadMore(uiState.selectedTab) },
        modifier = modifier,
    )
}

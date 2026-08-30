package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun TeamDetailScreen(
    teamId: String,
    onBack: () -> Unit,
    onMatchClick: (matchId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    onNewsClick: (articleId: String, slug: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<TeamDetailViewModel, TeamDetailViewModel.Factory>(
        key = teamId,
    ) {
        create(teamId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TeamDetailContent(
        uiState = uiState,
        listState = rememberLazyListState(),
        onBack = onBack,
        onMatchClick = onMatchClick,
        onPlayerClick = onPlayerClick,
        onNewsClick = onNewsClick,
        onRetry = viewModel::retry,
        onFavoriteToggle = viewModel::toggleFavorite,
        onFavoriteRetry = viewModel::retryFavoriteMutation,
        onFavoriteErrorDismiss = viewModel::dismissFavoriteError,
        modifier = modifier,
    )
}

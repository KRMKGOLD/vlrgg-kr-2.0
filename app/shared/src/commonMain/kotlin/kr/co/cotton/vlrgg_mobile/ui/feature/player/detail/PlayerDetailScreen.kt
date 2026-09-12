package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryDialog

@Composable
fun PlayerDetailScreen(
    playerId: String,
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<PlayerDetailViewModel, PlayerDetailViewModel.Factory>(key = playerId) { creationExtras ->
        create(playerId, creationExtras.createSavedStateHandle())
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerDetailContent(
        uiState = uiState,
        listState = rememberLazyListState(),
        agentStatsHorizontalScrollState = rememberScrollState(),
        onBack = onBack,
        onTeamClick = onTeamClick,
        onMatchClick = onMatchClick,
        onRetry = viewModel::retry,
        onFavoriteClick = viewModel::toggleFavorite,
        onFavoriteRetry = viewModel::retryFavoriteMutation,
        onFavoriteRestoreRetry = viewModel::retryFavoriteRestore,
        onFavoriteErrorDismiss = viewModel::dismissFavoriteError,
        onAgentStatsSortColumn = viewModel::sortAgentStats,
        modifier = modifier,
    )
    BusyRetryDialog(uiState.busyRetry, viewModel::retryBusy, viewModel::dismissBusy)
}

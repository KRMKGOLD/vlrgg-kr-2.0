package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun PlayerDetailScreen(
    playerId: String,
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<PlayerDetailViewModel, PlayerDetailViewModel.Factory>(key = playerId) {
        create(playerId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerDetailContent(
        uiState = uiState,
        listState = rememberLazyListState(),
        onBack = onBack,
        onTeamClick = onTeamClick,
        onMatchClick = onMatchClick,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

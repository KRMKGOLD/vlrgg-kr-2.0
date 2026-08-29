package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun MatchDetailScreen(
    matchId: String,
    onBack: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    onTeamClick: (teamId: String) -> Unit,
    onMatchClick: (matchId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<MatchDetailViewModel, MatchDetailViewModel.Factory>(
        key = matchId,
    ) {
        create(matchId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MatchDetailContent(
        uiState = uiState,
        listState = rememberLazyListState(),
        onBack = onBack,
        onEventClick = onEventClick,
        onTeamClick = onTeamClick,
        onMatchClick = onMatchClick,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

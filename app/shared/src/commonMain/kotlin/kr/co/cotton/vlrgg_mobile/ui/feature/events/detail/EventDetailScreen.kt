package kr.co.cotton.vlrgg_mobile.ui.feature.events.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    onNewsClick: (String, String) -> Unit,
    onPlayerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<EventDetailViewModel, EventDetailViewModel.Factory>(
        key = eventId,
    ) { creationExtras ->
        create(eventId, creationExtras.createSavedStateHandle())
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EventDetailContent(
        uiState = uiState,
        matchesListState = rememberLazyListState(),
        newsListState = rememberLazyListState(),
        statsListState = rememberLazyListState(),
        statsHorizontalScrollState = rememberScrollState(),
        onBack = onBack,
        onSelectTab = viewModel::selectTab,
        onMatchClick = onMatchClick,
        onNewsClick = onNewsClick,
        onPlayerClick = onPlayerClick,
        onRetryIdentity = viewModel::retryIdentity,
        onRetrySelectedTab = viewModel::retrySelectedTab,
        modifier = modifier,
    )
}

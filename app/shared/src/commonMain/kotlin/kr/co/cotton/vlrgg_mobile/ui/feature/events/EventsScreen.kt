package kr.co.cotton.vlrgg_mobile.ui.feature.events

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun EventsScreen(
    onSearch: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    EventsContent(
        uiState = uiState,
        listState = listState,
        onSearch = onSearch,
        onEventClick = onEventClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

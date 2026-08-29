package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onEventClick: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<SeriesDetailViewModel, SeriesDetailViewModel.Factory>(key = seriesId) {
        create(seriesId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SeriesDetailContent(
        uiState = uiState,
        listState = rememberLazyListState(),
        onBack = onBack,
        onEventClick = onEventClick,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

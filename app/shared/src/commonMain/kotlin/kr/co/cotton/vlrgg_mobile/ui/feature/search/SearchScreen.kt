package kr.co.cotton.vlrgg_mobile.ui.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onBack = onBack,
        onResultClick = onResultClick,
        modifier = modifier,
    )
}

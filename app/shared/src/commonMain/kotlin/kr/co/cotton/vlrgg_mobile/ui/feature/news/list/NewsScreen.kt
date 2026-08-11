package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun NewsScreen(
    onSearch: () -> Unit,
    onNewsClick: (articleId: String, slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsListViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    NewsContent(
        uiState = uiState,
        listState = listState,
        onSearch = onSearch,
        onNewsClick = onNewsClick,
        onRefresh = viewModel::refresh,
        onRetryInitial = viewModel::retryInitial,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        modifier = modifier,
    )
}

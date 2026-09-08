package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState

sealed interface NewsListContentState {
    data object Loading : NewsListContentState

    data object Empty : NewsListContentState

    data class Content(
        val items: List<NewsSummary>,
    ) : NewsListContentState

    data object Error : NewsListContentState
}

data class NewsListUiState(
    val contentState: NewsListContentState = NewsListContentState.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasPaginationError: Boolean = false,
    val busyRetry: BusyRetryState? = null,
)

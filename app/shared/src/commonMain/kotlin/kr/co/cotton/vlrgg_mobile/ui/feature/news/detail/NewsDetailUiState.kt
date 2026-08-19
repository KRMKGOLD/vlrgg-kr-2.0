package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle

sealed interface NewsDetailContentState {
    data object Loading : NewsDetailContentState

    data class Empty(
        val article: NewsArticle,
    ) : NewsDetailContentState

    data class Content(
        val article: NewsArticle,
    ) : NewsDetailContentState

    data object Error : NewsDetailContentState
}

data class NewsDetailUiState(
    val contentState: NewsDetailContentState = NewsDetailContentState.Loading,
)

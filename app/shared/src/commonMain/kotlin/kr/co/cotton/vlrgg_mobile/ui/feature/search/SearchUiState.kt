package kr.co.cotton.vlrgg_mobile.ui.feature.search

import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState

sealed interface SearchContentState {
    data object Initial : SearchContentState

    data object Loading : SearchContentState

    data class Populated(
        val items: List<SearchResult>,
    ) : SearchContentState

    data object Empty : SearchContentState

    data object Error : SearchContentState
}

data class SearchUiState(
    val query: String = "",
    val contentState: SearchContentState = SearchContentState.Initial,
    val busyRetry: BusyRetryState? = null,
) {
    val canSubmit: Boolean
        get() = isSearchQueryValid(query)
}

internal const val MAX_SEARCH_QUERY_LENGTH = 30

internal fun isSearchQueryValid(query: String): Boolean {
    val normalizedQuery = query.trim()
    return normalizedQuery.length in 1..MAX_SEARCH_QUERY_LENGTH &&
        normalizedQuery.any(Char::isLetterOrDigit)
}

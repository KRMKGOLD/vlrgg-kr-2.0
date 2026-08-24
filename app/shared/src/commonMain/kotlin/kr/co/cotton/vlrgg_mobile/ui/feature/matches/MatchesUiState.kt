package kr.co.cotton.vlrgg_mobile.ui.feature.matches

import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDateGroup

enum class MatchesTab(
    val savedStateId: String,
) {
    UPCOMING_LIVE("upcoming_live"),
    RESULTS("results"),
    ;

    companion object {
        internal fun fromSavedStateId(savedStateId: String?): MatchesTab =
            entries.firstOrNull { tab -> tab.savedStateId == savedStateId } ?: UPCOMING_LIVE
    }
}

sealed interface MatchesFeedContentState {
    data object Loading : MatchesFeedContentState

    data object Empty : MatchesFeedContentState

    data class Content(
        val groups: List<MatchDateGroup>,
    ) : MatchesFeedContentState

    data object Error : MatchesFeedContentState
}

data class MatchesFeedUiState(
    val contentState: MatchesFeedContentState = MatchesFeedContentState.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasPaginationError: Boolean = false,
)

data class MatchesUiState(
    val selectedTab: MatchesTab = MatchesTab.UPCOMING_LIVE,
    val upcomingLive: MatchesFeedUiState = MatchesFeedUiState(),
    val results: MatchesFeedUiState = MatchesFeedUiState(),
)

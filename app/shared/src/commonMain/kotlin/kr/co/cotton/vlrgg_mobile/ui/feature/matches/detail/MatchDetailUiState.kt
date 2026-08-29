package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail

sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState

    data class Content(
        val match: MatchDetail,
    ) : MatchDetailUiState

    data object Error : MatchDetailUiState
}

package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail

sealed interface MatchDetailContentState {
    data object Loading : MatchDetailContentState

    data class Content(
        val match: MatchDetail,
    ) : MatchDetailContentState

    data object Error : MatchDetailContentState
}

data class MatchDetailUiState(
    val contentState: MatchDetailContentState = MatchDetailContentState.Loading,
)

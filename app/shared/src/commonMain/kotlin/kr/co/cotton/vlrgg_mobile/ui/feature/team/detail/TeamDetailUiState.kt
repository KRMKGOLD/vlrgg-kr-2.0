package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail

sealed interface TeamDetailContentState {
    data object Loading : TeamDetailContentState

    data class Content(
        val team: TeamDetail,
    ) : TeamDetailContentState

    data object Error : TeamDetailContentState
}

data class TeamDetailUiState(
    val contentState: TeamDetailContentState = TeamDetailContentState.Loading,
)

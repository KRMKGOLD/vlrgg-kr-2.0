package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState

sealed interface TeamDetailContentState {
    data object Loading : TeamDetailContentState

    data class Content(
        val team: TeamDetail,
    ) : TeamDetailContentState

    data object Error : TeamDetailContentState
}

enum class TeamFavoriteMutationIntent {
    Add,
    Remove,
}

data class TeamFavoriteUiState(
    val isFavorite: Boolean = false,
    val isRestored: Boolean = false,
    val hasRestoreFailure: Boolean = false,
    val isMutationInProgress: Boolean = false,
    val failedIntent: TeamFavoriteMutationIntent? = null,
)

data class TeamDetailUiState(
    val contentState: TeamDetailContentState = TeamDetailContentState.Loading,
    val favorite: TeamFavoriteUiState = TeamFavoriteUiState(),
    val busyRetry: BusyRetryState? = null,
)

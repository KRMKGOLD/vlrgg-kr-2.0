package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState

sealed interface PlayerDetailContentState {
    data object Loading : PlayerDetailContentState
    data class Content(val player: PlayerDetail) : PlayerDetailContentState
    data object Error : PlayerDetailContentState
}

data class PlayerDetailUiState(
    val contentState: PlayerDetailContentState = PlayerDetailContentState.Loading,
    val favorite: PlayerFavoriteUiState = PlayerFavoriteUiState(),
    val busyRetry: BusyRetryState? = null,
)

data class PlayerFavoriteUiState(
    val isFavorite: Boolean = false,
    val isRestored: Boolean = false,
    val hasRestoreFailure: Boolean = false,
    val isMutationInProgress: Boolean = false,
    val failedIntent: PlayerFavoriteMutationIntent? = null,
)

enum class PlayerFavoriteMutationIntent {
    Add,
    Remove,
}

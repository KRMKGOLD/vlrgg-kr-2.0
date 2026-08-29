package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail

sealed interface PlayerDetailContentState {
    data object Loading : PlayerDetailContentState
    data class Content(val player: PlayerDetail) : PlayerDetailContentState
    data object Error : PlayerDetailContentState
}

data class PlayerDetailUiState(
    val contentState: PlayerDetailContentState = PlayerDetailContentState.Loading,
)

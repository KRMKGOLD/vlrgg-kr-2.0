package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort

enum class PlayerAgentStatsSortColumn(
    val label: String,
    val savedStateId: String,
) {
    MAPS("Maps", "maps"),
    PICK_RATE("Pick Rate", "pick-rate"),
    RATING("Rating", "rating"),
    ACS("ACS", "acs"),
    K_D("K/D", "k-d"),
    KAST("KAST", "kast"),
    ADR("ADR", "adr"),
    ;

    companion object {
        internal fun fromSavedStateId(savedStateId: String?): PlayerAgentStatsSortColumn? =
            entries.firstOrNull { it.savedStateId == savedStateId }
    }
}

sealed interface PlayerDetailContentState {
    data object Loading : PlayerDetailContentState
    data class Content(val player: PlayerDetail) : PlayerDetailContentState
    data object Error : PlayerDetailContentState
}

data class PlayerDetailUiState(
    val contentState: PlayerDetailContentState = PlayerDetailContentState.Loading,
    val favorite: PlayerFavoriteUiState = PlayerFavoriteUiState(),
    val busyRetry: BusyRetryState? = null,
    val agentStatsSort: StatsSort<PlayerAgentStatsSortColumn>? = null,
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

package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam

data class MyPageUiState(
    val favoriteTeams: FavoriteSectionState<FavoriteTeam> = FavoriteSectionState.Loading,
    val favoritePlayers: FavoriteSectionState<FavoritePlayer> = FavoriteSectionState.Loading,
    val isFullError: Boolean = false,
    val removingFavorite: FavoriteRemovalTarget? = null,
    val failedRemoval: FavoriteRemovalTarget? = null,
)

sealed interface FavoriteSectionState<out T> {
    data object Loading : FavoriteSectionState<Nothing>

    data class Content<T>(
        val favorites: List<T>,
    ) : FavoriteSectionState<T> {
        init {
            require(favorites.isNotEmpty()) { "Content requires at least one favorite." }
        }
    }

    data object Empty : FavoriteSectionState<Nothing>

    data object Error : FavoriteSectionState<Nothing>
}

sealed interface FavoriteRemovalTarget {
    val id: String

    data class Team(
        override val id: String,
    ) : FavoriteRemovalTarget

    data class Player(
        override val id: String,
    ) : FavoriteRemovalTarget
}

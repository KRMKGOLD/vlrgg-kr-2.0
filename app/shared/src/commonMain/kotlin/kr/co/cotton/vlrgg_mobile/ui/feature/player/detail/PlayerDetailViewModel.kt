package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.onFailure
import kr.co.cotton.vlrgg_mobile.domain.onBusy
import kr.co.cotton.vlrgg_mobile.domain.onSuccess
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@AssistedInject
class PlayerDetailViewModel(
    private val playerRepository: PlayerRepository,
    private val favoriteRepository: FavoriteRepository,
    @Assisted private val playerId: String,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerDetailUiState())
    val uiState: StateFlow<PlayerDetailUiState> = _uiState.asStateFlow()

    private var failedFavoriteMutation: FavoriteMutation? = null
    private var isFavoriteRestoreInProgress = false
    private var playerDetailJob: Job? = null

    init {
        loadPlayerDetail()
        restoreFavorite()
    }

    fun retry() {
        if (playerDetailJob?.isActive == true) return
        if (isBusyCooldown()) return
        if (_uiState.value.contentState != PlayerDetailContentState.Error) return
        _uiState.value = _uiState.value.copy(contentState = PlayerDetailContentState.Loading, busyRetry = null)
        loadPlayerDetail()
        if (!_uiState.value.favorite.isRestored) {
            restoreFavorite()
        }
    }

    fun retryBusy() {
        if (playerDetailJob?.isActive == true) return
        val busy = _uiState.value.busyRetry ?: return
        if (busy.operationId != OPERATION_ID || !busy.canRetry()) return
        _uiState.value = _uiState.value.copy(contentState = PlayerDetailContentState.Loading, busyRetry = null)
        loadPlayerDetail()
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    private fun loadPlayerDetail() {
        if (playerDetailJob?.isActive == true) return
        playerDetailJob = viewModelScope.launch {
            when (val result = playerRepository.getPlayerDetail(playerId)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        contentState = PlayerDetailContentState.Content(result.data),
                    )
                }
                AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        contentState = PlayerDetailContentState.Error,
                    )
                }
                is AppResult.Busy -> _uiState.value = _uiState.value.copy(
                    contentState = PlayerDetailContentState.Error,
                    busyRetry = busyRetryStateFactory.create(OPERATION_ID, result.retryDelay),
                )
            }
        }
    }

    fun toggleFavorite() {
        val player = (_uiState.value.contentState as? PlayerDetailContentState.Content)?.player ?: return
        val favorite = _uiState.value.favorite
        if (!favorite.isRestored || favorite.isMutationInProgress) return

        val intent = if (favorite.isFavorite) {
            PlayerFavoriteMutationIntent.Remove
        } else {
            PlayerFavoriteMutationIntent.Add
        }
        executeFavoriteMutation(FavoriteMutation(intent, player.toFavoritePlayer()))
    }

    fun retryFavoriteMutation() {
        if (_uiState.value.favorite.isMutationInProgress) return
        val mutation = failedFavoriteMutation ?: return

        executeFavoriteMutation(mutation)
    }

    fun retryFavoriteRestore() {
        if (!_uiState.value.favorite.hasRestoreFailure || isFavoriteRestoreInProgress) return

        restoreFavorite()
    }

    fun dismissFavoriteError() {
        failedFavoriteMutation = null
        updateFavorite { it.copy(failedIntent = null) }
    }

    private fun restoreFavorite() {
        if (isFavoriteRestoreInProgress) return

        isFavoriteRestoreInProgress = true
        viewModelScope.launch {
            favoriteRepository.getFavoritePlayers()
                .onSuccess { restoredFavorites ->
                    updateFavorite { favoriteState ->
                        favoriteState.copy(
                            isFavorite = restoredFavorites.any { favorite -> favorite.id == playerId },
                            isRestored = true,
                            hasRestoreFailure = false,
                        )
                    }
                }
                .onFailure {
                    updateFavorite { favoriteState ->
                        favoriteState.copy(
                            isRestored = false,
                            hasRestoreFailure = true,
                        )
                    }
                }
                .onBusy {
                    updateFavorite { favoriteState ->
                        favoriteState.copy(isRestored = false, hasRestoreFailure = true)
                    }
                }
            isFavoriteRestoreInProgress = false
        }
    }

    private fun executeFavoriteMutation(mutation: FavoriteMutation) {
        failedFavoriteMutation = null
        updateFavorite {
            it.copy(
                isFavorite = mutation.intent == PlayerFavoriteMutationIntent.Add,
                isMutationInProgress = true,
                failedIntent = null,
            )
        }
        viewModelScope.launch {
            val result = when (mutation.intent) {
                PlayerFavoriteMutationIntent.Add -> favoriteRepository.addFavoritePlayer(mutation.favorite)
                PlayerFavoriteMutationIntent.Remove -> favoriteRepository.removeFavoritePlayer(mutation.favorite.id)
            }
            result.onSuccess {
                updateFavorite { it.copy(isMutationInProgress = false) }
            }.onFailure {
                failedFavoriteMutation = mutation
                updateFavorite {
                    it.copy(
                        isFavorite = mutation.intent == PlayerFavoriteMutationIntent.Remove,
                        isMutationInProgress = false,
                        failedIntent = mutation.intent,
                    )
                }
            }.onBusy {
                failedFavoriteMutation = mutation
                updateFavorite {
                    it.copy(
                        isFavorite = mutation.intent == PlayerFavoriteMutationIntent.Remove,
                        isMutationInProgress = false,
                        failedIntent = mutation.intent,
                    )
                }
            }
        }
    }

    private fun updateFavorite(transform: (PlayerFavoriteUiState) -> PlayerFavoriteUiState) {
        _uiState.update { state ->
            state.copy(favorite = transform(state.favorite))
        }
    }

    private fun isBusyCooldown(): Boolean = _uiState.value.busyRetry
        ?.takeIf { it.operationId == OPERATION_ID }
        ?.canRetry() == false

    private fun PlayerDetail.toFavoritePlayer() = FavoritePlayer(
        id = id,
        handle = profile.handle,
        realName = profile.realName,
        countryCode = profile.countryCode,
        countryName = profile.countryName,
        imageUrl = profile.imageUrl,
    )

    private companion object { const val OPERATION_ID = "player-detail" }

    private data class FavoriteMutation(
        val intent: PlayerFavoriteMutationIntent,
        val favorite: FavoritePlayer,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted playerId: String): PlayerDetailViewModel
    }
}

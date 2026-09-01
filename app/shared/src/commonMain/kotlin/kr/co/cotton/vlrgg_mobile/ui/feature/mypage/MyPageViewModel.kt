package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MyPageViewModel(
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyPageUiState())

    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    private var teamObservationJob: Job? = null
    private var playerObservationJob: Job? = null
    private var removalJob: Job? = null
    private var teamGeneration = 0L
    private var playerGeneration = 0L
    private var hasTeamSnapshot = false
    private var hasPlayerSnapshot = false
    private var failedRemovalRequest: FavoriteRemovalRequest? = null

    init {
        startTeamObservation(showLoading = false)
        startPlayerObservation(showLoading = false)
    }

    fun retry() {
        if (!_uiState.value.isFullError) return

        hasTeamSnapshot = false
        hasPlayerSnapshot = false
        startTeamObservation(showLoading = true)
        startPlayerObservation(showLoading = true)
    }

    fun retryFavoriteTeams() {
        if (_uiState.value.favoriteTeams !is FavoriteSectionState.Error) return
        startTeamObservation(showLoading = true)
    }

    fun retryFavoritePlayers() {
        if (_uiState.value.favoritePlayers !is FavoriteSectionState.Error) return
        startPlayerObservation(showLoading = true)
    }

    fun removeFavoriteTeam(teamId: String) {
        val content = _uiState.value.favoriteTeams as? FavoriteSectionState.Content ?: return
        val index = content.favorites.indexOfFirst { it.id == teamId }
        if (index < 0) return

        executeRemoval(
            FavoriteRemovalRequest.Team(
                favorite = content.favorites[index],
                index = index,
                previousFavorites = content.favorites.toList(),
            ),
        )
    }

    fun removeFavoritePlayer(playerId: String) {
        val content = _uiState.value.favoritePlayers as? FavoriteSectionState.Content ?: return
        val index = content.favorites.indexOfFirst { it.id == playerId }
        if (index < 0) return

        executeRemoval(
            FavoriteRemovalRequest.Player(
                favorite = content.favorites[index],
                index = index,
                previousFavorites = content.favorites.toList(),
            ),
        )
    }

    fun retryFavoriteRemoval() {
        val request = failedRemovalRequest ?: return
        executeRemoval(request)
    }

    private fun startTeamObservation(showLoading: Boolean) {
        teamObservationJob?.cancel()
        val generation = ++teamGeneration
        if (showLoading) {
            _uiState.update {
                it.copy(
                    favoriteTeams = FavoriteSectionState.Loading,
                    isFullError = false,
                )
            }
        }
        teamObservationJob = observeFavorites(
            generation = generation,
            currentGeneration = { teamGeneration },
            flow = favoriteRepository.observeFavoriteTeams(),
            onResult = ::applyTeamResult,
        )
    }

    private fun startPlayerObservation(showLoading: Boolean) {
        playerObservationJob?.cancel()
        val generation = ++playerGeneration
        if (showLoading) {
            _uiState.update {
                it.copy(
                    favoritePlayers = FavoriteSectionState.Loading,
                    isFullError = false,
                )
            }
        }
        playerObservationJob = observeFavorites(
            generation = generation,
            currentGeneration = { playerGeneration },
            flow = favoriteRepository.observeFavoritePlayers(),
            onResult = ::applyPlayerResult,
        )
    }

    private fun <T> observeFavorites(
        generation: Long,
        currentGeneration: () -> Long,
        flow: Flow<AppResult<List<T>>>,
        onResult: (AppResult<List<T>>) -> Unit,
    ): Job = viewModelScope.launch {
        try {
            flow.collect { result ->
                if (generation == currentGeneration()) onResult(result)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            if (generation == currentGeneration()) onResult(AppResult.Failure)
        }
    }

    private fun applyTeamResult(result: AppResult<List<FavoriteTeam>>) {
        when (result) {
            is AppResult.Success -> {
                hasTeamSnapshot = true
                clearFailedTeamRemovalIfMissing(result.data)
                val removing = _uiState.value.removingFavorite as? FavoriteRemovalTarget.Team
                val visibleFavorites = result.data.filterNot { it.id == removing?.id }
                _uiState.update { state ->
                    state.copy(favoriteTeams = visibleFavorites.toSectionState())
                }
            }

            AppResult.Failure -> _uiState.update { state ->
                state.copy(favoriteTeams = FavoriteSectionState.Error)
            }
        }
        updateFullError()
    }

    private fun applyPlayerResult(result: AppResult<List<FavoritePlayer>>) {
        when (result) {
            is AppResult.Success -> {
                hasPlayerSnapshot = true
                clearFailedPlayerRemovalIfMissing(result.data)
                val removing = _uiState.value.removingFavorite as? FavoriteRemovalTarget.Player
                val visibleFavorites = result.data.filterNot { it.id == removing?.id }
                _uiState.update { state ->
                    state.copy(favoritePlayers = visibleFavorites.toSectionState())
                }
            }

            AppResult.Failure -> _uiState.update { state ->
                state.copy(favoritePlayers = FavoriteSectionState.Error)
            }
        }
        updateFullError()
    }

    private fun updateFullError() {
        _uiState.update { state ->
            state.copy(
                isFullError = !hasTeamSnapshot &&
                    !hasPlayerSnapshot &&
                    state.favoriteTeams is FavoriteSectionState.Error &&
                    state.favoritePlayers is FavoriteSectionState.Error,
            )
        }
    }

    private fun executeRemoval(request: FavoriteRemovalRequest) {
        if (removalJob?.isActive == true) return

        failedRemovalRequest = null
        optimisticallyRemove(request)
        _uiState.update {
            it.copy(
                removingFavorite = request.target,
                failedRemoval = null,
            )
        }
        removalJob = viewModelScope.launch {
            val result = try {
                when (request) {
                    is FavoriteRemovalRequest.Team -> favoriteRepository.removeFavoriteTeam(request.favorite.id)
                    is FavoriteRemovalRequest.Player -> favoriteRepository.removeFavoritePlayer(request.favorite.id)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                AppResult.Failure
            }

            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(removingFavorite = null)
                }

                AppResult.Failure -> {
                    restoreRemovedFavorite(request)
                    failedRemovalRequest = request
                    _uiState.update {
                        it.copy(
                            removingFavorite = null,
                            failedRemoval = request.target,
                        )
                    }
                }
            }
        }
    }

    private fun optimisticallyRemove(request: FavoriteRemovalRequest) {
        _uiState.update { state ->
            when (request) {
                is FavoriteRemovalRequest.Team -> state.copy(
                    favoriteTeams = state.favoriteTeams.withoutId(request.favorite.id) { it.id },
                )

                is FavoriteRemovalRequest.Player -> state.copy(
                    favoritePlayers = state.favoritePlayers.withoutId(request.favorite.id) { it.id },
                )
            }
        }
    }

    private fun restoreRemovedFavorite(request: FavoriteRemovalRequest) {
        _uiState.update { state ->
            when (request) {
                is FavoriteRemovalRequest.Team -> state.copy(
                    favoriteTeams = state.favoriteTeams.restoreAt(
                        favorite = request.favorite,
                        index = request.index,
                        previousFavorites = request.previousFavorites,
                        id = { it.id },
                    ),
                )

                is FavoriteRemovalRequest.Player -> state.copy(
                    favoritePlayers = state.favoritePlayers.restoreAt(
                        favorite = request.favorite,
                        index = request.index,
                        previousFavorites = request.previousFavorites,
                        id = { it.id },
                    ),
                )
            }
        }
    }

    private fun clearFailedTeamRemovalIfMissing(favorites: List<FavoriteTeam>) {
        val failed = _uiState.value.failedRemoval as? FavoriteRemovalTarget.Team ?: return
        if (favorites.any { it.id == failed.id }) return

        failedRemovalRequest = null
        _uiState.update { it.copy(failedRemoval = null) }
    }

    private fun clearFailedPlayerRemovalIfMissing(favorites: List<FavoritePlayer>) {
        val failed = _uiState.value.failedRemoval as? FavoriteRemovalTarget.Player ?: return
        if (favorites.any { it.id == failed.id }) return

        failedRemovalRequest = null
        _uiState.update { it.copy(failedRemoval = null) }
    }

    private sealed interface FavoriteRemovalRequest {
        val target: FavoriteRemovalTarget

        data class Team(
            val favorite: FavoriteTeam,
            val index: Int,
            val previousFavorites: List<FavoriteTeam>,
        ) : FavoriteRemovalRequest {
            override val target = FavoriteRemovalTarget.Team(favorite.id)
        }

        data class Player(
            val favorite: FavoritePlayer,
            val index: Int,
            val previousFavorites: List<FavoritePlayer>,
        ) : FavoriteRemovalRequest {
            override val target = FavoriteRemovalTarget.Player(favorite.id)
        }
    }
}

private fun <T> List<T>.toSectionState(): FavoriteSectionState<T> =
    if (isEmpty()) FavoriteSectionState.Empty else FavoriteSectionState.Content(this)

private fun <T> FavoriteSectionState<T>.withoutId(
    removedId: String,
    id: (T) -> String,
): FavoriteSectionState<T> {
    val favorites = (this as? FavoriteSectionState.Content)?.favorites ?: return this
    return favorites.filterNot { id(it) == removedId }.toSectionState()
}

private fun <T> FavoriteSectionState<T>.restoreAt(
    favorite: T,
    index: Int,
    previousFavorites: List<T>,
    id: (T) -> String,
): FavoriteSectionState<T> {
    val favorites = when (this) {
        is FavoriteSectionState.Content -> favorites
        FavoriteSectionState.Empty -> emptyList()
        FavoriteSectionState.Error,
        FavoriteSectionState.Loading,
        -> previousFavorites
    }
    if (favorites.any { id(it) == id(favorite) }) return FavoriteSectionState.Content(favorites)

    val restored = favorites.toMutableList().apply {
        add(index.coerceIn(0, size), favorite)
    }
    return FavoriteSectionState.Content(restored)
}

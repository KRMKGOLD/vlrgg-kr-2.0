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
    private var latestTeamFavorites: List<FavoriteTeam>? = null
    private var latestPlayerFavorites: List<FavoritePlayer>? = null
    private var failedRemovalRequest: FavoriteRemovalRequest? = null

    init {
        startTeamObservation(showLoading = false)
        startPlayerObservation(showLoading = false)
    }

    fun retry() {
        if (!_uiState.value.isFullError) return

        teamObservationJob?.cancel()
        playerObservationJob?.cancel()
        val nextTeamGeneration = ++teamGeneration
        val nextPlayerGeneration = ++playerGeneration
        hasTeamSnapshot = false
        hasPlayerSnapshot = false
        _uiState.update {
            it.copy(
                favoriteTeams = FavoriteSectionState.Loading,
                favoritePlayers = FavoriteSectionState.Loading,
                isFullError = false,
            )
        }
        teamObservationJob = observeTeamFavorites(nextTeamGeneration)
        playerObservationJob = observePlayerFavorites(nextPlayerGeneration)
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
                id = content.favorites[index].id,
            ),
        )
    }

    fun removeFavoritePlayer(playerId: String) {
        val content = _uiState.value.favoritePlayers as? FavoriteSectionState.Content ?: return
        val index = content.favorites.indexOfFirst { it.id == playerId }
        if (index < 0) return

        executeRemoval(
            FavoriteRemovalRequest.Player(
                id = content.favorites[index].id,
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
        teamObservationJob = observeTeamFavorites(generation)
    }

    private fun observeTeamFavorites(generation: Long): Job = observeFavorites(
        generation = generation,
        currentGeneration = { teamGeneration },
        flow = favoriteRepository.observeFavoriteTeams(),
        onResult = ::applyTeamResult,
    )

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
        playerObservationJob = observePlayerFavorites(generation)
    }

    private fun observePlayerFavorites(generation: Long): Job = observeFavorites(
        generation = generation,
        currentGeneration = { playerGeneration },
        flow = favoriteRepository.observeFavoritePlayers(),
        onResult = ::applyPlayerResult,
    )

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
                latestTeamFavorites = result.data.toList()
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
                latestPlayerFavorites = result.data.toList()
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
                    is FavoriteRemovalRequest.Team -> favoriteRepository.removeFavoriteTeam(request.id)
                    is FavoriteRemovalRequest.Player -> favoriteRepository.removeFavoritePlayer(request.id)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                AppResult.Failure
            }

            when (result) {
                is AppResult.Success -> completeSuccessfulRemoval(request)

                AppResult.Failure -> {
                    val shouldRetry = restoreLatestFavorites(request)
                    failedRemovalRequest = request.takeIf { shouldRetry }
                    _uiState.update {
                        it.copy(
                            removingFavorite = null,
                            failedRemoval = request.target.takeIf { shouldRetry },
                        )
                    }
                }
            }
        }
    }

    private fun completeSuccessfulRemoval(request: FavoriteRemovalRequest) {
        when (request) {
            is FavoriteRemovalRequest.Team -> {
                teamObservationJob?.cancel()
                val nextGeneration = ++teamGeneration
                _uiState.update { it.copy(removingFavorite = null) }
                teamObservationJob = observeTeamFavorites(nextGeneration)
            }

            is FavoriteRemovalRequest.Player -> {
                playerObservationJob?.cancel()
                val nextGeneration = ++playerGeneration
                _uiState.update { it.copy(removingFavorite = null) }
                playerObservationJob = observePlayerFavorites(nextGeneration)
            }
        }
    }

    private fun optimisticallyRemove(request: FavoriteRemovalRequest) {
        _uiState.update { state ->
            when (request) {
                is FavoriteRemovalRequest.Team -> state.copy(
                    favoriteTeams = state.favoriteTeams.withoutId(request.id) { it.id },
                )

                is FavoriteRemovalRequest.Player -> state.copy(
                    favoritePlayers = state.favoritePlayers.withoutId(request.id) { it.id },
                )
            }
        }
    }

    private fun restoreLatestFavorites(request: FavoriteRemovalRequest): Boolean = when (request) {
        is FavoriteRemovalRequest.Team -> {
            val favorites = latestTeamFavorites ?: return false
            _uiState.update { it.copy(favoriteTeams = favorites.toSectionState()) }
            favorites.any { it.id == request.id }
        }

        is FavoriteRemovalRequest.Player -> {
            val favorites = latestPlayerFavorites ?: return false
            _uiState.update { it.copy(favoritePlayers = favorites.toSectionState()) }
            favorites.any { it.id == request.id }
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
            val id: String,
        ) : FavoriteRemovalRequest {
            override val target = FavoriteRemovalTarget.Team(id)
        }

        data class Player(
            val id: String,
        ) : FavoriteRemovalRequest {
            override val target = FavoriteRemovalTarget.Player(id)
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

package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

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
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@AssistedInject
class TeamDetailViewModel(
    private val teamRepository: TeamRepository,
    private val favoriteRepository: FavoriteRepository,
    @Assisted private val teamId: String,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(TeamDetailUiState())
    val uiState: StateFlow<TeamDetailUiState> = _uiState.asStateFlow()
    private var failedFavoriteMutation: FavoriteMutation? = null
    private var isFavoriteRestoreInProgress = false
    private var teamDetailJob: Job? = null

    init {
        loadTeamDetail()
        restoreFavorite()
    }

    fun retry() {
        if (teamDetailJob?.isActive == true) return
        if (isBusyCooldown()) return
        if (_uiState.value.contentState != TeamDetailContentState.Error) return

        _uiState.value = _uiState.value.copy(
            contentState = TeamDetailContentState.Loading,
            busyRetry = null,
        )
        loadTeamDetail()
        if (!_uiState.value.favorite.isRestored) {
            restoreFavorite()
        }
    }

    fun retryBusy() {
        if (teamDetailJob?.isActive == true) return
        val busy = _uiState.value.busyRetry ?: return
        if (busy.operationId != OPERATION_ID || !busy.canRetry()) return
        _uiState.value = _uiState.value.copy(contentState = TeamDetailContentState.Loading, busyRetry = null)
        loadTeamDetail()
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    fun toggleFavorite() {
        val favorite = _uiState.value.favorite
        if (!favorite.isRestored || favorite.isMutationInProgress) return

        val team = (_uiState.value.contentState as? TeamDetailContentState.Content)?.team ?: return
        val intent = if (favorite.isFavorite) {
            TeamFavoriteMutationIntent.Remove
        } else {
            TeamFavoriteMutationIntent.Add
        }
        beginFavoriteMutation(
            FavoriteMutation(
                intent = intent,
                favorite = team.toFavoriteTeam(),
            ),
        )
    }

    fun retryFavoriteMutation() {
        if (_uiState.value.favorite.isMutationInProgress) return
        val mutation = failedFavoriteMutation ?: return
        beginFavoriteMutation(mutation)
    }

    fun retryFavoriteRestore() {
        if (!_uiState.value.favorite.hasRestoreFailure || isFavoriteRestoreInProgress) return

        restoreFavorite()
    }

    fun dismissFavoriteError() {
        failedFavoriteMutation = null
        _uiState.value = _uiState.value.copy(
            favorite = _uiState.value.favorite.copy(failedIntent = null),
        )
    }

    private fun loadTeamDetail() {
        if (teamDetailJob?.isActive == true) return
        teamDetailJob = viewModelScope.launch {
            when (val result = teamRepository.getTeamDetail(teamId)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        contentState = TeamDetailContentState.Content(result.data),
                    )
                }
                AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        contentState = TeamDetailContentState.Error,
                    )
                }
                is AppResult.Busy -> _uiState.value = _uiState.value.copy(
                    contentState = TeamDetailContentState.Error,
                    busyRetry = busyRetryStateFactory.create(OPERATION_ID, result.retryDelay),
                )
            }
        }
    }

    private fun restoreFavorite() {
        if (isFavoriteRestoreInProgress) return

        isFavoriteRestoreInProgress = true
        viewModelScope.launch {
            favoriteRepository.getFavoriteTeams()
                .onSuccess { restoredFavorites ->
                    updateFavorite { favoriteState ->
                        favoriteState.copy(
                            isFavorite = restoredFavorites.any { favorite -> favorite.id == teamId },
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

    private fun beginFavoriteMutation(mutation: FavoriteMutation) {
        failedFavoriteMutation = null
        updateFavorite {
            it.copy(
                isFavorite = mutation.intent == TeamFavoriteMutationIntent.Add,
                isMutationInProgress = true,
                failedIntent = null,
            )
        }

        viewModelScope.launch {
            val result = when (mutation.intent) {
                TeamFavoriteMutationIntent.Add -> favoriteRepository.addFavoriteTeam(mutation.favorite)
                TeamFavoriteMutationIntent.Remove -> favoriteRepository.removeFavoriteTeam(mutation.favorite.id)
            }
            result
                .onSuccess {
                    updateFavorite { it.copy(isMutationInProgress = false) }
                }
                .onFailure {
                    failedFavoriteMutation = mutation
                    updateFavorite {
                        it.copy(
                            isFavorite = mutation.intent == TeamFavoriteMutationIntent.Remove,
                            isMutationInProgress = false,
                            failedIntent = mutation.intent,
                        )
                    }
                }
                .onBusy {
                    failedFavoriteMutation = mutation
                    updateFavorite {
                        it.copy(
                            isFavorite = mutation.intent == TeamFavoriteMutationIntent.Remove,
                            isMutationInProgress = false,
                            failedIntent = mutation.intent,
                        )
                    }
                }
        }
    }

    private fun updateFavorite(transform: (TeamFavoriteUiState) -> TeamFavoriteUiState) {
        _uiState.update { state ->
            state.copy(favorite = transform(state.favorite))
        }
    }

    private fun isBusyCooldown(): Boolean = _uiState.value.busyRetry
        ?.takeIf { it.operationId == OPERATION_ID }
        ?.canRetry() == false

    private data class FavoriteMutation(
        val intent: TeamFavoriteMutationIntent,
        val favorite: FavoriteTeam,
    )

    private fun TeamDetail.toFavoriteTeam() = FavoriteTeam(
        id = id,
        name = name,
        tag = tag,
        country = country,
        imageUrl = logoUrl,
    )

    private companion object { const val OPERATION_ID = "team-detail" }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted teamId: String,
        ): TeamDetailViewModel
    }
}

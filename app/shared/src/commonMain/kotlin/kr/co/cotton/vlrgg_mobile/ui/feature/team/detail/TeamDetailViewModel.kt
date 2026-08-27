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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.onFailure
import kr.co.cotton.vlrgg_mobile.domain.onSuccess
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository

@AssistedInject
class TeamDetailViewModel(
    private val teamRepository: TeamRepository,
    @Assisted private val teamId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TeamDetailUiState())
    val uiState: StateFlow<TeamDetailUiState> = _uiState.asStateFlow()

    init {
        loadTeamDetail()
    }

    fun retry() {
        if (_uiState.value.contentState != TeamDetailContentState.Error) return

        _uiState.value = TeamDetailUiState()
        loadTeamDetail()
    }

    private fun loadTeamDetail() = viewModelScope.launch {
        teamRepository.getTeamDetail(teamId)
            .onSuccess { team ->
                _uiState.value = TeamDetailUiState(
                    contentState = TeamDetailContentState.Content(team),
                )
            }
            .onFailure {
                _uiState.value = TeamDetailUiState(
                    contentState = TeamDetailContentState.Error,
                )
            }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted teamId: String,
        ): TeamDetailViewModel
    }
}

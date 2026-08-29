package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

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
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository

@AssistedInject
class MatchDetailViewModel(
    private val matchRepository: MatchRepository,
    @Assisted private val matchId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchDetailUiState>(MatchDetailUiState.Loading)
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()

    init {
        loadMatchDetail()
    }

    fun retry() {
        if (_uiState.value != MatchDetailUiState.Error) return

        _uiState.value = MatchDetailUiState.Loading
        loadMatchDetail()
    }

    private fun loadMatchDetail() = viewModelScope.launch {
        matchRepository.getMatchDetail(matchId)
            .onSuccess { match ->
                _uiState.value = MatchDetailUiState.Content(match)
            }
            .onFailure {
                _uiState.value = MatchDetailUiState.Error
            }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted matchId: String,
        ): MatchDetailViewModel
    }
}

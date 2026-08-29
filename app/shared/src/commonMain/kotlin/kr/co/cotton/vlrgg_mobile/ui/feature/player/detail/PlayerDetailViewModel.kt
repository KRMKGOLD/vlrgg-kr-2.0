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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.onFailure
import kr.co.cotton.vlrgg_mobile.domain.onSuccess
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository

@AssistedInject
class PlayerDetailViewModel(
    private val playerRepository: PlayerRepository,
    @Assisted private val playerId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerDetailUiState())
    val uiState: StateFlow<PlayerDetailUiState> = _uiState.asStateFlow()

    init { loadPlayerDetail() }

    fun retry() {
        if (_uiState.value.contentState != PlayerDetailContentState.Error) return
        _uiState.value = PlayerDetailUiState()
        loadPlayerDetail()
    }

    private fun loadPlayerDetail() = viewModelScope.launch {
        playerRepository.getPlayerDetail(playerId)
            .onSuccess { player -> _uiState.value = PlayerDetailUiState(PlayerDetailContentState.Content(player)) }
            .onFailure { _uiState.value = PlayerDetailUiState(PlayerDetailContentState.Error) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted playerId: String): PlayerDetailViewModel
    }
}

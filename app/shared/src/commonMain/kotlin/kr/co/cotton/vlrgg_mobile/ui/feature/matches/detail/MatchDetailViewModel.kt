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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@AssistedInject
class MatchDetailViewModel(
    private val matchRepository: MatchRepository,
    @Assisted private val matchId: String,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchDetailUiState())
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null

    init {
        loadMatchDetail()
    }

    fun retry() {
        if (requestJob?.isActive == true) return
        if (isBusyCooldown()) return
        if (_uiState.value.contentState != MatchDetailContentState.Error) return
        _uiState.value = _uiState.value.copy(contentState = MatchDetailContentState.Loading, busyRetry = null)
        loadMatchDetail()
    }

    fun retryBusy() {
        if (requestJob?.isActive == true) return
        val busy = _uiState.value.busyRetry ?: return
        if (busy.operationId != OPERATION_ID || !busy.canRetry()) return
        _uiState.value = _uiState.value.copy(contentState = MatchDetailContentState.Loading, busyRetry = null)
        loadMatchDetail()
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    private fun loadMatchDetail() {
        if (requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            when (val result = matchRepository.getMatchDetail(matchId)) {
                is AppResult.Success -> {
                    _uiState.value = MatchDetailUiState(
                        contentState = MatchDetailContentState.Content(result.data),
                    )
                }
                AppResult.Failure -> {
                    _uiState.value = MatchDetailUiState(
                        contentState = MatchDetailContentState.Error,
                    )
                }
                is AppResult.Busy -> _uiState.value = _uiState.value.copy(
                    contentState = MatchDetailContentState.Error,
                    busyRetry = busyRetryStateFactory.create(OPERATION_ID, result.retryDelay),
                )
            }
        }
    }

    private fun isBusyCooldown(): Boolean = _uiState.value.busyRetry
        ?.takeIf { it.operationId == OPERATION_ID }
        ?.canRetry() == false

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted matchId: String,
        ): MatchDetailViewModel
    }

    private companion object { const val OPERATION_ID = "match-detail" }
}

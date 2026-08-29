package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

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
import kr.co.cotton.vlrgg_mobile.domain.repository.SeriesRepository

@AssistedInject
class SeriesDetailViewModel(
    private val seriesRepository: SeriesRepository,
    @Assisted private val seriesId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        if (_uiState.value.contentState != SeriesDetailContentState.Error) return
        _uiState.value = SeriesDetailUiState()
        load()
    }

    private fun load() = viewModelScope.launch {
        seriesRepository.getSeriesDetail(seriesId)
            .onSuccess { series ->
                _uiState.value = SeriesDetailUiState(
                    contentState = SeriesDetailContentState.Content(series),
                )
            }
            .onFailure {
                _uiState.value = SeriesDetailUiState(
                    contentState = SeriesDetailContentState.Error,
                )
            }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted seriesId: String): SeriesDetailViewModel
    }
}

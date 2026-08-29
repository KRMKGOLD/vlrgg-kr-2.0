package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail

sealed interface SeriesDetailContentState {
    data object Loading : SeriesDetailContentState

    data class Content(
        val series: SeriesDetail,
    ) : SeriesDetailContentState

    data object Error : SeriesDetailContentState
}

data class SeriesDetailUiState(
    val contentState: SeriesDetailContentState = SeriesDetailContentState.Loading,
)

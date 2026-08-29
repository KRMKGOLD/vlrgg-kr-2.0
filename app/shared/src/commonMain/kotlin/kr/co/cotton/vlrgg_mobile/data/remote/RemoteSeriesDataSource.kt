package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.series.SeriesDetailResponseDto

internal interface RemoteSeriesDataSource {

    suspend fun getSeriesDetail(seriesId: String): SeriesDetailResponseDto
}

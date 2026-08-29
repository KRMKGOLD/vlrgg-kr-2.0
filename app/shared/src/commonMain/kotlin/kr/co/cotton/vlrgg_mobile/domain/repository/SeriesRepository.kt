package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail

interface SeriesRepository {

    suspend fun getSeriesDetail(seriesId: String): AppResult<SeriesDetail>
}

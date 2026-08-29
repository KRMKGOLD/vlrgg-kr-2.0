package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSeriesDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.SeriesRepository

@Inject
internal class SeriesRepositoryImpl(
    private val remoteSeriesDataSource: RemoteSeriesDataSource,
) : SeriesRepository {

    override suspend fun getSeriesDetail(seriesId: String): AppResult<SeriesDetail> = wrapAsAppResult {
        remoteSeriesDataSource.getSeriesDetail(seriesId).toDomain()
    }
}

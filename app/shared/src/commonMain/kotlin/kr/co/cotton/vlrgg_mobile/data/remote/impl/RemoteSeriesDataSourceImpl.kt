package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSeriesDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.series.SeriesDetailResponseDto

@Inject
internal class RemoteSeriesDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteSeriesDataSource {

    override suspend fun getSeriesDetail(seriesId: String): SeriesDetailResponseDto =
        httpClient.get("$SERIES_PATH/$seriesId").body()

    private companion object {
        const val SERIES_PATH = "/api/v1/series"
    }
}

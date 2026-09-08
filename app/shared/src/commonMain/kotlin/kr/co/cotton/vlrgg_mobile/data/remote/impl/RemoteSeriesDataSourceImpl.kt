package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSeriesDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.series.SeriesDetailResponseDto
import kr.co.cotton.vlrgg_mobile.network.getPublicJson

@Inject
internal class RemoteSeriesDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteSeriesDataSource {

    override suspend fun getSeriesDetail(seriesId: String): SeriesDetailResponseDto =
        httpClient.getPublicJson("$SERIES_PATH/$seriesId")

    private companion object {
        const val SERIES_PATH = "/api/v1/series"
    }
}

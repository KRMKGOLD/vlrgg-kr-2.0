package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSearchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResponseDto
import kr.co.cotton.vlrgg_mobile.network.getPublicJson

@Inject
internal class RemoteSearchDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteSearchDataSource {
    override suspend fun getSearch(query: String): SearchResponseDto = httpClient.getPublicJson(SEARCH_PATH) {
        parameter("q", query)
    }

    private companion object {
        const val SEARCH_PATH = "/api/v1/search"
    }
}

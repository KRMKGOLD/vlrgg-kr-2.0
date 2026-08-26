package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSearchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResponseDto

@Inject
internal class RemoteSearchDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteSearchDataSource {
    override suspend fun getSearch(query: String): SearchResponseDto = httpClient.get(SEARCH_PATH) {
        parameter("q", query)
    }.body()

    private companion object {
        const val SEARCH_PATH = "/api/v1/search"
    }
}

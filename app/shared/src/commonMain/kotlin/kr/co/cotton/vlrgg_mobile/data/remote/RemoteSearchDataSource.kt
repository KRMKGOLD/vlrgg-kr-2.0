package kr.co.cotton.vlrgg_mobile.data.remote

import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResponseDto

internal interface RemoteSearchDataSource {
    suspend fun getSearch(query: String): SearchResponseDto
}

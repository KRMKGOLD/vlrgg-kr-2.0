package kr.co.cotton.vlrgg_mobile.domain.repository

import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults

interface SearchRepository {
    suspend fun getSearch(query: String): AppResult<SearchResults>
}

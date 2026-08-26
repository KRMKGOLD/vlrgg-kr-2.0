package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSearchDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository

@Inject
internal class SearchRepositoryImpl(
    private val remoteSearchDataSource: RemoteSearchDataSource,
) : SearchRepository {
    override suspend fun getSearch(query: String): AppResult<SearchResults> = wrapAsAppResult {
        remoteSearchDataSource.getSearch(query).toDomain()
    }
}

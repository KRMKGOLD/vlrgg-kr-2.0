package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.search.EventSearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.PlayerSearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResourceDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SeriesSearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.TeamSearchResultDto
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult

internal fun SearchResponseDto.toDomain(): SearchResults = SearchResults(
    query = query,
    items = results.map(SearchResultDto::toDomain),
)

private fun SearchResultDto.toDomain(): SearchResult = when (this) {
    is SeriesSearchResultDto -> {
        require(reference.resource == SearchResourceDto.SERIES)
        SeriesSearchResult(reference.id, name, scope)
    }

    is EventSearchResultDto -> {
        require(reference.resource == SearchResourceDto.EVENT)
        EventSearchResult(reference.id, name, period)
    }

    is TeamSearchResultDto -> {
        require(reference.resource == SearchResourceDto.TEAM)
        TeamSearchResult(reference.id, name, tagOrRegion)
    }

    is PlayerSearchResultDto -> {
        require(reference.resource == SearchResourceDto.PLAYER)
        PlayerSearchResult(reference.id, name, identity)
    }
}

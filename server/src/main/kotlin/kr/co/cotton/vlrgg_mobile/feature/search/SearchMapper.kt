package kr.co.cotton.vlrgg_mobile.feature.search

/** Converts parsed source data into the versioned, app-facing response variants. */
internal class SearchMapper {
    fun map(source: SearchSourceModel): List<SearchResultResponse> = source.results.map { result ->
        val reference = SearchReferenceResponse(
            resource = result.type.toResourceType(),
            id = result.id,
        )

        when (result.type) {
            SearchSourceResultType.SERIES -> SeriesSearchResultResponse(reference, result.name, result.description)
            SearchSourceResultType.EVENT -> EventSearchResultResponse(reference, result.name, result.description)
            SearchSourceResultType.TEAM -> TeamSearchResultResponse(reference, result.name, result.description)
            SearchSourceResultType.PLAYER -> PlayerSearchResultResponse(reference, result.name, result.description)
        }
    }

    private fun SearchSourceResultType.toResourceType(): SearchResourceType = when (this) {
        SearchSourceResultType.SERIES -> SearchResourceType.SERIES
        SearchSourceResultType.EVENT -> SearchResourceType.EVENT
        SearchSourceResultType.TEAM -> SearchResourceType.TEAM
        SearchSourceResultType.PLAYER -> SearchResourceType.PLAYER
    }
}

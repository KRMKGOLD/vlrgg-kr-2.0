package kr.co.cotton.vlrgg_mobile.domain.model.search

data class SearchResults(
    val query: String,
    val items: List<SearchResult>,
)

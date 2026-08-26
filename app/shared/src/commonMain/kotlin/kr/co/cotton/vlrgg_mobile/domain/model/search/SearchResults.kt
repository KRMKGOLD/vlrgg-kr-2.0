package kr.co.cotton.vlrgg_mobile.domain.model.search

data class SearchResults(
    val query: String,
    val items: List<SearchResult>,
)

sealed interface SearchResult {
    val id: String
    val name: String
    val metadata: String?
}

data class SeriesSearchResult(
    override val id: String,
    override val name: String,
    val scope: String?,
) : SearchResult {
    override val metadata: String? = scope
}

data class EventSearchResult(
    override val id: String,
    override val name: String,
    val period: String?,
) : SearchResult {
    override val metadata: String? = period
}

data class TeamSearchResult(
    override val id: String,
    override val name: String,
    val tagOrRegion: String?,
) : SearchResult {
    override val metadata: String? = tagOrRegion
}

data class PlayerSearchResult(
    override val id: String,
    override val name: String,
    val identity: String?,
) : SearchResult {
    override val metadata: String? = identity
}

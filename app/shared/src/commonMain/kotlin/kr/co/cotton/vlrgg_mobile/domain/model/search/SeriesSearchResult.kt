package kr.co.cotton.vlrgg_mobile.domain.model.search

data class SeriesSearchResult(
    override val id: String,
    override val name: String,
    val scope: String?,
    override val imageUrl: String? = null,
) : SearchResult {
    override val metadata: String? = scope
}

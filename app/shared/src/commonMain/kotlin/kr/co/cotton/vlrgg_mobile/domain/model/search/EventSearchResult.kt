package kr.co.cotton.vlrgg_mobile.domain.model.search

data class EventSearchResult(
    override val id: String,
    override val name: String,
    val period: String?,
) : SearchResult {
    override val metadata: String? = period
}

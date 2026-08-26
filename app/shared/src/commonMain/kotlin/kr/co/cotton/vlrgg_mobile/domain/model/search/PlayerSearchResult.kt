package kr.co.cotton.vlrgg_mobile.domain.model.search

data class PlayerSearchResult(
    override val id: String,
    override val name: String,
    val identity: String?,
) : SearchResult {
    override val metadata: String? = identity
}

package kr.co.cotton.vlrgg_mobile.domain.model.search

data class TeamSearchResult(
    override val id: String,
    override val name: String,
    val tagOrRegion: String?,
    override val imageUrl: String? = null,
) : SearchResult {
    override val metadata: String? = tagOrRegion
}

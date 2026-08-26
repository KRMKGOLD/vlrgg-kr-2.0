package kr.co.cotton.vlrgg_mobile.domain.model.search

data class TeamSearchResult(
    override val id: String,
    override val name: String,
    val tagOrRegion: String?,
) : SearchResult {
    override val metadata: String? = tagOrRegion
}

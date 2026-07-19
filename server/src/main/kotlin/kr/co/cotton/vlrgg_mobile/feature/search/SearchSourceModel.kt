package kr.co.cotton.vlrgg_mobile.feature.search

/** VLR.GG search data after DOM parsing and before conversion to the public API contract. */
internal data class SearchSourceModel(
    val results: List<SearchSourceResult>,
)

internal data class SearchSourceResult(
    val type: SearchSourceResultType,
    val id: String,
    val name: String,
    val description: String?,
)

internal enum class SearchSourceResultType {
    SERIES,
    EVENT,
    TEAM,
    PLAYER,
}

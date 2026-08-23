package kr.co.cotton.vlrgg_mobile.domain.model.matches

data class MatchPage(
    val category: MatchListCategory,
    val page: Int,
    val groups: List<MatchDateGroup>,
)

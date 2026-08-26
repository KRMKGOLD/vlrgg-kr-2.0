package kr.co.cotton.vlrgg_mobile.domain.model.search

sealed interface SearchResult {
    val id: String
    val name: String
    val metadata: String?
}

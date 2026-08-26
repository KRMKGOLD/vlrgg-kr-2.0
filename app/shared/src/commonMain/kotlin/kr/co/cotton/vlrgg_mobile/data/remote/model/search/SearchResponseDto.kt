package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponseDto(
    val query: String,
    val results: List<SearchResultDto>,
)

package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchReferenceDto(
    val resource: SearchResourceDto,
    val id: String,
)

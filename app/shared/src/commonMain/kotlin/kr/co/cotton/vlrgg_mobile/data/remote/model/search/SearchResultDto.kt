package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.Serializable

@Serializable
internal sealed class SearchResultDto {
    abstract val reference: SearchReferenceDto
    abstract val name: String
}

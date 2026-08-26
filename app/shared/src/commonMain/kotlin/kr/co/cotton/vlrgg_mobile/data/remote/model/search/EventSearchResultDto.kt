package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("event")
internal data class EventSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val period: String? = null,
) : SearchResultDto()

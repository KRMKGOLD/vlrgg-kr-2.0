package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("player")
internal data class PlayerSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val identity: String? = null,
) : SearchResultDto()

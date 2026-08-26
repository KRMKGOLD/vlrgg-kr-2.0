package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("team")
internal data class TeamSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val tagOrRegion: String? = null,
) : SearchResultDto()

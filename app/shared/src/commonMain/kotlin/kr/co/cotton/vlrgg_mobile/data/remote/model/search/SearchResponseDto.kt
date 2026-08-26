package kr.co.cotton.vlrgg_mobile.data.remote.model.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponseDto(
    val query: String,
    val results: List<SearchResultDto>,
)

@Serializable
internal data class SearchReferenceDto(
    val resource: SearchResourceDto,
    val id: String,
)

@Serializable
internal enum class SearchResourceDto {
    @SerialName("series")
    SERIES,

    @SerialName("event")
    EVENT,

    @SerialName("team")
    TEAM,

    @SerialName("player")
    PLAYER,
}

@Serializable
internal sealed class SearchResultDto {
    abstract val reference: SearchReferenceDto
    abstract val name: String
}

@Serializable
@SerialName("series")
internal data class SeriesSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val scope: String? = null,
) : SearchResultDto()

@Serializable
@SerialName("event")
internal data class EventSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val period: String? = null,
) : SearchResultDto()

@Serializable
@SerialName("team")
internal data class TeamSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val tagOrRegion: String? = null,
) : SearchResultDto()

@Serializable
@SerialName("player")
internal data class PlayerSearchResultDto(
    override val reference: SearchReferenceDto,
    override val name: String,
    val identity: String? = null,
) : SearchResultDto()

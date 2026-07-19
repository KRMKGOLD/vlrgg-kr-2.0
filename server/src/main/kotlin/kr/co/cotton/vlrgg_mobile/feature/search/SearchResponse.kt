package kr.co.cotton.vlrgg_mobile.feature.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** App-facing response for one request-time VLR.GG search. */
@Serializable
internal data class SearchResponse(
    val query: String,
    val results: List<SearchResultResponse>,
)

/** A stable detail-navigation target, deliberately independent of upstream display slugs and URLs. */
@Serializable
internal data class SearchReferenceResponse(
    val resource: SearchResourceType,
    val id: String,
)

@Serializable
internal enum class SearchResourceType {
    @SerialName("series")
    SERIES,

    @SerialName("event")
    EVENT,

    @SerialName("team")
    TEAM,

    @SerialName("player")
    PLAYER,
}

/** The serialized `type` discriminator classifies each result without exposing source DOM details. */
@Serializable
internal sealed class SearchResultResponse {
    abstract val reference: SearchReferenceResponse
    abstract val name: String
}

@Serializable
@SerialName("series")
internal data class SeriesSearchResultResponse(
    override val reference: SearchReferenceResponse,
    override val name: String,
    val scope: String?,
) : SearchResultResponse()

@Serializable
@SerialName("event")
internal data class EventSearchResultResponse(
    override val reference: SearchReferenceResponse,
    override val name: String,
    val period: String?,
) : SearchResultResponse()

@Serializable
@SerialName("team")
internal data class TeamSearchResultResponse(
    override val reference: SearchReferenceResponse,
    override val name: String,
    val tagOrRegion: String?,
) : SearchResultResponse()

@Serializable
@SerialName("player")
internal data class PlayerSearchResultResponse(
    override val reference: SearchReferenceResponse,
    override val name: String,
    val identity: String?,
) : SearchResultResponse()

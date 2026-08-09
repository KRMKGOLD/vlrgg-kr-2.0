package kr.co.cotton.vlrgg_mobile.data.remote.model.news

import kotlinx.serialization.Serializable

@Serializable
internal enum class NewsLinkKindDto {
    TEAM,
    PLAYER,
    EVENT,
    MATCH,
    INTERNAL_UNSUPPORTED,
    EXTERNAL,
}

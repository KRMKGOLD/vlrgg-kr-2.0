package kr.co.cotton.vlrgg_mobile.data.remote.model.news

import kotlinx.serialization.Serializable

@Serializable
internal data class NewsSummaryDto(
    val reference: String,
    val title: String,
    val author: String,
    val publishedAt: String,
)

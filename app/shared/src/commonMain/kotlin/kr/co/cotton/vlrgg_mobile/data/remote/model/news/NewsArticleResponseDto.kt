package kr.co.cotton.vlrgg_mobile.data.remote.model.news

import kotlinx.serialization.Serializable

@Serializable
internal data class NewsArticleResponseDto(
    val reference: String,
    val title: String,
    val author: String,
    val publishedAt: String,
    val blocks: List<NewsArticleBlockDto>,
)

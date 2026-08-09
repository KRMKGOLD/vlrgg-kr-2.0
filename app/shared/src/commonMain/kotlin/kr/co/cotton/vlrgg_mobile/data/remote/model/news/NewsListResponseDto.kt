package kr.co.cotton.vlrgg_mobile.data.remote.model.news

import kotlinx.serialization.Serializable

@Serializable
internal data class NewsListResponseDto(
    val page: Int,
    val nextPage: Int?,
    val items: List<NewsSummaryDto>,
)

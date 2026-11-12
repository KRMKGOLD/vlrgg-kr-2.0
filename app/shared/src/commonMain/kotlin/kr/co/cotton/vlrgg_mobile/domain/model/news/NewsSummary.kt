package kr.co.cotton.vlrgg_mobile.domain.model.news

import kotlinx.datetime.LocalDate

data class NewsSummary(
    val articleId: String,
    val slug: String,
    val title: String,
    val author: String?,
    val publishedAt: String,
    /** `publishedAt`의 달력 날짜. 서버가 주지 않거나 형식이 맞지 않으면 null이다. */
    val publishedDate: LocalDate? = null,
)

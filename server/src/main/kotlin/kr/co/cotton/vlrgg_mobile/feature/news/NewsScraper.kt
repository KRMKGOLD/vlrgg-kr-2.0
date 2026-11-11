package kr.co.cotton.vlrgg_mobile.feature.news

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

internal const val VLR_PRIMARY_ORIGIN = "https://www.vlr.gg"

internal class NewsScraper(
    private val transport: UpstreamHtmlTransport,
) {
    suspend fun fetchList(page: Int): String =
        fetch(path = if (page == 1) "/news" else "/news/?page=$page")

    suspend fun fetchArticle(reference: NewsReference): String =
        fetch(path = "/${reference.value}")

    private suspend fun fetch(path: String): String = transport.get(Url("$VLR_PRIMARY_ORIGIN$path"))
}

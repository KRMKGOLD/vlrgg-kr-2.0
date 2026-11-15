package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

internal interface MatchesScraper {
    suspend fun fetchList(category: MatchListCategory, page: Int): String

    suspend fun fetchDetail(matchId: String): String
}

internal class VlrMatchesScraper(
    private val transport: UpstreamHtmlTransport,
) : MatchesScraper {
    override suspend fun fetchList(category: MatchListCategory, page: Int): String =
        transport.get(buildListUrl(category, page))

    override suspend fun fetchDetail(matchId: String): String = transport.get(buildDetailUrl(matchId))

    private fun buildListUrl(category: MatchListCategory, page: Int): Url = URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = VLR_PRIMARY_HOST,
    ).apply {
        pathSegments = when (category) {
            MatchListCategory.UPCOMING -> listOf("matches")
            MatchListCategory.RESULTS -> listOf("matches", "results")
        }
        if (page > 1) {
            parameters.append("page", page.toString())
        }
    }.build()

    private fun buildDetailUrl(matchId: String): Url = URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = VLR_PRIMARY_HOST,
    ).apply {
        pathSegments = listOf(matchId)
    }.build()

    private companion object {
        const val VLR_PRIMARY_HOST = "www.vlr.gg"
    }
}

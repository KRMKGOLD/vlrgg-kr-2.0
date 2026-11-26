package kr.co.cotton.vlrgg_mobile.feature.news

import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference

/** Coordinates one fresh upstream request with parsing and public response mapping. */
internal class NewsService(
    private val scraper: NewsScraper,
    private val parser: NewsParser,
    private val mapper: NewsMapper,
) {
    suspend fun getList(page: Int): NewsListResponse {
        val html = scraper.fetchList(page)
        return mapper.toListResponse(
            page = page,
            source = parse { parser.parseList(html, page) },
        )
    }

    suspend fun getArticle(reference: NewsReference): NewsArticleResponse {
        val html = scraper.fetchArticle(reference)
        return mapper.toArticleResponse(
            source = parse { parser.parseArticle(html, reference) },
        )
    }

    // Only known News DOM failures become SourceParsingFailure; any other exception stays an internal error.
    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (exception: NewsParsingException) {
        throw SourceParsingFailure(exception)
    }
}

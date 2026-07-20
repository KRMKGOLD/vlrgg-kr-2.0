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
        val scraped = scraper.fetchList(page)
        return mapper.toListResponse(
            page = page,
            source = parse(scraped) { parser.parseList(it, page) },
        )
    }

    suspend fun getArticle(reference: NewsReference): NewsArticleResponse {
        val scraped = scraper.fetchArticle(reference)
        return mapper.toArticleResponse(
            source = parse(scraped) { parser.parseArticle(it, reference) },
        )
    }

    private fun <T> parse(scraped: NewsScrapedHtml, parse: (String) -> T): T = try {
        parse(scraped.html)
    } catch (exception: NewsParsingException) {
        throw SourceParsingFailure(upstreamUrl = scraped.upstreamUrl, cause = exception)
    }
}

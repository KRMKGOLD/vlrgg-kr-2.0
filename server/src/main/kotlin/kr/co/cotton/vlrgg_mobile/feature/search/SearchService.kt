package kr.co.cotton.vlrgg_mobile.feature.search

/** Coordinates a fresh scrape and maps only that request's source data into the public contract. */
internal class SearchService(
    private val scraper: SearchScraper,
    private val mapper: SearchMapper,
) {
    suspend fun search(query: String): SearchResponse = SearchResponse(
        query = query,
        results = mapper.map(scraper.scrape(query)),
    )
}

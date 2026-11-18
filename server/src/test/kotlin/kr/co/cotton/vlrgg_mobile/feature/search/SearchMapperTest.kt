package kr.co.cotton.vlrgg_mobile.feature.search

import kotlin.test.*

class SearchMapperTest {

    @Test
    fun `mapper creates typed responses with stable references and optional auxiliary fields`() {
        val response = SearchMapper().map(
            SearchSourceModel(
                results = listOf(
                    SearchSourceResult(SearchSourceResultType.SERIES, "101", "Champions Tour", "Global circuit", null),
                    SearchSourceResult(SearchSourceResultType.EVENT, "202", "Champions Seoul", "Aug 1 to Aug 24", null),
                    SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", "SEN · United States", null),
                    SearchSourceResult(SearchSourceResultType.PLAYER, "9", "TenZ", null),
                ),
            ),
        )

        assertEquals(
            listOf(
                SeriesSearchResultResponse(SearchReferenceResponse(SearchResourceType.SERIES, "101"), "Champions Tour", "Global circuit", null),
                EventSearchResultResponse(SearchReferenceResponse(SearchResourceType.EVENT, "202"), "Champions Seoul", "Aug 1 to Aug 24", null),
                TeamSearchResultResponse(SearchReferenceResponse(SearchResourceType.TEAM, "2"), "Sentinels", "SEN · United States", null),
                PlayerSearchResultResponse(SearchReferenceResponse(SearchResourceType.PLAYER, "9"), "TenZ", null, null),
            ),
            response,
        )
    }

    @Test
    fun `mapper carries the thumbnail imageUrl to every response type`() {
        val response = SearchMapper().map(
            SearchSourceModel(
                results = listOf(
                    SearchSourceResult(SearchSourceResultType.SERIES, "101", "Champions Tour", null, "https://owcdn.net/img/s.png"),
                    SearchSourceResult(SearchSourceResultType.EVENT, "202", "Champions Seoul", null, "https://owcdn.net/img/e.png"),
                    SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", null, "https://owcdn.net/img/t.png"),
                    SearchSourceResult(SearchSourceResultType.PLAYER, "9", "TenZ", null),
                ),
            ),
        )

        assertEquals(
            listOf(
                "https://owcdn.net/img/s.png",
                "https://owcdn.net/img/e.png",
                "https://owcdn.net/img/t.png",
                null,
            ),
            response.map { it.imageUrl },
        )
    }
}

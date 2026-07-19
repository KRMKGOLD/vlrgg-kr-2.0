package kr.co.cotton.vlrgg_mobile.feature.search

import kotlin.test.*

class SearchMapperTest {

    @Test
    fun `mapper creates typed responses with stable references and optional auxiliary fields`() {
        val response = SearchMapper().map(
            SearchSourceModel(
                results = listOf(
                    SearchSourceResult(SearchSourceResultType.SERIES, "101", "Champions Tour", "Global circuit"),
                    SearchSourceResult(SearchSourceResultType.EVENT, "202", "Champions Seoul", "Aug 1 to Aug 24"),
                    SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", "SEN · United States"),
                    SearchSourceResult(SearchSourceResultType.PLAYER, "9", "TenZ", null),
                ),
            ),
        )

        assertEquals(
            listOf(
                SeriesSearchResultResponse(SearchReferenceResponse(SearchResourceType.SERIES, "101"), "Champions Tour", "Global circuit"),
                EventSearchResultResponse(SearchReferenceResponse(SearchResourceType.EVENT, "202"), "Champions Seoul", "Aug 1 to Aug 24"),
                TeamSearchResultResponse(SearchReferenceResponse(SearchResourceType.TEAM, "2"), "Sentinels", "SEN · United States"),
                PlayerSearchResultResponse(SearchReferenceResponse(SearchResourceType.PLAYER, "9"), "TenZ", null),
            ),
            response,
        )
    }
}

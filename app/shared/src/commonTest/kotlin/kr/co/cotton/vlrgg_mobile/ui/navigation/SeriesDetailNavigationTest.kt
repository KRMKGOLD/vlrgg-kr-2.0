package kr.co.cotton.vlrgg_mobile.ui.navigation

import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesDetailNavigationTest {

    @Test
    fun seriesSearchResultBuildsTheExpectedOverlayDestination() {
        assertEquals(
            SeriesDetail("2"),
            destinationForSearchResult(SeriesSearchResult("2", "Champions Tour", null)),
        )
    }
}

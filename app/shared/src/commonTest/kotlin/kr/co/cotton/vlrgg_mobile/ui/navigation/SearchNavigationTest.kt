package kr.co.cotton.vlrgg_mobile.ui.navigation

import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchNavigationTest {
    @Test
    fun eachSupportedSearchTypeMapsOnlyToItsMatchingDetailKey() {
        assertEquals(SeriesDetail("1"), destinationForSearchResult(SeriesSearchResult("1", "Series", null)))
        assertEquals(EventDetail("2"), destinationForSearchResult(EventSearchResult("2", "Event", null)))
        assertEquals(TeamDetail("3"), destinationForSearchResult(TeamSearchResult("3", "Team", null)))
        assertEquals(PlayerDetail("4"), destinationForSearchResult(PlayerSearchResult("4", "Player", null)))
    }
}

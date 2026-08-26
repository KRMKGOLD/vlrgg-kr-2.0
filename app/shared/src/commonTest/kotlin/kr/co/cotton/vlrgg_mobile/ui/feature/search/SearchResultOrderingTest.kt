package kr.co.cotton.vlrgg_mobile.ui.feature.search

import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SearchResultOrderingTest {
    @Test
    fun supportedTypesHaveCanonicalDisplayOrder() {
        val ordered = orderedSearchResults(
            listOf(
                PlayerSearchResult("4", "Player", null),
                TeamSearchResult("3", "Team", null),
                EventSearchResult("2", "Event", null),
                SeriesSearchResult("1", "Series", null),
            ),
        )

        assertEquals(listOf("1", "2", "3", "4"), ordered.map { it.id })
    }

    @Test
    fun listKeysKeepSameNumericIdsFromDifferentTypesDistinct() {
        assertNotEquals(
            searchResultListKey(SeriesSearchResult("1", "Series", null), index = 0),
            searchResultListKey(EventSearchResult("1", "Event", null), index = 0),
        )
    }

    @Test
    fun listKeysKeepDuplicateResultsFromTheSameTypeDistinct() {
        val result = TeamSearchResult("1", "Team", null)

        assertNotEquals(
            searchResultListKey(result, index = 0),
            searchResultListKey(result, index = 1),
        )
    }
}

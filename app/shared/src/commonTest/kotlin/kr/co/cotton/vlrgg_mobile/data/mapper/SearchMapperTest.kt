package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.search.EventSearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.PlayerSearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchReferenceDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResourceDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SeriesSearchResultDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.TeamSearchResultDto
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchMapperTest {
    @Test
    fun responseMapsEachSupportedTypeAndPreservesOptionalMetadata() {
        val results = SearchResponseDto(
            query = "T1",
            results = listOf(
                SeriesSearchResultDto(reference(SearchResourceDto.SERIES, "1"), "T1 vs GEN", "Group Stage"),
                EventSearchResultDto(reference(SearchResourceDto.EVENT, "2"), "VCT Pacific", null),
                TeamSearchResultDto(reference(SearchResourceDto.TEAM, "3"), "T1", "Pacific"),
                PlayerSearchResultDto(reference(SearchResourceDto.PLAYER, "4"), "T1 Sayaplayer", "Ha Jung-woo"),
            ),
        ).toDomain()

        assertEquals("T1", results.query)
        assertEquals(
            listOf(
                SeriesSearchResult("1", "T1 vs GEN", "Group Stage"),
                EventSearchResult("2", "VCT Pacific", null),
                TeamSearchResult("3", "T1", "Pacific"),
                PlayerSearchResult("4", "T1 Sayaplayer", "Ha Jung-woo"),
            ),
            results.items,
        )
    }

    @Test
    fun resourceMismatchFailsRatherThanCreatingWrongDetailTarget() {
        val response = SearchResponseDto(
            query = "T1",
            results = listOf(
                TeamSearchResultDto(reference(SearchResourceDto.PLAYER, "4"), "T1", null),
            ),
        )

        assertFailsWith<IllegalArgumentException> { response.toDomain() }
    }

    private fun reference(resource: SearchResourceDto, id: String) = SearchReferenceDto(resource, id)
}

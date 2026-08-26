package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSearchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchReferenceDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResourceDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.SearchResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.search.TeamSearchResultDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SearchRepositoryImplTest {
    @Test
    fun getSearchReturnsMappedSuccess() = runTest {
        val repository = SearchRepositoryImpl(FakeRemoteSearchDataSource { response() })

        val result = repository.getSearch("T1")

        assertEquals("T1", (result as AppResult.Success).data.query)
        assertEquals("3", result.data.items.single().id)
    }

    @Test
    fun genericFailureBecomesAppFailure() = runTest {
        val repository = SearchRepositoryImpl(FakeRemoteSearchDataSource { error("network") })

        assertSame(AppResult.Failure, repository.getSearch("T1"))
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = SearchRepositoryImpl(FakeRemoteSearchDataSource { throw cancellation })

        assertSame(cancellation, assertFailsWith<CancellationException> { repository.getSearch("T1") })
    }

    private fun response() = SearchResponseDto(
        query = "T1",
        results = listOf(
            TeamSearchResultDto(
                reference = SearchReferenceDto(SearchResourceDto.TEAM, "3"),
                name = "T1",
                tagOrRegion = null,
            ),
        ),
    )
}

private class FakeRemoteSearchDataSource(
    private val handler: suspend () -> SearchResponseDto,
) : RemoteSearchDataSource {
    override suspend fun getSearch(query: String): SearchResponseDto = handler()
}

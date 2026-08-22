package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteMatchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchListCategoryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MatchRepositoryImplTest {

    @Test
    fun getUpcomingMatchesReturnsMappedSuccess() = runTest {
        val remoteDataSource = FakeRemoteMatchDataSource(
            upcomingHandler = { page -> pageDto(MatchListCategoryDto.UPCOMING, page) },
        )
        val repository = MatchRepositoryImpl(remoteDataSource)

        val result = repository.getUpcomingMatches(page = 2)

        assertEquals(2, remoteDataSource.requestedUpcomingPage)
        assertEquals(
            AppResult.Success(
                MatchPage(
                    category = MatchListCategory.UPCOMING,
                    page = 2,
                    groups = emptyList(),
                ),
            ),
            result,
        )
    }

    @Test
    fun getResultsReturnsMappedSuccess() = runTest {
        val remoteDataSource = FakeRemoteMatchDataSource(
            resultsHandler = { page -> pageDto(MatchListCategoryDto.RESULTS, page) },
        )
        val repository = MatchRepositoryImpl(remoteDataSource)

        val result = repository.getResults(page = 3)

        assertEquals(3, remoteDataSource.requestedResultsPage)
        assertEquals(
            AppResult.Success(
                MatchPage(
                    category = MatchListCategory.RESULTS,
                    page = 3,
                    groups = emptyList(),
                ),
            ),
            result,
        )
    }

    @Test
    fun feedFailuresAreConvertedIndependently() = runTest {
        val repository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(
                upcomingHandler = { throw IllegalStateException("upcoming failure") },
                resultsHandler = { page -> pageDto(MatchListCategoryDto.RESULTS, page) },
            ),
        )

        assertSame(AppResult.Failure, repository.getUpcomingMatches(page = 1))
        assertEquals(
            AppResult.Success(
                MatchPage(
                    category = MatchListCategory.RESULTS,
                    page = 1,
                    groups = emptyList(),
                ),
            ),
            repository.getResults(page = 1),
        )
    }

    @Test
    fun resultsFailureIsConvertedToAppFailure() = runTest {
        val repository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(
                resultsHandler = { throw IllegalStateException("results failure") },
            ),
        )

        assertSame(AppResult.Failure, repository.getResults(page = 1))
    }

    @Test
    fun upcomingCancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(
                upcomingHandler = { throw cancellation },
            ),
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getUpcomingMatches(page = 1)
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun resultsCancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(
                resultsHandler = { throw cancellation },
            ),
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getResults(page = 1)
        }

        assertSame(cancellation, thrown)
    }

    private fun pageDto(
        category: MatchListCategoryDto,
        page: Int,
    ) = MatchesPageResponseDto(
        category = category,
        page = page,
        groups = emptyList(),
    )
}

private class FakeRemoteMatchDataSource(
    private val upcomingHandler: suspend (Int) -> MatchesPageResponseDto = {
        error("Unexpected getUpcomingMatches call")
    },
    private val resultsHandler: suspend (Int) -> MatchesPageResponseDto = {
        error("Unexpected getResults call")
    },
) : RemoteMatchDataSource {
    var requestedUpcomingPage: Int? = null
        private set
    var requestedResultsPage: Int? = null
        private set

    override suspend fun getUpcomingMatches(page: Int): MatchesPageResponseDto {
        requestedUpcomingPage = page
        return upcomingHandler(page)
    }

    override suspend fun getResults(page: Int): MatchesPageResponseDto {
        requestedResultsPage = page
        return resultsHandler(page)
    }
}

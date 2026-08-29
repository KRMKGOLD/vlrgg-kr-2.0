package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteMatchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchListCategoryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchEventDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchTeamDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertIs

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

    @Test
    fun detailFailureIsConvertedAndCancellationIsRethrown() = runTest {
        val failureRepository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(detailHandler = { throw IllegalStateException("detail failure") }),
        )
        assertSame(AppResult.Failure, failureRepository.getMatchDetail("7000"))

        val cancellation = CancellationException("cancelled")
        val cancellingRepository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(detailHandler = { throw cancellation }),
        )
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { cancellingRepository.getMatchDetail("7000") },
        )
    }

    @Test
    fun detailReturnsMappedSuccessWithoutReplacingMissingScores() = runTest {
        val repository = MatchRepositoryImpl(
            FakeRemoteMatchDataSource(
                detailHandler = {
                    MatchDetailResponseDto(
                        id = it,
                        status = MatchStatusDto.LIVE,
                        timeLabel = "LIVE",
                        homeTeam = MatchTeamDto("Alpha"),
                        awayTeam = MatchTeamDto("Beta"),
                        homeScore = null,
                        awayScore = 0,
                        event = MatchEventDto("Champions"),
                        maps = emptyList(),
                        headToHead = emptyList(),
                        pastMatches = emptyList(),
                    )
                },
            ),
        )

        val result = repository.getMatchDetail("7000")

        val match = assertIs<AppResult.Success<MatchDetail>>(result).data
        assertEquals("7000", match.id)
        assertEquals(null, match.homeScore)
        assertEquals(0, match.awayScore)
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
    private val detailHandler: suspend (String) -> MatchDetailResponseDto = {
        error("Unexpected getMatchDetail call")
    },
) : RemoteMatchDataSource {
    var requestedUpcomingPage: Int? = null
        private set
    var requestedResultsPage: Int? = null
        private set
    var requestedMatchId: String? = null
        private set

    override suspend fun getUpcomingMatches(page: Int): MatchesPageResponseDto {
        requestedUpcomingPage = page
        return upcomingHandler(page)
    }

    override suspend fun getResults(page: Int): MatchesPageResponseDto {
        requestedResultsPage = page
        return resultsHandler(page)
    }

    override suspend fun getMatchDetail(matchId: String): MatchDetailResponseDto {
        requestedMatchId = matchId
        return detailHandler(matchId)
    }
}

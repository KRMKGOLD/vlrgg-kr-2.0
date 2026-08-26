package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteTeamDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamRosterMemberDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TeamRepositoryImplTest {

    @Test
    fun getTeamDetailReturnsMappedSuccess() = runTest {
        val remoteDataSource = FakeRemoteTeamDataSource { teamDetailResponse() }
        val repository = TeamRepositoryImpl(remoteDataSource)

        val result = repository.getTeamDetail("8185")

        assertEquals("8185", remoteDataSource.requestedTeamId)
        assertEquals(AppResult.Success(teamDetail()), result)
    }

    @Test
    fun nonCancellationFailureIsConvertedToAppFailure() = runTest {
        val repository = TeamRepositoryImpl(
            FakeRemoteTeamDataSource { throw IllegalStateException("team failure") },
        )

        assertSame(AppResult.Failure, repository.getTeamDetail("8185"))
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = TeamRepositoryImpl(
            FakeRemoteTeamDataSource { throw cancellation },
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getTeamDetail("8185")
        }

        assertSame(cancellation, thrown)
    }

    private fun teamDetailResponse() = TeamDetailResponseDto(
        id = "8185",
        name = "KIWOOM DRX",
        tag = null,
        country = null,
        upcomingMatches = listOf(
            TeamMatchDto("698887", null, null, "KIWOOM DRX", "Sentinels", null, null),
        ),
        recentMatches = emptyList(),
        players = listOf(TeamRosterMemberDto("4462", "MaKo", null, emptyList())),
        staff = emptyList(),
        news = listOf(TeamNewsDto("700755/kiwoom-drx", "DRX news", null)),
    )

    private fun teamDetail() = TeamDetail(
        id = "8185",
        name = "KIWOOM DRX",
        tag = null,
        country = null,
        upcomingMatches = listOf(
            TeamMatch("698887", null, null, "KIWOOM DRX", "Sentinels", null, null),
        ),
        recentMatches = emptyList(),
        players = listOf(TeamRosterMember("4462", "MaKo", null, emptyList())),
        staff = emptyList(),
        news = listOf(TeamNews("700755", "kiwoom-drx", "DRX news", null)),
    )
}

private class FakeRemoteTeamDataSource(
    private val handler: suspend (String) -> TeamDetailResponseDto,
) : RemoteTeamDataSource {
    var requestedTeamId: String? = null
        private set

    override suspend fun getTeamDetail(teamId: String): TeamDetailResponseDto {
        requestedTeamId = teamId
        return handler(teamId)
    }
}

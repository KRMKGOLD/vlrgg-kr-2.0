package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemotePlayerDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.player.PlayerProfileDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PlayerRepositoryImplTest {

    @Test
    fun getPlayerDetailReturnsMappedSuccess() = runTest {
        val repository = PlayerRepositoryImpl(
            FakeRemotePlayerDataSource { detailResponse("488") },
        )

        val result = repository.getPlayerDetail("488")

        assertEquals("488", (result as AppResult.Success).data.id)
        assertEquals("Rb", result.data.profile.handle)
        assertEquals(emptyList(), result.data.agentStats)
        assertEquals(emptyList(), result.data.recentMatches)
    }

    @Test
    fun failureIsConvertedToAppFailure() = runTest {
        val repository = PlayerRepositoryImpl(
            FakeRemotePlayerDataSource { throw IllegalStateException("player failure") },
        )

        assertSame(AppResult.Failure, repository.getPlayerDetail("488"))
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = PlayerRepositoryImpl(
            FakeRemotePlayerDataSource { throw cancellation },
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getPlayerDetail("488")
        }

        assertSame(cancellation, thrown)
    }

    private fun detailResponse(playerId: String) = PlayerDetailResponseDto(
        id = playerId,
        profile = PlayerProfileDto("Rb", null, emptyList(), null, null),
        currentTeam = null,
        agentStats = emptyList(),
        recentMatches = emptyList(),
    )
}

private class FakeRemotePlayerDataSource(
    private val handler: suspend () -> PlayerDetailResponseDto,
) : RemotePlayerDataSource {
    override suspend fun getPlayerDetail(playerId: String): PlayerDetailResponseDto = handler()
}

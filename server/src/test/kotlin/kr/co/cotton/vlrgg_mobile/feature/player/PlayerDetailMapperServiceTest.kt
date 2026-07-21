package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.*

class PlayerDetailMapperServiceTest {
    @Test
    fun `mapper preserves stable String IDs optional metrics and recent-match cap`() {
        val match = PlayerRecentMatchSource(
            id = "1",
            eventName = "Cup",
            eventStage = null,
            teamA = PlayerMatchTeamSource("One", null),
            teamB = PlayerMatchTeamSource("Two", null),
            teamAScore = null,
            teamBScore = null,
            outcome = PlayerMatchOutcomeSource.UNKNOWN,
            playedOn = null,
        )
        val response = PlayerDetailMapper().map(
            PlayerId.fromPath("9999999999"),
            PlayerDetailSource(
                profile = PlayerProfileSource("solo", null, emptyList(), null, null),
                currentTeam = null,
                agentStats = listOf(
                    AgentStatSource("gekko", 1, 0, null, null, 174.0, 0.53, null, null, 0.50, 0.33, null, 9, 17, 6, 0, 0),
                ),
                recentMatches = List(6) { match.copy(id = (it + 1).toString()) },
            ),
        )

        assertEquals("9999999999", response.id)
        assertNull(response.currentTeam)
        assertNull(response.agentStats.single().rating)
        assertNull(response.agentStats.single().roundsPlayed)
        assertEquals(5, response.recentMatches.size)
        assertEquals("5", response.recentMatches.last().id)
    }

    @Test
    fun `service fetches current all-time data each time without stale fallback and preserves cancellation`() {
        runBlocking {
        val transport = RecordingTransport()
        val service = PlayerDetailService(PlayerDetailScraper(transport), PlayerDetailParser(), PlayerDetailMapper())

        assertEquals("488", service.get(PlayerId.fromPath("488")).id)
        transport.failAfterFirst = true
        assertFailsWith<UpstreamNetworkFailure> { service.get(PlayerId.fromPath("488")) }
        assertEquals(2, transport.requestedUrls.size)
        assertTrue(transport.requestedUrls.all { it.toString() == "https://www.vlr.gg/player/488/?timespan=all" })

        val cancelled = PlayerDetailService(
            PlayerDetailScraper(object : UpstreamHtmlTransport {
                override suspend fun get(url: Url): String = throw CancellationException("cancel")
            }),
            PlayerDetailParser(),
            PlayerDetailMapper(),
        )
            assertFailsWith<CancellationException> { cancelled.get(PlayerId.fromPath("488")) }
        }
    }

    private class RecordingTransport : UpstreamHtmlTransport {
        val requestedUrls = mutableListOf<Url>()
        var failAfterFirst = false

        override suspend fun get(url: Url): String {
            requestedUrls += url
            if (failAfterFirst) throw UpstreamNetworkFailure(url)
            return checkNotNull(javaClass.classLoader.getResource("fixtures/players/player-detail-empty.html")).readText()
        }
    }
}

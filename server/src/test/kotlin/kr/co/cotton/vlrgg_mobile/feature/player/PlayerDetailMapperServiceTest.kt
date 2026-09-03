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
        assertNull(response.profile.imageUrl)
        assertNull(response.agentStats.single().rating)
        assertNull(response.agentStats.single().roundsPlayed)
        assertEquals(5, response.recentMatches.size)
        assertEquals("5", response.recentMatches.last().id)
    }

    @Test
    fun `mapper preserves nullable current team image URL`() {
        val response = PlayerDetailMapper().map(
            PlayerId.fromPath("488"),
            PlayerDetailSource(
                profile = PlayerProfileSource("solo", null, emptyList(), null, null),
                currentTeam = PlayerTeamSource(
                    id = "11060",
                    name = "Nongshim RedForce",
                    imageUrl = "https://owcdn.net/img/6399bb707aacb.png",
                ),
                agentStats = emptyList(),
                recentMatches = emptyList(),
            ),
        )

        assertEquals("https://owcdn.net/img/6399bb707aacb.png", response.currentTeam?.imageUrl)
    }

    @Test
    fun `mapper preserves nullable player profile image URL`() {
        listOf("https://owcdn.net/img/69d5f87b7c32d.png", null).forEach { imageUrl ->
            val response = PlayerDetailMapper().map(
                PlayerId.fromPath("488"),
                PlayerDetailSource(
                    profile = PlayerProfileSource("solo", null, emptyList(), null, null, imageUrl),
                    currentTeam = null,
                    agentStats = emptyList(),
                    recentMatches = emptyList(),
                ),
            )

            assertEquals(imageUrl, response.profile.imageUrl)
        }
    }

    @Test
    fun `mapper preserves current team when its image URL is null`() {
        val response = PlayerDetailMapper().map(
            PlayerId.fromPath("488"),
            PlayerDetailSource(
                profile = PlayerProfileSource("solo", null, emptyList(), null, null),
                currentTeam = PlayerTeamSource(
                    id = "11060",
                    name = "Nongshim RedForce",
                    imageUrl = null,
                ),
                agentStats = emptyList(),
                recentMatches = emptyList(),
            ),
        )

        assertEquals(PlayerTeamResponse("11060", "Nongshim RedForce", null), response.currentTeam)
    }

    @Test
    fun `service fetches current all-time data each time without stale fallback and preserves cancellation`() {
        runBlocking {
        val transport = RecordingTransport()
        val service = PlayerDetailService(PlayerDetailScraper(transport), PlayerDetailParser(), PlayerDetailMapper())

        val firstResponse = service.get(PlayerId.fromPath("488"))
        assertEquals("488", firstResponse.id)
        assertNull(firstResponse.profile.imageUrl)
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

    @Test
    fun `service maps the parsed player profile image URL`() = runBlocking {
        val service = PlayerDetailService(
            PlayerDetailScraper(RecordingTransport(fixture("player-detail.html"))),
            PlayerDetailParser(),
            PlayerDetailMapper(),
        )

        assertEquals("https://owcdn.net/img/69d5f87b7c32d.png", service.get(PlayerId.fromPath("488")).profile.imageUrl)
    }

    private class RecordingTransport(
        private val html: String = fixture("player-detail-empty.html"),
    ) : UpstreamHtmlTransport {
        val requestedUrls = mutableListOf<Url>()
        var failAfterFirst = false

        override suspend fun get(url: Url): String {
            requestedUrls += url
            if (failAfterFirst) throw UpstreamNetworkFailure(url)
            return html
        }
    }

    private companion object {
        fun fixture(name: String): String = checkNotNull(
            PlayerDetailMapperServiceTest::class.java.classLoader.getResource("fixtures/players/$name"),
        ).readText()
    }
}

package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class PlayerDetailParserTest {
    private val parser = PlayerDetailParser()
    private val upstreamUrl = Url("https://www.vlr.gg/player/488/?timespan=all")

    @Test
    fun `parser separates profile current team all-time Agent Stats and five Recent Matches`() {
        val source = parser.parse(content("player-detail.html"))

        assertEquals(PlayerProfileSource("Rb", "Goo Sang-min", listOf("ClokingRb"), "kr", "SOUTH KOREA"), source.profile)
        assertEquals(PlayerTeamSource("11060", "Nongshim RedForce"), source.currentTeam)
        assertEquals(2, source.agentStats.size)
        assertEquals("jett", source.agentStats.first().agentName)
        assertEquals(134, source.agentStats.first().mapsPlayed)
        assertEquals(1.07, source.agentStats.first().rating)
        assertNull(source.agentStats.last().rating)
        assertNull(source.agentStats.last().kastPercent)
        assertEquals(0, source.agentStats.last().pickRatePercent)
        assertEquals(5, source.recentMatches.size)
        assertEquals("708427", source.recentMatches.first().id)
        assertEquals(PlayerMatchOutcomeSource.WIN, source.recentMatches.first().outcome)
        assertEquals("2026-07-12", source.recentMatches.first().playedOn)
    }

    @Test
    fun `parser accepts no stats current team or recent matches without synthesizing optionals`() {
        val source = parser.parse(content("player-detail-empty.html"))

        assertEquals(PlayerProfileSource("solo", null, emptyList(), null, null), source.profile)
        assertNull(source.currentTeam)
        assertTrue(source.agentStats.isEmpty())
        assertTrue(source.recentMatches.isEmpty())
    }

    @Test
    fun `parser rejects missing required structure and observed stat drift safely`() {
        assertFailsWith<SourceParsingFailure> { parser.parse(content("player-detail-required-structure-missing.html")) }
        assertFailsWith<SourceParsingFailure> {
            parser.parse(content("player-detail.html").copy(html = fixture("player-detail.html").replace("(134) 25%</td><td>2680", "(134) 25%</td>")))
        }
    }

    @Test
    fun `parser scopes sections to exclude contamination outside player detail content`() {
        val source = parser.parse(content("player-detail-polluted.html"))

        assertEquals(PlayerTeamSource("101", "Clean Team"), source.currentTeam)
        assertEquals(listOf("sage"), source.agentStats.map(AgentStatSource::agentName))
        assertEquals(listOf("700001"), source.recentMatches.map(PlayerRecentMatchSource::id))
    }

    private fun content(name: String) = PlayerDetailUpstreamContent(fixture(name), upstreamUrl)
    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/players/$name"),
    ).readText()
}

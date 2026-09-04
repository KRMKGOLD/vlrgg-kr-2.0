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

        assertEquals(
            PlayerProfileSource(
                "Rb",
                "Goo Sang-min",
                listOf("ClokingRb"),
                "kr",
                "SOUTH KOREA",
                "https://owcdn.net/img/69d5f87b7c32d.png",
            ),
            source.profile,
        )
        assertEquals(
            PlayerTeamSource("11060", "Nongshim RedForce", "https://owcdn.net/img/6399bb707aacb.png"),
            source.currentTeam,
        )
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
    fun `parser deduplicates a direct Recent Match card while preserving section scope`() {
        val source = parser.parse(content("player-detail-direct-match.html"))

        assertEquals(listOf("700002"), source.recentMatches.map(PlayerRecentMatchSource::id))
    }

    @Test
    fun `parser deduplicates recent matches by stable ID before applying the cap`() {
        val source = parser.parse(content("player-detail-duplicate-matches.html"))

        assertEquals(listOf("700001", "700002", "700003", "700004", "700005"), source.recentMatches.map(PlayerRecentMatchSource::id))
    }

    @Test
    fun `parser keeps missing and malformed optional pick rates null without dropping valid Agent Stats`() {
        val source = parser.parse(content("player-detail-optional-pick-rate.html"))

        assertEquals(listOf("sage", "brimstone"), source.agentStats.take(2).map(AgentStatSource::agentName))
        assertEquals(listOf(null, null), source.agentStats.take(2).map(AgentStatSource::pickRatePercent))
    }

    @Test
    fun `parser accepts KAST percent boundaries and maps malformed optional values to null`() {
        val source = parser.parse(content("player-detail-optional-pick-rate.html"))

        assertEquals(
            listOf(0, 100, null, null, null, null, null, null, null),
            source.agentStats.map(AgentStatSource::kastPercent),
        )
    }

    @Test
    fun `parser keeps zero optional doubles and maps negative doubles to null`() {
        val stat = parser.parse(content("player-detail-optional-doubles.html")).agentStats.single()

        assertNull(stat.rating)
        assertEquals(0.0, stat.averageCombatScore)
        assertNull(stat.killDeathRatio)
        assertNull(stat.averageDamagePerRound)
        assertNull(stat.killsPerRound)
        assertNull(stat.assistsPerRound)
        assertNull(stat.firstKillDeathRatio)
    }

    @Test
    fun `parser rejects missing required structure and observed stat drift safely`() {
        assertFailsWith<SourceParsingFailure> { parser.parse(content("player-detail-required-structure-missing.html")) }
        assertFailsWith<SourceParsingFailure> { parser.parse(content("player-detail-stats-without-tbody.html")) }
        assertFailsWith<SourceParsingFailure> {
            parser.parse(content("player-detail.html").copy(html = fixture("player-detail.html").replace("(134) 25%</td><td>2680", "(134) 25%</td>")))
        }
    }

    @Test
    fun `parser scopes sections to exclude contamination outside player detail content`() {
        val source = parser.parse(content("player-detail-polluted.html"))

        assertEquals(PlayerTeamSource("101", "Clean Team"), source.currentTeam)
        assertNull(source.currentTeam?.imageUrl)
        assertEquals(listOf("sage"), source.agentStats.map(AgentStatSource::agentName))
        assertEquals(listOf("700001"), source.recentMatches.map(PlayerRecentMatchSource::id))
    }

    @Test
    fun `parser normalizes only supported HTTPS team image sources`() {
        val imageSources = mapOf(
            "//owcdn.net/img/team.png" to "https://owcdn.net/img/team.png",
            "/img/team.png" to "https://www.vlr.gg/img/team.png",
            "https://owcdn.net/img/team.png" to "https://owcdn.net/img/team.png",
            "HTTPS://owcdn.net/img/team.png" to "https://owcdn.net/img/team.png",
            "//" to null,
            "https://" to null,
            "https:///img/team.png" to null,
            "https://bad host/img/team.png" to null,
            "http://owcdn.net/img/team.png" to null,
            "" to null,
        )

        imageSources.forEach { (source, expected) ->
            val html = fixture("player-detail.html").replace(
                "//owcdn.net/img/6399bb707aacb.png",
                source,
            )
            assertEquals(expected, parser.parse(content("player-detail.html").copy(html = html)).currentTeam?.imageUrl, source)
        }
    }

    @Test
    fun `parser normalizes only supported HTTPS player header avatar sources`() {
        val imageSources = mapOf(
            "//owcdn.net/img/69d5f87b7c32d.png" to "https://owcdn.net/img/69d5f87b7c32d.png",
            "/img/69d5f87b7c32d.png" to "https://www.vlr.gg/img/69d5f87b7c32d.png",
            "https://owcdn.net/img/69d5f87b7c32d.png" to "https://owcdn.net/img/69d5f87b7c32d.png",
            "HTTPS://owcdn.net/img/69d5f87b7c32d.png" to "https://owcdn.net/img/69d5f87b7c32d.png",
            "//" to null,
            "https://" to null,
            "https:///img/69d5f87b7c32d.png" to null,
            "https://bad host/img/69d5f87b7c32d.png" to null,
            "" to null,
            "http://owcdn.net/img/69d5f87b7c32d.png" to null,
            "data:image/png;base64,abc" to null,
            "javascript:alert(1)" to null,
            "69d5f87b7c32d.png" to null,
        )

        imageSources.forEach { (source, expected) ->
            val html = fixture("player-detail.html").replace("//owcdn.net/img/69d5f87b7c32d.png", source)

            assertEquals(expected, parser.parse(content("player-detail.html").copy(html = html)).profile.imageUrl, source)
        }

        val withoutAvatar = fixture("player-detail.html").replace(
            "<div class=\"wf-avatar mod-player\"><img src=\"//owcdn.net/img/69d5f87b7c32d.png\" alt=\"Rb\"></div>",
            "",
        )
        assertNull(parser.parse(content("player-detail.html").copy(html = withoutAvatar)).profile.imageUrl)
    }

    @Test
    fun `parser scopes representative image to the player header avatar excluding outside team and Agent images`() {
        val withoutHeaderAvatar = fixture("player-detail.html").replace(
            "<div class=\"wf-avatar mod-player\"><img src=\"//owcdn.net/img/69d5f87b7c32d.png\" alt=\"Rb\"></div>",
            "",
        )
        val html = withoutHeaderAvatar.replace(
            "</body>",
            "<div class=\"wf-avatar mod-player\"><img src=\"//owcdn.net/img/players/outside.png\"></div></body>",
        )

        val source = parser.parse(content("player-detail.html").copy(html = html))

        assertNull(source.profile.imageUrl)
        assertEquals("jett", source.agentStats.first().agentName)
        assertEquals("https://owcdn.net/img/6399bb707aacb.png", source.currentTeam?.imageUrl)
    }

    private fun content(name: String) = PlayerDetailUpstreamContent(fixture(name), upstreamUrl)
    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/players/$name"),
    ).readText()
}

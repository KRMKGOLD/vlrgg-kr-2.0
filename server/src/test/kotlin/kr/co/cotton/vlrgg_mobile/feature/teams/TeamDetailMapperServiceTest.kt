package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.*

class TeamDetailMapperServiceTest {
    @Test
    fun `mapper preserves stable string IDs and omitted optional source values`() {
        val response = TeamDetailMapper().map(
            TeamId.fromPath("9999999999"),
            TeamDetailSource(
                profile = TeamProfileSource("Example", null, null),
                upcomingMatches = listOf(TeamMatchSource("123", null, null, "Example", "Opponent", null, null)),
                recentMatches = emptyList(),
                players = listOf(TeamRosterMemberSource("456", "player", null, emptyList())),
                staff = emptyList(),
                news = listOf(TeamNewsSource(NewsReference.fromPath("789", "article")!!, "Article", null)),
            ),
        )

        assertEquals("9999999999", response.id)
        assertEquals("123", response.upcomingMatches.single().id)
        assertEquals("456", response.players.single().id)
        assertEquals("789/article", response.news.single().reference)
        assertNull(response.tag)
        assertNull(response.upcomingMatches.single().eventName)
    }

    @Test
    fun `service fetches both pages for each request and retains no stale result`() = runBlocking {
        val transport = FixtureTransport()
        val service = service(transport)

        val first = service.get(TeamId.fromPath("8185"))
        val second = service.get(TeamId.fromPath("8185"))

        assertEquals(first, second)
        assertEquals(
            listOf("/team/8185/", "/team/news/8185/", "/team/8185/", "/team/news/8185/"),
            transport.requestedPaths,
        )
    }

    @Test
    fun `service preserves upstream failures from either source fetch`() = runBlocking {
        listOf("/team/8185/", "/team/news/8185/").forEach { failedPath ->
            val failure = assertFailsWith<UpstreamNetworkFailure> {
                service(FixtureTransport(failedPath = failedPath)).get(TeamId.fromPath("8185"))
            }
            assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
        }
    }

    @Test
    fun `service preserves cancellation from transport`() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            service(FixtureTransport(cancellationPath = "/team/news/8185/")).get(TeamId.fromPath("8185"))
        }
    }

    private fun service(transport: UpstreamHtmlTransport) = TeamDetailService(
        TeamDetailScraper(transport), TeamDetailParser(), TeamDetailMapper(),
    )

    private class FixtureTransport(
        private val failedPath: String? = null,
        private val cancellationPath: String? = null,
    ) : UpstreamHtmlTransport {
        val requestedPaths = mutableListOf<String>()

        override suspend fun get(url: Url): String {
            requestedPaths += url.encodedPath
            if (url.encodedPath == cancellationPath) throw CancellationException("cancel")
            if (url.encodedPath == failedPath) throw UpstreamNetworkFailure(url)
            return when (url.encodedPath) {
                "/team/8185/" -> fixture("active-team-overview.html")
                "/team/news/8185/" -> fixture("active-team-news.html")
                else -> error("Unexpected upstream path: ${url.encodedPath}")
            }
        }

        private fun fixture(name: String): String = checkNotNull(
            javaClass.classLoader.getResource("fixtures/teams/$name"),
        ).readText()
    }
}

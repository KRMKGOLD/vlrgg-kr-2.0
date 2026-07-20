package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
            setOf("/team/8185/", "/team/news/8185/"),
            transport.requestedPaths.toSet(),
        )
        assertEquals(4, transport.requestedPaths.size)
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

    @Test
    fun `scraper begins both source fetches before either response is released`() = runBlocking {
        val transport = ConcurrentFixtureTransport()
        val result = async { TeamDetailScraper(transport).scrape(TeamId.fromPath("8185")) }

        withTimeout(1_000) { transport.bothRequestsStarted.await() }
        assertEquals(setOf("/team/8185/", "/team/news/8185/"), transport.requestedPaths.toSet())

        transport.releaseResponses.complete(Unit)
        assertEquals("<overview>", result.await().overviewHtml)
        assertEquals("<news>", result.await().newsHtml)
        assertEquals(2, transport.requestedPaths.size)
    }

    @Test
    fun `scraper propagates source failure and cancellation without extra requests`() = runBlocking {
        val networkTransport = FailingConcurrentTransport(
            UpstreamNetworkFailure(Url("https://www.vlr.gg/team/news/8185/")),
        )
        assertFailsWith<UpstreamNetworkFailure> {
            TeamDetailScraper(networkTransport).scrape(TeamId.fromPath("8185"))
        }
        assertEquals(setOf("/team/8185/", "/team/news/8185/"), networkTransport.requestedPaths.toSet())
        assertEquals(2, networkTransport.requestedPaths.size)

        val cancellationTransport = FailingConcurrentTransport(CancellationException("cancel"))
        assertFailsWith<CancellationException> {
            TeamDetailScraper(cancellationTransport).scrape(TeamId.fromPath("8185"))
        }
        assertEquals(setOf("/team/8185/", "/team/news/8185/"), cancellationTransport.requestedPaths.toSet())
        assertEquals(2, cancellationTransport.requestedPaths.size)
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

    private class ConcurrentFixtureTransport : UpstreamHtmlTransport {
        val requestedPaths = mutableListOf<String>()
        val bothRequestsStarted = CompletableDeferred<Unit>()
        val releaseResponses = CompletableDeferred<Unit>()

        override suspend fun get(url: Url): String {
            synchronized(requestedPaths) {
                requestedPaths += url.encodedPath
                if (requestedPaths.size == 2) bothRequestsStarted.complete(Unit)
            }
            releaseResponses.await()
            return if (url.encodedPath == "/team/8185/") "<overview>" else "<news>"
        }
    }

    private class FailingConcurrentTransport(
        private val failure: Throwable,
    ) : UpstreamHtmlTransport {
        val requestedPaths = mutableListOf<String>()
        private val bothRequestsStarted = CompletableDeferred<Unit>()

        override suspend fun get(url: Url): String {
            synchronized(requestedPaths) {
                requestedPaths += url.encodedPath
                if (requestedPaths.size == 2) bothRequestsStarted.complete(Unit)
            }
            bothRequestsStarted.await()
            return when (url.encodedPath) {
                "/team/8185/" -> "<overview>"
                "/team/news/8185/" -> throw failure
                else -> error("Unexpected upstream path: ${url.encodedPath}")
            }
        }
    }
}

package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.news.NewsService
import kr.co.cotton.vlrgg_mobile.feature.news.createDefaultNewsService
import kr.co.cotton.vlrgg_mobile.feature.search.SearchMapper
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceModel
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceResult
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceResultType
import kr.co.cotton.vlrgg_mobile.feature.search.TeamSearchResultResponse
import kr.co.cotton.vlrgg_mobile.module
import kotlin.test.*

class TeamDetailRoutesTest {
    @Test
    fun `route returns the versioned team contract with string IDs`() = withTeamApplication(FixtureTransport()) {
        val response = client.get("/api/v1/teams/8185")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("8185", body["id"]?.jsonPrimitive?.content)
        assertEquals("https://owcdn.net/img/kiwoom-drx.png", body["logoUrl"]?.jsonPrimitive?.content)
        assertEquals("698887", body["upcomingMatches"]?.jsonArray?.single()?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals("4462", body["players"]?.jsonArray?.single()?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(
            "https://owcdn.net/img/players/mako.png",
            body["players"]?.jsonArray?.single()?.jsonObject?.get("imageUrl")?.jsonPrimitive?.content,
        )
        assertEquals(
            "https://www.vlr.gg/img/base/ph/sil.png",
            body["staff"]?.jsonArray?.single()?.jsonObject?.get("imageUrl")?.jsonPrimitive?.content,
        )
        assertEquals(
            "700755/kiwoom-drx-releases-rookie-hermes",
            body["news"]?.jsonArray?.first()?.jsonObject?.get("reference")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `route preserves a canonical TBD opponent from Team match source`() = withTeamApplication(
        FixtureTransport(
            overviewHtml = fixture("alternate-attax-ruby-tbd-overview.html"),
            newsHtml = fixture("sparse-team-news.html"),
        ),
    ) {
        val response = client.get("/api/v1/teams/11496")
        val match = Json.parseToJsonElement(response.bodyAsText()).jsonObject["upcomingMatches"]
            ?.jsonArray
            ?.single()
            ?.jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("747668", match?.get("id")?.jsonPrimitive?.content)
        assertEquals("ALTERNATE aTTaX Ruby", match?.get("teamName")?.jsonPrimitive?.content)
        assertEquals("TBD", match?.get("opponentName")?.jsonPrimitive?.content)
        assertTrue(Json.parseToJsonElement(response.bodyAsText()).jsonObject["news"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `route serializes missing optional Team images as null`() = withTeamApplication(
        FixtureTransport(
            overviewHtml = fixture("sparse-team-overview.html"),
            newsHtml = fixture("sparse-team-news.html"),
        ),
    ) {
        val body = Json.parseToJsonElement(client.get("/api/v1/teams/19296").bodyAsText()).jsonObject

        assertEquals(JsonNull, body["logoUrl"])
        assertTrue(body["players"]!!.jsonArray.isEmpty())
        assertTrue(body["staff"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `route accepts a Team Search reference ID directly as its path ID`() = withTeamApplication(FixtureTransport()) {
        val searchReferenceId = (SearchMapper().map(
            SearchSourceModel(listOf(SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", null))),
        ).single() as TeamSearchResultResponse).reference.id

        val response = client.get("/api/v1/teams/$searchReferenceId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(searchReferenceId, Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Team news reference forms the existing News endpoint contract`() = withTeamApplication(
        transport = FixtureTransport(),
        newsService = createDefaultNewsService(ArticleFixtureTransport()),
    ) {
        val team = Json.parseToJsonElement(client.get("/api/v1/teams/8185").bodyAsText()).jsonObject
        val reference = team["news"]!!.jsonArray.first().jsonObject["reference"]!!.jsonPrimitive.content

        val response = client.get("/api/v1/news/$reference")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(reference, Json.parseToJsonElement(response.bodyAsText()).jsonObject["reference"]?.jsonPrimitive?.content)
    }

    @Test
    fun `route rejects missing Team IDs before any upstream fetch`() {
        val transport = FixtureTransport()
        withTeamApplication(transport) {
            listOf("/api/v1/teams", "/api/v1/teams/").forEach { path ->
                assertError(client.get(path), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
            }
        }
        assertTrue(transport.requestedPaths.isEmpty())
    }

    @Test
    fun `route accepts a ten digit ID and rejects malformed overflow leading zero and query input without fetch`() {
        val transport = FixtureTransport()
        withTeamApplication(transport) {
            val valid = client.get("/api/v1/teams/9999999999")
            assertEquals(HttpStatusCode.OK, valid.status)

            listOf(
                "/api/v1/teams/0",
                "/api/v1/teams/001",
                "/api/v1/teams/10000000000",
                "/api/v1/teams/not-a-number",
                "/api/v1/teams/8185?view=all",
                "/api/v1/teams/8185?view=all&view=compact",
                "/api/v1/teams/8185?unknown=1",
            ).forEach { path -> assertError(client.get(path), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST) }
        }
        val requestedPaths = transport.requestedPaths
        assertEquals(setOf("/team/9999999999/", "/team/news/9999999999/"), requestedPaths.toSet())
        assertEquals(2, requestedPaths.size)
    }

    @Test
    fun `route maps either upstream fetch failure to the common safe envelope`() {
        listOf("/team/8185/", "/team/news/8185/").forEach { failedPath ->
            withTeamApplication(FixtureTransport(failedPath = failedPath)) {
                val response = client.get("/api/v1/teams/8185")
                assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
                assertFalse(response.bodyAsText().contains("vlr.gg"))
            }
        }
    }

    @Test
    fun `route maps parser DOM drift to the common safe envelope`() = withTeamApplication(
        FixtureTransport(overviewHtml = "<html><body>private malformed source</body></html>"),
    ) {
        val response = client.get("/api/v1/teams/8185")
        assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
        assertFalse(response.bodyAsText().contains("private malformed"))
    }

    private fun withTeamApplication(
        transport: FixtureTransport,
        newsService: NewsService? = null,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            module(
                newsService = newsService,
                teamDetailService = createTeamDetailService(transport),
            )
        }
        block()
    }

    private suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).code)
    }

    private class FixtureTransport(
        private val overviewHtml: String = TeamDetailRoutesTest.fixture("active-team-overview.html"),
        private val newsHtml: String = TeamDetailRoutesTest.fixture("active-team-news.html"),
        private val failedPath: String? = null,
    ) : UpstreamHtmlTransport {
        private val lock = Any()
        private val recordedPaths = mutableListOf<String>()

        val requestedPaths: List<String>
            get() = synchronized(lock) { recordedPaths.toList() }

        override suspend fun get(url: Url): String {
            val path = url.encodedPath
            val shouldFail = synchronized(lock) {
                recordedPaths += path
                path == failedPath
            }
            if (shouldFail) throw UpstreamNetworkFailure(url)
            return when {
                path.startsWith("/team/news/") -> newsHtml
                path.startsWith("/team/") -> overviewHtml
                else -> error("Unexpected upstream path: $path")
            }
        }
    }

    private class ArticleFixtureTransport : UpstreamHtmlTransport {
        override suspend fun get(url: Url): String = checkNotNull(
            javaClass.classLoader.getResource("fixtures/news-article.html"),
        ).readText()
    }

    private companion object {
        private fun fixture(name: String): String = checkNotNull(
            TeamDetailRoutesTest::class.java.classLoader.getResource("fixtures/teams/$name"),
        ).readText()
    }
}

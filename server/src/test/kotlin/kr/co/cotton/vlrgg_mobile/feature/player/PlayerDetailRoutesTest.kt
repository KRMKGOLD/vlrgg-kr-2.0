package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.search.PlayerSearchResultResponse
import kr.co.cotton.vlrgg_mobile.feature.search.SearchMapper
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceModel
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceResult
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceResultType
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamDetailMapper
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamDetailSource
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamId
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamProfileSource
import kr.co.cotton.vlrgg_mobile.feature.teams.TeamRosterMemberSource
import kr.co.cotton.vlrgg_mobile.module
import kotlin.test.*

class PlayerDetailRoutesTest {
    @Test
    fun `route exposes the versioned player endpoint with a stable String ID`() = testApplication {
        application { module(playerDetailService = createPlayerDetailService(FixtureTransport())) }

        val response = client.get("/api/v1/players/488")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("488", body["id"]?.jsonPrimitive?.content)
        assertEquals("11060", body["currentTeam"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(
            "https://owcdn.net/img/6399bb707aacb.png",
            body["currentTeam"]?.jsonObject?.get("imageUrl")?.jsonPrimitive?.content,
        )
        assertEquals("708427", body["recentMatches"]?.jsonArray?.first()?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(5, body["recentMatches"]?.jsonArray?.size)
    }

    @Test
    fun `route preserves a missing current team image as JSON null`() = withPlayerApplication(
        FixtureTransport(html = fixture("player-detail-polluted.html")),
    ) {
        val currentTeam = Json.parseToJsonElement(client.get("/api/v1/players/488").bodyAsText())
            .jsonObject["currentTeam"]
            ?.jsonObject

        assertEquals(JsonNull, currentTeam?.get("imageUrl"))
    }

    @Test
    fun `Search Player reference ID can be used directly as the Player endpoint ID`() = withPlayerApplication(FixtureTransport()) {
        val playerId = ((SearchMapper().map(
            SearchSourceModel(listOf(SearchSourceResult(SearchSourceResultType.PLAYER, "9", "TenZ", null))),
        ).single()) as PlayerSearchResultResponse).reference.id

        val response = client.get("/api/v1/players/$playerId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(playerId, Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Team roster Player ID can be used directly as the Player endpoint ID`() = withPlayerApplication(FixtureTransport()) {
        val playerId = TeamDetailMapper().map(
            TeamId.fromPath("2"),
            TeamDetailSource(
                profile = TeamProfileSource("Team", null, null),
                upcomingMatches = emptyList(),
                recentMatches = emptyList(),
                players = listOf(TeamRosterMemberSource("4462", "MaKo", null, emptyList())),
                staff = emptyList(),
                news = emptyList(),
            ),
        ).players.single().id

        val response = client.get("/api/v1/players/$playerId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(playerId, Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `route rejects missing malformed leading-zero overlong duplicate and unknown query input before fetch`() {
        val transport = FixtureTransport()
        withPlayerApplication(transport) {
            listOf(
                "/api/v1/players",
                "/api/v1/players/",
                "/api/v1/players/488/",
                "/api/v1/players/488/extra/path",
                "/api/v1/players/0",
                "/api/v1/players/00488",
                "/api/v1/players/not-a-number",
                "/api/v1/players/10000000000",
                "/api/v1/players/488?view=all",
                "/api/v1/players/488?view=all&view=compact",
            ).forEach { path -> assertError(client.get(path), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST) }
        }
        assertTrue(transport.requestedPaths.isEmpty())
    }

    @Test
    fun `Player trailing-path guard does not replace global not found outside the Player prefix`() {
        val transport = FixtureTransport()
        withPlayerApplication(transport) {
            assertError(client.get("/api/v1/playerss/488/extra"), HttpStatusCode.NotFound, ApiErrorCode.NOT_FOUND)
        }
        assertTrue(transport.requestedPaths.isEmpty())
    }

    @Test
    fun `route maps network and parser failures to safe common envelopes`() {
        withPlayerApplication(FixtureTransport(failure = { throw UpstreamNetworkFailure(Url("https://www.vlr.gg/private")) })) {
            assertError(client.get("/api/v1/players/488"), HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        }
        withPlayerApplication(FixtureTransport(html = fixture("player-detail-required-structure-missing.html"))) {
            val response = client.get("/api/v1/players/488")
            assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
            assertFalse(response.bodyAsText().contains("vlr.gg"))
        }
    }

    private fun withPlayerApplication(
        transport: FixtureTransport,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(playerDetailService = createPlayerDetailService(transport)) }
        block()
    }

    private suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).code)
    }

    private class FixtureTransport(
        private val html: String = fixture("player-detail.html"),
        private val failure: (suspend () -> Nothing)? = null,
    ) : UpstreamHtmlTransport {
        val requestedPaths = mutableListOf<String>()

        override suspend fun get(url: Url): String {
            requestedPaths += url.encodedPath
            failure?.invoke()
            return html
        }
    }

    private companion object {
        fun fixture(name: String): String = checkNotNull(
            PlayerDetailRoutesTest::class.java.classLoader.getResource("fixtures/players/$name"),
        ).readText()
    }
}

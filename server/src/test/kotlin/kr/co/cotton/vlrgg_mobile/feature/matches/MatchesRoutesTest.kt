package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kotlin.test.*

class MatchesRoutesTest {
    @Test
    fun `routes return versioned matches payloads`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureMatchesRoutes(FakeMatchesService())
        }

        val list = client.get("/api/v1/matches/upcoming?page=2")
        assertEquals(HttpStatusCode.OK, list.status)
        val listBody = Json.parseToJsonElement(list.bodyAsText()).jsonObject
        assertEquals(2, listBody["page"]?.jsonPrimitive?.int)
        assertEquals("upcoming", listBody["category"]?.jsonPrimitive?.content)

        val detail = client.get("/api/v1/matches/709685")
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals("709685", Json.parseToJsonElement(detail.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `routes reject malformed identifiers and page input with safe error envelopes`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureMatchesRoutes(FakeMatchesService())
        }

        assertInvalid("/api/v1/matches/upcoming?page=0")
        assertInvalid("/api/v1/matches/upcoming?page=01")
        assertInvalid("/api/v1/matches/upcoming?page=1&page=2")
        assertInvalid("/api/v1/matches/upcoming?unexpected=true")
        assertInvalid("/api/v1/matches/0001")
        assertInvalid("/api/v1/matches/12abc")
    }

    @Test
    fun `routes map upstream and source parsing failures through the common envelope`() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureMatchesRoutes(
                FakeMatchesService(
                    detailFailure = SourceParsingFailure(
                        Url("https://www.vlr.gg/private?token=secret"),
                        IllegalStateException("selector details"),
                    ),
                    listFailure = UpstreamNetworkFailure(Url("https://www.vlr.gg/matches?token=secret")),
                ),
            )
        }

        assertError("/api/v1/matches/results", HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        assertError("/api/v1/matches/709685", HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
    }

    private suspend fun ApplicationTestBuilder.assertInvalid(path: String) {
        assertError(path, HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
    }

    private suspend fun ApplicationTestBuilder.assertError(
        path: String,
        status: HttpStatusCode,
        code: ApiErrorCode,
    ) {
        val response = client.get(path)
        val body = response.bodyAsText()
        assertEquals(status, response.status)
        assertEquals(code.name, Json.decodeFromString<ApiErrorResponse>(body).code.name)
        assertFalse(body.contains("selector"))
        assertFalse(body.contains("vlr.gg"))
    }

    private class FakeMatchesService(
        private val listFailure: Exception? = null,
        private val detailFailure: Exception? = null,
    ) : MatchesService {
        override suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse {
            listFailure?.let { throw it }
            return MatchesPageResponse(category, page, emptyList())
        }

        override suspend fun getMatch(matchId: String): MatchDetailResponse {
            detailFailure?.let { throw it }
            return MatchDetailResponse(
                id = matchId,
                status = MatchStatus.UPCOMING,
                timeLabel = "5:00 PM",
                relativeTimeLabel = null,
                scheduledAt = null,
                homeTeam = MatchTeamResponse("Alpha"),
                awayTeam = MatchTeamResponse("Beta"),
                homeScore = null,
                awayScore = null,
                event = MatchEventResponse("Event", null),
                description = null,
                seriesFormat = null,
                maps = emptyList(),
                headToHead = emptyList(),
                pastMatches = emptyList(),
            )
        }
    }
}

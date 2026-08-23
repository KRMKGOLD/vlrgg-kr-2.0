package kr.co.cotton.vlrgg_mobile.data.remote.impl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchListCategoryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteMatchDataSourceImplTest {

    @Test
    fun getUpcomingMatchesRequestsPageAndDeserializesResponse() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/matches/upcoming", request.url.encodedPath)
                assertEquals("2", request.url.parameters["page"])
                respondJson(UPCOMING_MATCHES_JSON)
            },
        )

        try {
            val response = RemoteMatchDataSourceImpl(client).getUpcomingMatches(page = 2)

            assertEquals(MatchListCategoryDto.UPCOMING, response.category)
            assertEquals(2, response.page)
            assertEquals(MatchStatusDto.LIVE, response.groups.single().matches.single().status)
        } finally {
            client.close()
        }
    }

    @Test
    fun getResultsRequestsPageAndDeserializesResponse() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/matches/results", request.url.encodedPath)
                assertEquals("3", request.url.parameters["page"])
                respondJson(RESULTS_MATCHES_JSON)
            },
        )

        try {
            val response = RemoteMatchDataSourceImpl(client).getResults(page = 3)

            assertEquals(MatchListCategoryDto.RESULTS, response.category)
            assertEquals(3, response.page)
            assertEquals(13, response.groups.single().matches.single().homeScore)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponseThrowsKtorResponseException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson(
                    content = """{"code":"UPSTREAM_NETWORK_FAILURE","message":"Unable to load matches"}""",
                    status = HttpStatusCode.BadGateway,
                )
            },
        )

        try {
            assertFailsWith<ResponseException> {
                RemoteMatchDataSourceImpl(client).getUpcomingMatches(page = 1)
            }
        } finally {
            client.close()
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        defaultRequest {
            url(TEST_BASE_URL)
        }
    }

    private companion object {
        const val TEST_BASE_URL = "https://example.invalid"

        val UPCOMING_MATCHES_JSON =
            """
            {
              "category": "upcoming",
              "page": 2,
              "groups": [
                {
                  "dateLabel": "TODAY",
                  "matches": [
                    {
                      "id": "4001",
                      "status": "live",
                      "timeLabel": "LIVE",
                      "relativeTimeLabel": "2h 10m",
                      "homeTeam": { "name": "Alpha", "id": "alpha" },
                      "awayTeam": { "name": "Beta", "id": null },
                      "homeScore": null,
                      "awayScore": null,
                      "event": { "name": "Champions", "series": "Playoffs", "id": "champions" }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val RESULTS_MATCHES_JSON =
            """
            {
              "category": "results",
              "page": 3,
              "groups": [
                {
                  "dateLabel": "WED, AUGUST 19, 2026",
                  "matches": [
                    {
                      "id": "3901",
                      "status": "completed",
                      "timeLabel": "10:00 AM",
                      "homeTeam": { "name": "Alpha", "id": "alpha" },
                      "awayTeam": { "name": "Beta", "id": "beta" },
                      "homeScore": 13,
                      "awayScore": 9,
                      "event": { "name": "Challengers", "series": null, "id": null }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
    }
}

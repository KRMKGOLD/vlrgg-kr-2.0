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
            assertEquals(null, response.groups.single().matches.single().homeTeam.imageUrl)
            assertEquals(null, response.groups.single().matches.single().awayTeam.imageUrl)
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
    fun getMatchDetailRequestsExactPathAndDeserializesOptionalFields() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/matches/7000", request.url.encodedPath)
                assertEquals(emptySet(), request.url.parameters.names())
                respondJson(MATCH_DETAIL_JSON)
            },
        )

        try {
            val response = RemoteMatchDataSourceImpl(client).getMatchDetail("7000")

            assertEquals("7000", response.id)
            assertEquals(MatchStatusDto.COMPLETED, response.status)
            assertEquals("2026-08-29T08:00:00Z", response.scheduledAt)
            assertEquals("https://owcdn.net/img/alpha.png", response.homeTeam.imageUrl)
            assertEquals("https://owcdn.net/img/beta.png", response.awayTeam.imageUrl)
            assertEquals(0, response.homeScore)
            assertEquals(null, response.awayScore)
            assertEquals(listOf("Lotus", "Haven"), response.maps.map { it.name })
            assertEquals(listOf("6999", "6998"), response.headToHead.map { it.id })
            assertEquals(listOf("6997", "6996"), response.pastMatches.map { it.id })
            assertEquals(0, response.pastMatches.last().awayScore)
        } finally {
            client.close()
        }
    }

    @Test
    fun getMatchDetailDeserializesOmittedOptionalFieldsAsNullAndPreservesEmptyLists() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/matches/7001", request.url.encodedPath)
                respondJson(MATCH_DETAIL_WITH_OMITTED_OPTIONAL_FIELDS_JSON)
            },
        )

        try {
            val response = RemoteMatchDataSourceImpl(client).getMatchDetail("7001")

            assertEquals(null, response.relativeTimeLabel)
            assertEquals(null, response.scheduledAt)
            assertEquals(null, response.homeTeam.imageUrl)
            assertEquals(null, response.awayTeam.imageUrl)
            assertEquals(null, response.homeScore)
            assertEquals(null, response.awayScore)
            assertEquals(null, response.event.series)
            assertEquals(null, response.event.id)
            assertEquals(null, response.description)
            assertEquals(null, response.seriesFormat)
            assertEquals(emptyList(), response.maps)
            assertEquals(emptyList(), response.headToHead)
            assertEquals(emptyList(), response.pastMatches)
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

        val MATCH_DETAIL_JSON =
            """
            {
              "id": "7000", "status": "completed", "timeLabel": "2026-08-29 17:00",
              "relativeTimeLabel": "1h ago", "scheduledAt": "2026-08-29T08:00:00Z",
              "homeTeam": { "name": "Alpha", "id": "alpha", "imageUrl": "https://owcdn.net/img/alpha.png" },
              "awayTeam": { "name": "Beta", "id": null, "imageUrl": "https://owcdn.net/img/beta.png" },
              "homeScore": 0, "awayScore": null,
              "event": { "name": "Champions", "series": "Playoffs", "id": null },
              "description": "Grand final", "seriesFormat": "Bo5",
              "maps": [
                { "name": "Lotus", "homeScore": 0, "awayScore": 13 },
                { "name": "Haven", "homeScore": null, "awayScore": null }
              ],
              "headToHead": [
                { "id": "6999", "homeTeamName": "Alpha", "awayTeamName": "Beta", "homeScore": 2, "awayScore": 0 },
                { "id": "6998", "homeTeamName": "Beta", "awayTeamName": "Alpha", "homeScore": null, "awayScore": null }
              ],
              "pastMatches": [
                { "id": "6997", "homeTeamName": "Alpha", "awayTeamName": "Gamma", "homeScore": 13, "awayScore": 11 },
                { "id": "6996", "homeTeamName": "Beta", "awayTeamName": "Delta", "homeScore": null, "awayScore": 0 }
              ]
            }
            """.trimIndent()

        val MATCH_DETAIL_WITH_OMITTED_OPTIONAL_FIELDS_JSON =
            """
            {
              "id": "7001", "status": "upcoming", "timeLabel": "TBD",
              "homeTeam": { "name": "Alpha" },
              "awayTeam": { "name": "Beta" },
              "event": { "name": "Champions" },
              "maps": [],
              "headToHead": [],
              "pastMatches": []
            }
            """.trimIndent()
    }
}

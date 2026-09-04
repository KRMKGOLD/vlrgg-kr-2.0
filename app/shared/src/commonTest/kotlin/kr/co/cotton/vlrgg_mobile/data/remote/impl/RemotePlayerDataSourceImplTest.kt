package kr.co.cotton.vlrgg_mobile.data.remote.impl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.JsonConvertException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class RemotePlayerDataSourceImplTest {

    @Test
    fun detailRequestsExactEndpointAndDeserializesPlayerResponse() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/players/488", request.url.encodedPath)
                assertEquals(emptySet(), request.url.parameters.names())
                respondJson(PLAYER_DETAIL_JSON)
            },
        )

        try {
            val response = RemotePlayerDataSourceImpl(client).getPlayerDetail("488")

            assertEquals("488", response.id)
            assertEquals("Rb", response.profile.handle)
            assertEquals("https://owcdn.net/img/rb.png", response.profile.imageUrl)
            assertEquals("11060", response.currentTeam?.id)
            assertEquals("https://owcdn.net/img/6399bb707aacb.png", response.currentTeam?.imageUrl)
            assertEquals(1.07, response.agentStats.single().rating)
            assertNull(response.agentStats.single().kills)
            assertEquals(listOf("708427", "708426"), response.recentMatches.map { it.id })
            assertNull(response.recentMatches.last().eventStage)
            assertNull(response.recentMatches.last().teamAScore)
            assertNull(response.recentMatches.last().playedOn)
        } finally {
            client.close()
        }
    }

    @Test
    fun omittedProfileImageDeserializesAsNull() = runTest {
        val client = createClient(MockEngine { respondJson(PLAYER_DETAIL_WITHOUT_PROFILE_IMAGE_JSON) })

        try {
            val response = RemotePlayerDataSourceImpl(client).getPlayerDetail("488")

            assertNull(response.profile.imageUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponseThrowsKtorResponseException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson("{\"code\":\"UPSTREAM_NETWORK_FAILURE\"}", HttpStatusCode.BadGateway)
            },
        )

        try {
            assertFailsWith<ResponseException> {
                RemotePlayerDataSourceImpl(client).getPlayerDetail("488")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun malformedResponseThrowsSerializationException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson(PLAYER_DETAIL_JSON.replace("\"WIN\"", "\"DRAW\""))
            },
        )

        try {
            val thrown = assertFailsWith<JsonConvertException> {
                RemotePlayerDataSourceImpl(client).getPlayerDetail("488")
            }

            assertIs<SerializationException>(thrown.cause)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationFromClientIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val client = createClient(
            MockEngine { throw cancellation },
        )

        try {
            val thrown = assertFailsWith<CancellationException> {
                RemotePlayerDataSourceImpl(client).getPlayerDetail("488")
            }

            assertEquals(cancellation.message, thrown.message)
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

        val PLAYER_DETAIL_JSON =
            """
            {
              "id": "488",
              "profile": {
                "handle": "Rb",
                "realName": "Goo Sang-min",
                "aliases": ["ClokingRb"],
                "countryCode": "kr",
                "countryName": "SOUTH KOREA",
                "imageUrl": "https://owcdn.net/img/rb.png"
              },
              "currentTeam": {
                "id": "11060",
                "name": "Nongshim RedForce",
                "imageUrl": "https://owcdn.net/img/6399bb707aacb.png"
              },
              "agentStats": [{
                "agentName": "jett",
                "mapsPlayed": 134,
                "pickRatePercent": 25,
                "roundsPlayed": 2680,
                "rating": 1.07,
                "averageCombatScore": 235.1,
                "killDeathRatio": 1.3,
                "kastPercent": 72,
                "averageDamagePerRound": 140.5,
                "killsPerRound": 0.83,
                "assistsPerRound": 0.13,
                "firstKillDeathRatio": 1.25,
                "kills": null,
                "deaths": 1712,
                "assists": 355,
                "firstKills": 545,
                "firstDeaths": 435
              }],
              "recentMatches": [
                {
                  "id": "708427", "eventName": "EWC 2026", "eventStage": "Playoffs · CF",
                  "teamA": {"name": "Nongshim RedForce", "tag": "NS"},
                  "teamB": {"name": "BBL Esports", "tag": "BBL"},
                  "teamAScore": 2, "teamBScore": 0, "outcome": "WIN", "playedOn": "2026-07-12"
                },
                {
                  "id": "708426", "eventName": "EWC 2026", "eventStage": null,
                  "teamA": {"name": "Nongshim RedForce", "tag": null},
                  "teamB": {"name": "BBL Esports", "tag": null},
                  "teamAScore": null, "teamBScore": 1, "outcome": "LOSS", "playedOn": null
                }
              ]
            }
            """.trimIndent()

        val PLAYER_DETAIL_WITHOUT_PROFILE_IMAGE_JSON =
            """
            {
              "id": "488",
              "profile": {
                "handle": "Rb",
                "aliases": []
              },
              "agentStats": [],
              "recentMatches": []
            }
            """.trimIndent()
    }
}

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
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteTeamDataSourceImplTest {

    @Test
    fun getTeamDetailRequestsExactEndpointAndDeserializesAllSections() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/teams/8185", request.url.encodedPath)
                assertEquals(emptySet(), request.url.parameters.names())
                respondJson(TEAM_DETAIL_JSON)
            },
        )

        try {
            val response = RemoteTeamDataSourceImpl(client).getTeamDetail("8185")

            assertEquals("8185", response.id)
            assertEquals("KRX", response.tag)
            assertEquals("Stage 2", response.upcomingMatches.single().eventStage)
            assertEquals("698100", response.recentMatches.single().id)
            assertEquals("MaKo", response.players.single().handle)
            assertEquals(listOf("head coach"), response.staff.single().roleLabels)
            assertEquals("700755/kiwoom-drx-releases-rookie-hermes", response.news.single().reference)
        } finally {
            client.close()
        }
    }

    @Test
    fun nullMetadataAndEmptySectionsDeserializeWithoutDefaults() = runTest {
        val client = createClient(MockEngine { respondJson(NULLABLE_AND_EMPTY_TEAM_DETAIL_JSON) })

        try {
            val response = RemoteTeamDataSourceImpl(client).getTeamDetail("19296")

            assertEquals(null, response.tag)
            assertEquals(null, response.country)
            assertEquals(emptyList(), response.upcomingMatches)
            assertEquals(emptyList(), response.recentMatches)
            assertEquals(emptyList(), response.players)
            assertEquals(emptyList(), response.staff)
            assertEquals(emptyList(), response.news)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponseThrowsKtorResponseException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson(
                    content = """{"code":"UPSTREAM_NETWORK_FAILURE","message":"Unable to load team"}""",
                    status = HttpStatusCode.BadGateway,
                )
            },
        )

        try {
            assertFailsWith<ResponseException> {
                RemoteTeamDataSourceImpl(client).getTeamDetail("8185")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun malformedResponseThrowsSerializationException() = runTest {
        val client = createClient(MockEngine { respondJson("""{"id":"8185"}""") })

        try {
            assertFailsWith<JsonConvertException> {
                RemoteTeamDataSourceImpl(client).getTeamDetail("8185")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val client = createClient(MockEngine { throw cancellation })

        try {
            assertFailsWith<CancellationException> {
                RemoteTeamDataSourceImpl(client).getTeamDetail("8185")
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

        val TEAM_DETAIL_JSON =
            """
            {
              "id": "8185",
              "name": "KIWOOM DRX",
              "tag": "KRX",
              "country": "South Korea",
              "upcomingMatches": [{
                "id": "698887",
                "eventName": "VCT Pacific",
                "eventStage": "Stage 2",
                "teamName": "KIWOOM DRX",
                "opponentName": "Sentinels",
                "statusText": "in 2d",
                "scheduledAtText": "2026-08-28 17:00"
              }],
              "recentMatches": [{
                "id": "698100",
                "eventName": "VCT Pacific",
                "eventStage": null,
                "teamName": "KIWOOM DRX",
                "opponentName": "Gen.G",
                "statusText": "final",
                "scheduledAtText": null
              }],
              "players": [{
                "id": "4462",
                "handle": "MaKo",
                "realName": "Kim Myeong-kwan",
                "roleLabels": ["player"]
              }],
              "staff": [{
                "id": "775",
                "handle": "termi",
                "realName": null,
                "roleLabels": ["head coach"]
              }],
              "news": [{
                "reference": "700755/kiwoom-drx-releases-rookie-hermes",
                "title": "KIWOOM DRX releases rookie Hermes",
                "publishedDateText": "2026-08-25"
              }]
            }
            """.trimIndent()

        val NULLABLE_AND_EMPTY_TEAM_DETAIL_JSON =
            """
            {
              "id": "19296",
              "name": "Team Korea",
              "tag": null,
              "country": null,
              "upcomingMatches": [],
              "recentMatches": [],
              "players": [],
              "staff": [],
              "news": []
            }
            """.trimIndent()
    }
}

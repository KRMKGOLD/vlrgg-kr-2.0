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
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteEventDataSourceImplTest {

    @Test
    fun getEventsRequestsFirstPageEndpointAndDeserializesGroups() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/events", request.url.encodedPath)
                assertEquals(emptySet(), request.url.parameters.names())
                respondJson(EVENTS_JSON)
            },
        )

        try {
            val response = RemoteEventDataSourceImpl(client).getEvents()

            assertEquals("100", response.ongoing.single().id)
            assertEquals(EventStatusDto.UPCOMING, response.upcoming.single().status)
            assertEquals("400", response.completedOrPaused.last().id)
            assertEquals(null, response.ongoing.single().dateLabel)
            assertEquals(null, response.ongoing.single().regionCode)
            assertEquals(null, response.ongoing.single().imageUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponseThrowsKtorResponseException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson(
                    content = """{"code":"UPSTREAM_NETWORK_FAILURE","message":"Unable to load events"}""",
                    status = HttpStatusCode.BadGateway,
                )
            },
        )

        try {
            assertFailsWith<ResponseException> {
                RemoteEventDataSourceImpl(client).getEvents()
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

        val EVENTS_JSON =
            """
            {
              "ongoing": [
                {
                  "id": "100",
                  "name": "Masters Seoul",
                  "status": "ongoing",
                  "dateLabel": null,
                  "regionCode": null,
                  "imageUrl": null
                }
              ],
              "upcoming": [
                {
                  "id": "200",
                  "name": "Champions",
                  "status": "upcoming",
                  "dateLabel": "Jun 1—20",
                  "regionCode": "na",
                  "imageUrl": "https://owcdn.net/img/champions.png"
                }
              ],
              "completedOrPaused": [
                { "id": "300", "name": "Kickoff", "status": "completed" },
                { "id": "400", "name": "Break", "status": "paused" }
              ]
            }
            """.trimIndent()
    }
}

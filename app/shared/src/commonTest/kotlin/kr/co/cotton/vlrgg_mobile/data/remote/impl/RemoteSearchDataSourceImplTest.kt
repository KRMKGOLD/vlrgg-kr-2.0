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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteSearchDataSourceImplTest {
    @Test
    fun getSearchRequestsCanonicalEndpointWithOnlyQueryAndDeserializesTypes() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/search", request.url.encodedPath)
                assertEquals(setOf("q"), request.url.parameters.names())
                assertEquals("T1 & GEN", request.url.parameters["q"])
                respondJson(SEARCH_JSON)
            },
        )

        try {
            val response = RemoteSearchDataSourceImpl(client).getSearch("T1 & GEN")

            assertEquals("T1 & GEN", response.query)
            assertEquals(4, response.results.size)
            assertEquals("T1 vs GEN", response.results.first().name)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponseThrowsKtorResponseException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson(
                    content = """{\"code\":\"UPSTREAM_NETWORK_FAILURE\",\"message\":\"Unable to load search\"}""",
                    status = HttpStatusCode.BadGateway,
                )
            },
        )

        try {
            assertFailsWith<ResponseException> {
                RemoteSearchDataSourceImpl(client).getSearch("T1")
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
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest { url(TEST_BASE_URL) }
    }

    private companion object {
        const val TEST_BASE_URL = "https://example.invalid"
        val SEARCH_JSON =
            """
            {
              "query": "T1 & GEN",
              "results": [
                {"type":"series","reference":{"resource":"series","id":"1"},"name":"T1 vs GEN","scope":"Group Stage"},
                {"type":"event","reference":{"resource":"event","id":"2"},"name":"VCT Pacific","period":null},
                {"type":"team","reference":{"resource":"team","id":"3"},"name":"T1","tagOrRegion":"Pacific"},
                {"type":"player","reference":{"resource":"player","id":"4"},"name":"T1 Sayaplayer","identity":"Ha Jung-woo"}
              ]
            }
            """.trimIndent()
    }
}

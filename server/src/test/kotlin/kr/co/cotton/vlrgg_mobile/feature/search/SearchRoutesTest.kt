package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.*

class SearchRoutesTest {

    @Test
    fun `route returns a versioned search response with normalized query`() = withSearchApplication(
        scraper = RecordingSearchScraper(),
    ) { scraper ->
        val response = client.get("/api/v1/search?q=%20Sentinels%20")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Sentinels", body["query"]?.jsonPrimitive?.content)
        assertEquals("team", body["results"]?.jsonArray?.single()?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("team", body["results"]?.jsonArray?.single()?.jsonObject?.get("reference")?.jsonObject?.get("resource")?.jsonPrimitive?.content)
        assertEquals("2", body["results"]?.jsonArray?.single()?.jsonObject?.get("reference")?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(listOf("Sentinels"), scraper.requestedQueries)
    }

    @Test
    fun `route rejects missing blank duplicate control and unknown query input without scraping`() = withSearchApplication(
        scraper = RecordingSearchScraper(),
    ) { scraper ->
        listOf(
            "/api/v1/search",
            "/api/v1/search?q=%20%20%20",
            "/api/v1/search?q=one&q=two",
            "/api/v1/search?q=one&page=1",
            "/api/v1/search?q=%0A",
            "/api/v1/search?q=---",
            "/api/v1/search?q=${"a".repeat(81)}",
        ).forEach { path ->
            assertError(client.get(path), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        }

        assertTrue(scraper.requestedQueries.isEmpty())
    }

    @Test
    fun `route maps a malformed percent encoded query to invalid request without scraping`() = runBlocking {
        val scraper = RecordingSearchScraper()
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            module(searchService = SearchService(scraper, SearchMapper()))
        }.start(wait = false)

        try {
            val connector = server.engine.resolvedConnectors().single()
            val response = Socket(connector.host, connector.port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().apply {
                    write(
                        "GET /api/v1/search?q=% HTTP/1.1\r\n".toByteArray(StandardCharsets.US_ASCII),
                    )
                    write("Host: ${connector.host}:${connector.port}\r\n".toByteArray(StandardCharsets.US_ASCII))
                    write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    flush()
                }
                socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readText()
            }

            assertTrue(response.startsWith("HTTP/1.1 400"), response)
            assertContains(response, ApiErrorCode.INVALID_REQUEST.name)
            assertFalse(response.contains(ApiErrorCode.INTERNAL_ERROR.name))
        } finally {
            server.stop(1_000, 1_000)
        }

        assertTrue(scraper.requestedQueries.isEmpty())
    }

    @Test
    fun `route maps upstream failure to the safe common envelope`() = withSearchApplication(
        scraper = FailingSearchScraper(UpstreamNetworkFailure(Url("https://www.vlr.gg/search/?q=private"))),
    ) {
        val response = client.get("/api/v1/search?q=Sentinels")

        assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
        assertFalse(response.bodyAsText().contains("vlr.gg"))
    }

    @Test
    fun `route maps malformed fixture parsing to the safe common envelope`() = testApplication {
        application {
            module(
                searchService = createSearchService(
                    object : UpstreamHtmlTransport {
                        override suspend fun get(url: Url): String = fixture("malformed-structure.html")
                    },
                ),
            )
        }

        val response = client.get("/api/v1/search?q=Sentinels")

        assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
        assertFalse(response.bodyAsText().contains("Private malformed source detail"))
    }

    private fun <T : SearchScraper> withSearchApplication(
        scraper: T,
        block: suspend ApplicationTestBuilder.(T) -> Unit,
    ) = testApplication {
        application {
            module(searchService = SearchService(scraper, SearchMapper()))
        }

        block(scraper)
    }

    private suspend fun assertError(
        response: HttpResponse,
        status: HttpStatusCode,
        code: ApiErrorCode,
    ) {
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).code)
    }

    private class RecordingSearchScraper : SearchScraper {
        val requestedQueries = mutableListOf<String>()

        override suspend fun scrape(query: String): SearchSourceModel {
            requestedQueries += query
            return SearchSourceModel(
                listOf(SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", "SEN")),
            )
        }
    }

    private class FailingSearchScraper(
        private val failure: RuntimeException,
    ) : SearchScraper {
        override suspend fun scrape(query: String): SearchSourceModel = throw failure
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/search/$name"),
    ).readText()
}

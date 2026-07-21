package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.search.SearchMapper
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceModel
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceResult
import kr.co.cotton.vlrgg_mobile.feature.search.SearchSourceResultType
import kr.co.cotton.vlrgg_mobile.feature.search.SeriesSearchResultResponse
import kr.co.cotton.vlrgg_mobile.module
import kotlin.test.*

class SeriesRoutesTest {
    @Test
    fun `route serializes Series and Event summary response from one canonical upstream URL`() = withSeriesApplication(FixtureTransport()) { transport ->
        val response = client.get("/api/v1/series/85")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("85", body["id"]?.jsonPrimitive?.content)
        assertEquals("ongoing", body["upcomingEvents"]?.jsonArray?.first()?.jsonObject?.get("status")?.jsonPrimitive?.content)
        assertEquals("completed", body["completedEvents"]?.jsonArray?.first()?.jsonObject?.get("status")?.jsonPrimitive?.content)
        assertEquals(listOf(Url("https://www.vlr.gg/series/85")), transport.requestedUrls)
    }

    @Test
    fun `route rejects invalid ID query and non-exact path without upstream access`() {
        val transport = FixtureTransport()
        withSeriesApplication(transport) {
            listOf(
                "/api/v1/series",
                "/api/v1/series/",
                "/api/v1/series/85/",
                "/api/v1/series/85/extra/path",
                "/api/v1/series/0",
                "/api/v1/series/085",
                "/api/v1/series/not-a-number",
                "/api/v1/series/10000000000",
                "/api/v1/series/85?page=2",
                "/api/v1/series/85?view=all&view=compact",
            ).forEach { path -> assertError(client.get(path), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST) }
        }
        assertTrue(transport.requestedUrls.isEmpty())
    }

    @Test
    fun `Series path guards do not replace not found outside the Series prefix`() {
        val transport = FixtureTransport()
        withSeriesApplication(transport) {
            assertError(client.get("/api/v1/seriess/85/extra"), HttpStatusCode.NotFound, ApiErrorCode.NOT_FOUND)
        }
        assertTrue(transport.requestedUrls.isEmpty())
    }

    @Test
    fun `route maps network and parser failures to safe common envelopes`() {
        withSeriesApplication(FixtureTransport(failure = { url -> throw UpstreamNetworkFailure(url) })) {
            val response = client.get("/api/v1/series/85")
            assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
            assertFalse(response.bodyAsText().contains("vlr.gg"))
        }
        withSeriesApplication(FixtureTransport(html = fixture("missing-required-structure.html"))) {
            val response = client.get("/api/v1/series/85")
            assertError(response, HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
            assertFalse(response.bodyAsText().contains("container"))
        }
    }

    @Test
    fun `Search Series reference ID targets Series endpoint unchanged`() = withSeriesApplication(FixtureTransport()) {
        val searchSeriesId = (SearchMapper().map(
            SearchSourceModel(listOf(SearchSourceResult(SearchSourceResultType.SERIES, "85", "Series", null))),
        ).single() as SeriesSearchResultResponse).reference.id

        val response = client.get("/api/v1/series/$searchSeriesId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(searchSeriesId, Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content)
    }

    private fun withSeriesApplication(
        transport: FixtureTransport,
        block: suspend ApplicationTestBuilder.(FixtureTransport) -> Unit,
    ) = testApplication {
        application { module(seriesService = createSeriesService(transport)) }
        block(transport)
    }

    private suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).code)
    }

    private class FixtureTransport(
        private val html: String = fixture("both-groups.html"),
        private val failure: (suspend (Url) -> Nothing)? = null,
    ) : UpstreamHtmlTransport {
        val requestedUrls = mutableListOf<Url>()

        override suspend fun get(url: Url): String {
            requestedUrls += url
            failure?.invoke(url)
            return html
        }
    }

    private companion object {
        fun fixture(name: String): String = checkNotNull(
            SeriesRoutesTest::class.java.classLoader.getResource("fixtures/series/$name"),
        ).readText()
    }
}

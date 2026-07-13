package kr.co.cotton.vlrgg_mobile.feature.news

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.http.Url
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kotlin.test.*

class NewsRoutesTest {

    @Test
    fun `news routes return versioned list and structured article contracts`() = testApplication {
        val requestedUrls = mutableListOf<Url>()
        application {
            module(newsService = serviceForFixtures(onRequest = requestedUrls::add))
        }

        val listResponse = client.get("/api/v1/news?page=1")
        val listJson = Json.parseToJsonElement(listResponse.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertEquals(1, listJson["page"]?.jsonPrimitive?.int)
        assertEquals(2, listJson["nextPage"]?.jsonPrimitive?.int)
        assertEquals("101/champions-run", listJson["items"]?.jsonArray?.first()?.jsonObject?.get("reference")?.jsonPrimitive?.content)

        val detailResponse = client.get("/api/v1/news/101/champions-run")
        val detailBody = detailResponse.bodyAsText()
        val detailJson = Json.parseToJsonElement(detailBody).jsonObject

        assertEquals(HttpStatusCode.OK, detailResponse.status)
        assertEquals("Champions run", detailJson["title"]?.jsonPrimitive?.content)
        assertEquals("paragraph", detailJson["blocks"]?.jsonArray?.get(0)?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("image", detailJson["blocks"]?.jsonArray?.get(1)?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("list", detailJson["blocks"]?.jsonArray?.get(2)?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("TEAM", detailBody.findLinkKind("Sentinels"))
        assertTrue(detailBody.contains("\"label\":\"TenZ\",\"kind\":\"PLAYER\""))
        assertEquals("EVENT", detailBody.findLinkKind("event"))
        assertEquals("MATCH", detailBody.findLinkKind("match"))
        assertEquals(JsonNull, detailBody.findLink("event")?.get("reference"))
        assertEquals(JsonNull, detailBody.findLink("match")?.get("reference"))
        assertFalse(detailBody.contains("wf-hover-card"))
        assertFalse(detailBody.contains("hidden hover-card text"))
        assertFalse(detailBody.contains("article-body"))
        assertEquals(
            listOf(
                "https://www.vlr.gg/news",
                "https://www.vlr.gg/101/champions-run",
            ),
            requestedUrls.map(Url::toString),
        )
    }

    @Test
    fun `news routes reject malformed pagination and article references`() = testApplication {
        application {
            module(newsService = serviceForFixtures())
        }

        listOf(
            "/api/v1/news?page=0",
            "/api/v1/news?page=01",
            "/api/v1/news?page=1&page=2",
            "/api/v1/news?unknown=value",
            "/api/v1/news/0/champions-run",
            "/api/v1/news/101/Uppercase",
        ).forEach { path ->
            assertError(path, HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        }
    }

    @Test
    fun `news routes map transport and parsing failures to safe envelopes`() = testApplication {
        application {
            module(newsService = NewsService(NewsScraper(FailingTransport), NewsParser(), NewsMapper()))
        }

        assertError("/api/v1/news", HttpStatusCode.BadGateway, ApiErrorCode.UPSTREAM_NETWORK_FAILURE)
    }

    @Test
    fun `news routes map invalid required article structure to a parsing failure`() = testApplication {
        application {
            module(
                newsService = NewsService(
                    scraper = NewsScraper(
                        HtmlTransport { "<html><h1>Missing body</h1></html>" },
                    ),
                    parser = NewsParser(),
                    mapper = NewsMapper(),
                ),
            )
        }

        assertError("/api/v1/news/101/champions-run", HttpStatusCode.BadGateway, ApiErrorCode.SOURCE_PARSING_FAILURE)
    }

    private suspend fun ApplicationTestBuilder.assertError(
        path: String,
        status: HttpStatusCode,
        code: ApiErrorCode,
    ) {
        val response = client.get(path)
        val body = response.bodyAsText()

        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(body).code)
        assertFalse(body.contains("vlr.gg"))
        assertFalse(body.contains("article-body"))
    }

    private fun serviceForFixtures(onRequest: (Url) -> Unit = {}): NewsService = NewsService(
        scraper = NewsScraper(
            HtmlTransport { url ->
                onRequest(url)
                when (url.encodedPath) {
                    "/news" -> readFixture("news-list-page-1.html")
                    "/101/champions-run" -> readFixture("news-article.html")
                    else -> error("Unexpected requested path: ${url.encodedPath}")
                }
            },
        ),
        parser = NewsParser(),
        mapper = NewsMapper(),
    )

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture: $name" }.readText()

    private fun String.findLink(label: String): JsonObject? {
        val blocks = Json.parseToJsonElement(this).jsonObject["blocks"]?.jsonArray.orEmpty()
        return blocks
            .flatMap { block -> block.jsonObject["content"]?.jsonArray.orEmpty() }
            .firstOrNull { inline -> inline.jsonObject["label"]?.jsonPrimitive?.content == label }
            ?.jsonObject
    }

    private fun String.findLinkKind(label: String): String? =
        findLink(label)?.get("kind")?.jsonPrimitive?.content

    private fun JsonElement?.orEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

    private fun interface HtmlTransport : UpstreamHtmlTransport {
        override suspend fun get(url: Url): String
    }

    private object FailingTransport : UpstreamHtmlTransport {
        override suspend fun get(url: Url): String = throw UpstreamNetworkFailure(
            upstreamUrl = url,
            cause = IllegalStateException("network details"),
        )
    }
}

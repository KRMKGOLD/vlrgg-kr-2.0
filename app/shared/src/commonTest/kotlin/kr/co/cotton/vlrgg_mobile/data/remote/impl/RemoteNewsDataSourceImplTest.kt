package kr.co.cotton.vlrgg_mobile.data.remote.impl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleBlockDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.news.NewsArticleInlineDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RemoteNewsDataSourceImplTest {

    @Test
    fun getNewsPageRequestsPageAndDeserializesResponse() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals("/api/v1/news", request.url.encodedPath)
                assertEquals("2", request.url.parameters["page"])
                respondJson(NEWS_LIST_JSON)
            },
        )

        try {
            val response = RemoteNewsDataSourceImpl(client).getNewsPage(page = 2)

            assertEquals(2, response.page)
            assertEquals(3, response.nextPage)
            assertEquals("101/champions-run", response.items.single().reference)
        } finally {
            client.close()
        }
    }

    @Test
    fun getNewsArticleAppendsEncodedPathSegmentsAndDeserializesResponse() = runTest {
        val client = createClient(
            MockEngine { request ->
                assertEquals(
                    "/api/v1/news/101/champions-run",
                    request.url.encodedPath,
                )
                respondJson(NEWS_ARTICLE_JSON)
            },
        )

        try {
            val response = RemoteNewsDataSourceImpl(client).getNewsArticle(
                articleId = "101",
                slug = "champions-run",
            )

            assertEquals("101/champions-run", response.reference)
            val paragraph = assertIs<NewsArticleBlockDto.Paragraph>(response.blocks.single())
            assertIs<NewsArticleInlineDto.Text>(paragraph.content.single())
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponseThrowsKtorResponseException() = runTest {
        val client = createClient(
            MockEngine {
                respondJson(
                    content = """{"code":"INVALID_INPUT","message":"Invalid input"}""",
                    status = HttpStatusCode.BadRequest,
                )
            },
        )

        try {
            assertFailsWith<ClientRequestException> {
                RemoteNewsDataSourceImpl(client).getNewsPage(page = 0)
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
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }

        defaultRequest {
            url(TEST_BASE_URL)
        }
    }

    private companion object {
        const val TEST_BASE_URL = "https://example.invalid"

        val NEWS_LIST_JSON =
            """
            {
              "page": 2,
              "nextPage": 3,
              "items": [
                {
                  "reference": "101/champions-run",
                  "title": "Champions run",
                  "author": "Reporter",
                  "publishedAt": "2026-08-09T12:00:00Z"
                }
              ]
            }
            """.trimIndent()

        val NEWS_ARTICLE_JSON =
            """
            {
              "reference": "101/champions-run",
              "title": "Champions run",
              "author": "Reporter",
              "publishedAt": "2026-08-09T12:00:00Z",
              "blocks": [
                {
                  "type": "paragraph",
                  "content": [
                    {
                      "type": "text",
                      "text": "Article body"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
    }
}

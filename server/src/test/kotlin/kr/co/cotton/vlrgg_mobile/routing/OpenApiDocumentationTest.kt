package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.POSITIVE_DECIMAL_ID_OPENAPI_PATTERN
import kr.co.cotton.vlrgg_mobile.feature.news.MAX_NEWS_PAGE
import kr.co.cotton.vlrgg_mobile.feature.news.MINIMUM_NEWS_PAGE
import kr.co.cotton.vlrgg_mobile.module
import kotlin.test.*

class OpenApiDocumentationTest {

    @Test
    fun `OpenAPI JSON documents public API routes and excludes documentation routes`() = testApplication {
        application { module(enableApiDocumentation = true) }

        val response = client.get("/openapi.json")
        val specification = response.bodyAsText()
        val paths = Json.parseToJsonElement(specification).jsonObject["paths"]!!.jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
        assertEquals(
            setOf(
                "/api/v1/news",
                "/api/v1/news/{articleId}/{slug}",
                "/api/v1/matches/upcoming",
                "/api/v1/matches/results",
                "/api/v1/matches/{matchId}",
                "/api/v1/events",
                "/api/v1/events/{eventId}",
                "/api/v1/events/{eventId}/matches",
                "/api/v1/events/{eventId}/news",
                "/api/v1/events/{eventId}/stats",
                "/api/v1/search",
                "/api/v1/teams/{teamId}",
                "/api/v1/players/{playerId}",
                "/api/v1/series/{seriesId}",
            ),
            paths.keys,
        )
        assertFalse(paths.containsKey("/openapi.json"))
        assertFalse(paths.containsKey("/swagger"))
        paths.values.forEach { pathItem ->
            val responses = pathItem.jsonObject["get"]!!.jsonObject["responses"]!!.jsonObject
            assertEquals(setOf("200", "400", "502", "500"), responses.keys)
        }
    }

    @Test
    fun `OpenAPI parameter schemas mirror server validation constraints`() = testApplication {
        application { module(enableApiDocumentation = true) }

        val paths = openApiPaths()

        assertCanonicalPage(
            paths = paths,
            path = "/api/v1/news",
            minimum = MINIMUM_NEWS_PAGE,
            maximum = MAX_NEWS_PAGE,
            maximumLength = 5,
            pattern = "^(?:[1-9][0-9]{0,3}|10000)$",
        )
        listOf("/api/v1/matches/upcoming", "/api/v1/matches/results").forEach { path ->
            assertCanonicalPage(
                paths = paths,
                path = path,
                minimum = 1,
                maximum = 1_000,
                maximumLength = 4,
                pattern = "^(?:[1-9][0-9]{0,2}|1000)$",
            )
        }

        listOf(
            "/api/v1/news/{articleId}/{slug}" to "articleId",
            "/api/v1/matches/{matchId}" to "matchId",
            "/api/v1/events/{eventId}" to "eventId",
            "/api/v1/events/{eventId}/matches" to "eventId",
            "/api/v1/events/{eventId}/news" to "eventId",
            "/api/v1/events/{eventId}/stats" to "eventId",
            "/api/v1/teams/{teamId}" to "teamId",
            "/api/v1/players/{playerId}" to "playerId",
            "/api/v1/series/{seriesId}" to "seriesId",
        ).forEach { (path, name) ->
            assertPositiveDecimalId(paths.parameter(path, name))
        }

        val slug = paths.parameter("/api/v1/news/{articleId}/{slug}", "slug").schema()
        assertEquals("string", slug.string("type"))
        assertEquals(1, slug.int("minLength"))
        assertEquals(128, slug.int("maxLength"))
        assertEquals("^[a-z0-9][a-z0-9-]{0,127}$", slug.string("pattern"))

        val newsPagePattern = paths.parameter("/api/v1/news", "page").schema().string("pattern")
        val compiledNewsPagePattern = Regex(newsPagePattern)
        assertTrue(compiledNewsPagePattern.matches(MAX_NEWS_PAGE.toString()))
        assertFalse(compiledNewsPagePattern.matches((MAX_NEWS_PAGE + 1).toString()))

        val newsArticleDescription = paths["/api/v1/news/{articleId}/{slug}"]!!
            .jsonObject["get"]!!.jsonObject.string("description")
        assertTrue(newsArticleDescription.contains("Query parameters are ignored."))

        val search = paths.parameter("/api/v1/search", "q")
        val searchSchema = search.schema()
        assertTrue(search.string("description").contains("trims surrounding whitespace"))
        assertTrue(search.string("description").contains("Unicode letter or digit"))
        assertEquals("string", searchSchema.string("type"))
        assertEquals(1, searchSchema.int("minLength"))
        val searchPattern = searchSchema.string("pattern")
        assertEquals("^[^\\u0000-\\u001F\\u007F-\\u009F]+$", searchPattern)
        assertFalse(searchPattern.contains("\\p{"))
        val portableSearchPattern = Regex(searchPattern)
        listOf("masters tokyo", "발로란트 검색").forEach { query ->
            assertTrue(portableSearchPattern.matches(query), "Expected standard pattern to accept $query")
        }
        listOf(
            "line${'\n'}break",
            "delete${0x7F.toChar()}",
            "control${0x9F.toChar()}",
        ).forEach { query ->
            assertFalse(portableSearchPattern.matches(query), "Expected standard pattern to reject controls")
        }
        assertTrue(search.boolean("x-server-single-value"))
        assertTrue(search.boolean("x-server-trim-before-validation"))
        assertEquals(1, search.int("x-server-trimmed-minimum-length"))
        assertEquals(80, search.int("x-server-trimmed-maximum-length"))
        assertTrue(search.boolean("x-server-requires-unicode-letter-or-digit"))
        assertTrue(search.boolean("x-server-rejects-iso-control"))
    }

    @Test
    fun `Swagger UI and OpenAPI error schemas expose only public contract details`() = testApplication {
        application { module(enableApiDocumentation = true) }

        val swagger = client.get("/swagger")
        val specification = client.get("/openapi.json").bodyAsText()
        val swaggerHtml = swagger.bodyAsText()

        assertEquals(HttpStatusCode.OK, swagger.status)
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), swagger.contentType())
        assertTrue(swaggerHtml.contains("Swagger UI"))
        assertTrue(swaggerHtml.contains("openapi.json"))
        assertTrue(specification.contains("ApiErrorResponse"))
        assertTrue(specification.contains("INVALID_REQUEST"))
        assertTrue(specification.contains("NOT_FOUND"))
        assertTrue(specification.contains("UPSTREAM_NETWORK_FAILURE"))
        assertTrue(specification.contains("SOURCE_PARSING_FAILURE"))
        assertTrue(specification.contains("INTERNAL_ERROR"))
        listOf("Jsoup", "selector", "SourceModel", "raw HTML", "https://www.vlr.gg/").forEach {
            assertFalse(specification.contains(it), "OpenAPI document must not expose $it")
        }
    }

    @Test
    fun `OpenAPI documentation routes require explicit opt-in`() = testApplication {
        application { module() }

        listOf("/openapi.json", "/swagger").forEach { path ->
            val response = client.get(path)

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ContentType.Application.Json, response.contentType())
            assertEquals(
                ApiErrorResponse(
                    code = ApiErrorCode.NOT_FOUND,
                    message = "Requested resource was not found.",
                ),
                Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()),
            )
        }
    }

    private suspend fun ApplicationTestBuilder.openApiPaths(): JsonObject = Json.parseToJsonElement(
        client.get("/openapi.json").bodyAsText(),
    ).jsonObject["paths"]!!.jsonObject

    private fun assertCanonicalPage(
        paths: JsonObject,
        path: String,
        minimum: Int,
        maximum: Int,
        maximumLength: Int,
        pattern: String,
    ) {
        val parameter = paths.parameter(path, "page")
        val schema = parameter.schema()

        assertEquals("query", parameter.string("in"))
        assertFalse(parameter.booleanOrFalse("required"))
        assertEquals("string", schema.string("type"))
        assertEquals("1", schema.string("default"))
        assertEquals(1, schema.int("minLength"))
        assertEquals(maximumLength, schema.int("maxLength"))
        assertEquals(pattern, schema.string("pattern"))
        assertEquals(minimum, parameter.int("x-server-minimum"))
        assertEquals(maximum, parameter.int("x-server-maximum"))
        assertTrue(parameter.boolean("x-server-canonical-decimal"))
        assertTrue(parameter.boolean("x-server-single-value"))
    }

    private fun assertPositiveDecimalId(parameter: JsonObject) {
        val schema = parameter.schema()

        assertEquals("path", parameter.string("in"))
        assertTrue(parameter.boolean("required"))
        assertEquals("string", schema.string("type"))
        assertEquals(1, schema.int("minLength"))
        assertEquals(10, schema.int("maxLength"))
        assertEquals(POSITIVE_DECIMAL_ID_OPENAPI_PATTERN, schema.string("pattern"))
    }

    private fun JsonObject.parameter(path: String, name: String): JsonObject = this[path]!!
        .jsonObject["get"]!!
        .jsonObject["parameters"]!!
        .jsonArray
        .map(JsonElement::jsonObject)
        .single { it.string("name") == name }

    private fun JsonObject.schema(): JsonObject = this["schema"]!!.jsonObject

    private fun JsonObject.string(name: String): String = this[name]!!.jsonPrimitive.content

    private fun JsonObject.int(name: String): Int = this[name]!!.jsonPrimitive.int

    private fun JsonObject.boolean(name: String): Boolean = requireNotNull(this[name]) {
        "Missing $name; available keys: ${keys}"
    }.jsonPrimitive.boolean

    private fun JsonObject.booleanOrFalse(name: String): Boolean = this[name]?.jsonPrimitive?.boolean ?: false
}

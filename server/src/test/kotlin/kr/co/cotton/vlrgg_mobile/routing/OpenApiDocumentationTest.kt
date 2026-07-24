package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.module
import kotlin.test.*

class OpenApiDocumentationTest {

    @Test
    fun `OpenAPI JSON documents public API routes and excludes documentation routes`() = testApplication {
        application { module() }

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
        application { module() }

        val paths = openApiPaths()

        assertCanonicalPage(
            paths = paths,
            path = "/api/v1/news",
            maximum = 10_000,
            maximumLength = 5,
            pattern = "^(?:[1-9][0-9]{0,3}|10000)$",
        )
        listOf("/api/v1/matches/upcoming", "/api/v1/matches/results").forEach { path ->
            assertCanonicalPage(
                paths = paths,
                path = path,
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

        val search = paths.parameter("/api/v1/search", "q")
        val searchSchema = search.schema()
        assertEquals("string", searchSchema.string("type"))
        assertEquals(1, searchSchema.int("minLength"))
        assertTrue(searchSchema.string("pattern").contains("\\p{L}"))
        assertTrue(search.boolean("x-server-single-value"))
        assertTrue(search.boolean("x-server-trim-before-validation"))
        assertEquals(1, search.int("x-server-trimmed-minimum-length"))
        assertEquals(80, search.int("x-server-trimmed-maximum-length"))
        assertTrue(search.boolean("x-server-requires-letter-or-digit"))
        assertTrue(search.boolean("x-server-rejects-iso-control"))
    }

    @Test
    fun `Swagger UI and OpenAPI error schemas expose only public contract details`() = testApplication {
        application { module() }

        val swagger = client.get("/swagger")
        val specification = client.get("/openapi.json").bodyAsText()

        assertEquals(HttpStatusCode.OK, swagger.status)
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), swagger.contentType())
        assertTrue(swagger.bodyAsText().contains("Swagger UI"))
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

    private suspend fun ApplicationTestBuilder.openApiPaths(): JsonObject = Json.parseToJsonElement(
        client.get("/openapi.json").bodyAsText(),
    ).jsonObject["paths"]!!.jsonObject

    private fun assertCanonicalPage(
        paths: JsonObject,
        path: String,
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
        assertEquals(1, parameter.int("x-server-minimum"))
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
        assertEquals("^[1-9][0-9]{0,9}$", schema.string("pattern"))
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

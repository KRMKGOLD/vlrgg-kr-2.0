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
        val responses = paths["/api/v1/news"]!!.jsonObject["get"]!!.jsonObject["responses"]!!.jsonObject
        assertTrue(setOf("200", "400", "404", "502", "500").all(responses::containsKey))
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
}

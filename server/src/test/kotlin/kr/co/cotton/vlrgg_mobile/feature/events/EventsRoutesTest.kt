package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kotlin.test.*

class EventsRoutesTest {
    @Test
    fun `event routes return independent versioned list detail matches news and stats responses`() = testApplication {
        application { module(eventsService = createEventsService(fixtureTransport())) }

        val listResponse = client.get("/api/v1/events")
        val detailResponse = client.get("/api/v1/events/100")
        val matchesResponse = client.get("/api/v1/events/100/matches")
        val newsResponse = client.get("/api/v1/events/100/news")
        val statsResponse = client.get("/api/v1/events/100/stats")

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertEquals(listOf("100"), Json.decodeFromString<EventListResponse>(listResponse.bodyAsText()).ongoing.map { it.id })
        assertEquals("Masters Seoul", Json.decodeFromString<EventDetailResponse>(detailResponse.bodyAsText()).name)
        assertEquals(
            listOf("501", "502"),
            Json.decodeFromString<EventMatchesResponse>(matchesResponse.bodyAsText()).items.map { it.id },
        )
        assertEquals(
            "701/masters-recap",
            Json.decodeFromString<EventNewsListResponse>(newsResponse.bodyAsText()).items.single().reference,
        )
        assertEquals(
            EventStatsAvailability.AVAILABLE,
            Json.decodeFromString<EventStatsResponse>(statsResponse.bodyAsText()).availability,
        )
    }

    @Test
    fun `event routes reject non canonical identifiers and all unsupported query inputs`() = testApplication {
        application { installEventsRoutes(fixtureTransport()) }

        listOf(
            "/api/v1/events?filter=all",
            "/api/v1/events/0",
            "/api/v1/events/01",
            "/api/v1/events/abc",
            "/api/v1/events/100?debug=true",
            "/api/v1/events/100/matches?page=1",
            "/api/v1/events/100/news?debug=true",
            "/api/v1/events/100/stats?debug=true",
        ).forEach { path ->
            val response = client.get(path)
            val error = Json.decodeFromString<ApiErrorResponse>(response.bodyAsText())

            assertEquals(HttpStatusCode.BadRequest, response.status, path)
            assertEquals(ApiErrorCode.INVALID_REQUEST, error.code, path)
        }
    }

    @Test
    fun `normal upstream no-stats response stays a successful explicit empty state`() = testApplication {
        val transport = fixtureTransport(
            replacements = mapOf(
                "https://www.vlr.gg/event/stats/100" to fixture("event-stats-empty.html"),
            ),
        )
        application { installEventsRoutes(transport) }

        val response = client.get("/api/v1/events/100/stats")
        val body = Json.decodeFromString<EventStatsResponse>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(EventStatsAvailability.NOT_AVAILABLE, body.availability)
        assertTrue(body.players.isEmpty())
    }

    @Test
    fun `upstream failures retain the safe common network error envelope`() = testApplication {
        application {
            installEventsRoutes(
                object : UpstreamHtmlTransport {
                    override suspend fun get(url: Url): String {
                        throw UpstreamNetworkFailure(url, IllegalStateException("private upstream token"))
                    }
                },
            )
        }

        val response = client.get("/api/v1/events/100/stats")
        val body = response.bodyAsText()
        val error = Json.decodeFromString<ApiErrorResponse>(body)

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ApiErrorCode.UPSTREAM_NETWORK_FAILURE, error.code)
        assertFalse(body.contains("private"))
        assertFalse(body.contains("vlr.gg"))
    }

    @Test
    fun `malformed upstream HTML maps to safe source parsing failure`() = testApplication {
        application {
            installEventsRoutes(
                object : UpstreamHtmlTransport {
                    override suspend fun get(url: Url): String =
                        "<html><body><div class='event-header'>private DOM shape</div></body></html>"
                },
            )
        }

        val response = client.get("/api/v1/events/100/stats")
        val body = response.bodyAsText()
        val error = Json.decodeFromString<ApiErrorResponse>(body)

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(ApiErrorCode.SOURCE_PARSING_FAILURE, error.code)
        assertFalse(body.contains("private DOM"))
        assertFalse(body.contains("selector"))
    }

    private fun Application.installEventsRoutes(transport: UpstreamHtmlTransport) {
        configureSerialization()
        configureErrorHandling()
        routing {
            configureEventsRoutes(createEventsService(transport))
        }
    }

    private fun fixtureTransport(
        replacements: Map<String, String> = emptyMap(),
    ): UpstreamHtmlTransport {
        val fixtures = mapOf(
            "https://www.vlr.gg/events" to fixture("event-list.html"),
            "https://www.vlr.gg/event/100" to fixture("event-detail.html"),
            "https://www.vlr.gg/event/matches/100/?series_id=all" to fixture("event-matches.html"),
            "https://www.vlr.gg/event/news/100" to fixture("event-news.html"),
            "https://www.vlr.gg/event/stats/100" to fixture("event-stats.html"),
        ) + replacements
        return object : UpstreamHtmlTransport {
            override suspend fun get(url: Url): String = checkNotNull(fixtures[url.toString()]) { url.toString() }
        }
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("events/$name"),
    ).readText()
}

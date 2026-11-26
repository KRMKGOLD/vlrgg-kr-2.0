package kr.co.cotton.vlrgg_mobile

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheConfig
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchDetailResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchEventResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchTeamResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesPageResponse
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ApplicationTest {

    @Test
    fun `health endpoint serializes a JSON response`() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
        assertEquals("ok", Json.parseToJsonElement(response.bodyAsText()).jsonObject["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `failure types map to safe status and error envelopes`() = testApplication {
        application {
            module()
            installFailureRoutes()
        }

        assertError("/test/invalid", HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST, "Request input is invalid.")
        assertError(
            "/test/network",
            HttpStatusCode.BadGateway,
            ApiErrorCode.UPSTREAM_NETWORK_FAILURE,
            "Unable to retrieve data from the upstream source.",
        )
        assertError(
            "/test/parsing",
            HttpStatusCode.BadGateway,
            ApiErrorCode.SOURCE_PARSING_FAILURE,
            "Unable to parse data from the upstream source.",
        )
        assertError(
            "/test/internal",
            HttpStatusCode.InternalServerError,
            ApiErrorCode.INTERNAL_ERROR,
            "An unexpected server error occurred.",
        )
        assertError(
            "/missing",
            HttpStatusCode.NotFound,
            ApiErrorCode.NOT_FOUND,
            "Requested resource was not found.",
        )
    }

    @Test
    fun `unmatched routes use the common 404 envelope without replacing explicit 404 responses`() = testApplication {
        val explicitError = ApiErrorResponse(
            code = ApiErrorCode.NOT_FOUND,
            message = "The requested test resource was not found.",
        )
        application {
            module()
            routing {
                get("/test/explicit-not-found") {
                    call.response.status(HttpStatusCode.NotFound)
                    call.respond(explicitError, typeInfo<ApiErrorResponse>())
                }
            }
        }

        assertError(
            "/missing",
            HttpStatusCode.NotFound,
            ApiErrorCode.NOT_FOUND,
            "Requested resource was not found.",
        )

        val response = client.get("/test/explicit-not-found")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
        assertEquals(explicitError, Json.decodeFromString<ApiErrorResponse>(body))
        assertFalse(body.contains("Requested resource was not found."))
    }

    @Test
    fun `module caches matches responses by default`() {
        val service = CountingMatchesService()
        testApplication {
            application { module(matchesService = service, responseCacheConfig = ResponseCacheConfig.fromEnvironment(emptyMap())) }

            val miss = client.get("/api/v1/matches/123")
            val hit = client.get("/api/v1/matches/123")

            assertEquals(HttpStatusCode.OK, hit.status)
            assertContentEquals(miss.bodyAsBytes(), hit.bodyAsBytes())
            assertEquals(1, service.calls.get())
        }
    }

    @Test
    fun `module with the cache turned off calls the service for every request`() {
        val service = CountingMatchesService()
        testApplication {
            application {
                module(
                    matchesService = service,
                    responseCacheConfig = ResponseCacheConfig.fromEnvironment(mapOf("VLRGG_RESPONSE_CACHE_ENABLED" to "false")),
                )
            }

            assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/123").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/matches/123").status)
            assertEquals(2, service.calls.get())
        }
    }

    @Test
    fun `server start is refused for malformed response cache settings`() {
        listOf(
            mapOf("VLRGG_RESPONSE_CACHE_ENABLED" to "on"),
            mapOf("VLRGG_RESPONSE_CACHE_MAX_BYTES" to "0"),
        ).forEach { environment ->
            assertFailsWith<IllegalArgumentException> {
                testApplication {
                    application { module(responseCacheConfig = ResponseCacheConfig.fromEnvironment(environment)) }
                    client.get("/health")
                }
            }
        }
    }

    private class CountingMatchesService : MatchesService {
        val calls = AtomicInteger()

        override suspend fun getMatches(category: MatchListCategory, page: Int): MatchesPageResponse {
            calls.incrementAndGet()
            return MatchesPageResponse(category, page, emptyList())
        }

        override suspend fun getMatch(matchId: String): MatchDetailResponse {
            calls.incrementAndGet()
            return MatchDetailResponse(
                id = matchId,
                status = MatchStatus.UPCOMING,
                timeLabel = "5:00 PM",
                homeTeam = MatchTeamResponse("Alpha"),
                awayTeam = MatchTeamResponse("Beta"),
                event = MatchEventResponse("Event"),
                maps = emptyList(),
                headToHead = emptyList(),
                pastMatches = emptyList(),
            )
        }
    }

    private suspend fun ApplicationTestBuilder.assertError(
        path: String,
        expectedStatus: HttpStatusCode,
        expectedCode: ApiErrorCode,
        expectedMessage: String,
    ) {
        val response = client.get(path)
        val body = response.bodyAsText()
        val error = Json.decodeFromString<ApiErrorResponse>(body)

        assertEquals(expectedStatus, response.status)
        assertEquals(ApiErrorResponse(expectedCode, expectedMessage), error)
        assertFalse(body.contains("private"))
        assertFalse(body.contains("selector"))
        assertFalse(body.contains("vlr.gg"))
    }

    private fun Application.installFailureRoutes() {
        routing {
            get("/test/invalid") {
                throw InvalidInputFailure()
            }
            get("/test/network") {
                throw UpstreamNetworkFailure()
            }
            get("/test/parsing") {
                throw SourceParsingFailure(
                    cause = IllegalStateException("selector details"),
                )
            }
            get("/test/internal") {
                throw IllegalStateException("private exception details")
            }
        }
    }
}

package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.RateLimitedFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerBusyFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class PublicRequestProtectionTest {
    @Test
    fun `unknown routes are rate limited while health remains exempt`() = testApplication {
        application {
            module(
                protectionConfig = PublicApiProtectionConfig(
                    apiRequestsPerSecond = 1,
                    apiBurst = 1,
                    maxActiveApiRequests = 1,
                    upstreamRequestsPerSecond = 2,
                    upstreamBurst = 4,
                    maxActiveUpstreamRequests = 4,
                    maxInFlightCanonicalUrls = 4,
                ),
            )
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/unknown").status)
        val limited = client.get("/another-unknown")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("1", limited.headers[HttpHeaders.RetryAfter])
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

    @Test
    fun `actual GET body is bounded before an unknown route is handled`() = testApplication {
        application {
            module(
                protectionConfig = PublicApiProtectionConfig(maxRequestBodyBytes = 1),
            )
        }

        val response = client.get("/unknown") { setBody("xx") }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `oversized public JSON is rejected before a success header is committed`() = testApplication {
        application {
            module()
            routing {
                get("/test/large-json") {
                    call.respondPublicJson(LargeResponse("x".repeat(MAX_PUBLIC_JSON_BYTES)))
                }
            }
        }

        assertEquals(HttpStatusCode.BadGateway, client.get("/test/large-json").status)
    }

    @Test
    fun `capped public JSON preserves a complete successful serializer payload`() = testApplication {
        val fixture = PublicJsonFixture(id = "fixture-1", nullable = null, labels = listOf("alpha", "beta"))
        application {
            module()
            routing {
                get("/test/json-fixture") {
                    call.respondPublicJson(fixture)
                }
            }
        }

        val response = client.get("/test/json-fixture")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Json.encodeToString(fixture), response.bodyAsText())
    }

    @Test
    fun `StatusPages outcomes are counted once with their stable failure codes`() = testApplication {
        val observability = PublicApiObservability()
        application {
            configureObservedPublicProtection(observability)
            routing {
                get("/observed/ok") { call.respondText("ok") }
                get("/observed/rate-limited") { throw RateLimitedFailure() }
                get("/observed/busy") { throw ServerBusyFailure() }
                get("/observed/upstream") { throw UpstreamNetworkFailure(Url("https://www.vlr.gg/fixture")) }
                get("/observed/exception") { throw IllegalStateException("fixture exception") }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/observed/ok").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/observed/unknown").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/observed/rate-limited").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/observed/busy").status)
        assertEquals(HttpStatusCode.BadGateway, client.get("/observed/upstream").status)
        assertEquals(HttpStatusCode.InternalServerError, client.get("/observed/exception").status)

        val snapshot = observability.snapshot()
        assertEquals(6, snapshot.requests)
        assertEquals(1, snapshot.statusClasses[2])
        assertEquals(2, snapshot.statusClasses[4])
        assertEquals(3, snapshot.statusClasses[5])
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.RATE_LIMITED))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.SERVER_BUSY))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.UPSTREAM_NETWORK_FAILURE))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.INTERNAL_ERROR))
        assertEquals(1, snapshot.upstreamFailures)
    }

    private fun Application.configureObservedPublicProtection(observability: PublicApiObservability) {
        val config = PublicApiProtectionConfig(
            apiRequestsPerSecond = 100,
            apiBurst = 20,
            maxActiveApiRequests = 8,
            upstreamRequestsPerSecond = 10,
            upstreamBurst = 4,
            maxActiveUpstreamRequests = 4,
            maxInFlightCanonicalUrls = 4,
        )
        configureSerialization()
        configureErrorHandling(observability)
        configurePublicRequestProtection(createPublicApiProtection(config, observability), config)
    }

    @Serializable
    private data class LargeResponse(val value: String)

    @Serializable
    private data class PublicJsonFixture(
        val id: String,
        val nullable: String?,
        val labels: List<String>,
    )
}

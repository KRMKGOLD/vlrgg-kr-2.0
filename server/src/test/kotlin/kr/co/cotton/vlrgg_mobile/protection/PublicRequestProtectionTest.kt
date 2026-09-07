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
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger
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

        val response = client.get("/test/large-json")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            ApiErrorCode.RESPONSE_TOO_LARGE.name,
            error.getValue("code").jsonPrimitive.content,
        )
        assertEquals("Response data is too large.", error.getValue("message").jsonPrimitive.content)
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
                get("/observed/parsing") { throw SourceParsingFailure(Url("https://www.vlr.gg/fixture"), IllegalStateException()) }
                get("/observed/large-json") { call.respondPublicJson(LargeResponse("x".repeat(MAX_PUBLIC_JSON_BYTES))) }
                get("/observed/exception") { throw IllegalStateException("fixture exception") }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/observed/ok").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/observed/unknown").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/observed/rate-limited").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/observed/busy").status)
        assertEquals(HttpStatusCode.BadGateway, client.get("/observed/upstream").status)
        assertEquals(HttpStatusCode.BadGateway, client.get("/observed/parsing").status)
        val oversized = client.get("/observed/large-json")
        assertEquals(HttpStatusCode.BadGateway, oversized.status)
        assertEquals(
            ApiErrorCode.RESPONSE_TOO_LARGE.name,
            Json.parseToJsonElement(oversized.bodyAsText()).jsonObject.getValue("code").jsonPrimitive.content,
        )
        assertEquals(HttpStatusCode.InternalServerError, client.get("/observed/exception").status)

        val snapshot = observability.snapshot()
        assertEquals(8, snapshot.requests)
        assertEquals(1, snapshot.statusClasses[2])
        assertEquals(2, snapshot.statusClasses[4])
        assertEquals(5, snapshot.statusClasses[5])
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.RATE_LIMITED))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.SERVER_BUSY))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.UPSTREAM_NETWORK_FAILURE))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.SOURCE_PARSING_FAILURE))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.RESPONSE_TOO_LARGE))
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.INTERNAL_ERROR))
        assertEquals(2, snapshot.upstreamFailures)
    }

    @Test
    fun `repeated headers exceed legacy accounting before handler or upstream work`() {
        val normal = repeatedHeaderProbe(maxRequestHeaderBytes = 16 * 1024)
        val correctBytes = assertNotNull(normal.headerBytes)
        assertEquals(200, normal.status)
        assertEquals(2, normal.repeatedHeaderValues)
        assertEquals(1, normal.handlerCalls)
        assertEquals(1, normal.upstreamCalls)

        val legacyGroupedBytes = correctBytes - HttpHeaders.Date.toByteArray().size
        assertTrue(legacyGroupedBytes < correctBytes)

        val rejected = repeatedHeaderProbe(maxRequestHeaderBytes = legacyGroupedBytes.toInt())
        assertEquals(431, rejected.status)
        assertEquals(ApiErrorCode.REQUEST_TOO_LARGE.name, rejected.errorCode)
        assertEquals(0, rejected.handlerCalls)
        assertEquals(0, rejected.upstreamCalls)

        val exactBoundary = repeatedHeaderProbe(maxRequestHeaderBytes = correctBytes.toInt())
        assertEquals(200, exactBoundary.status)
        assertEquals(1, exactBoundary.handlerCalls)
        assertEquals(1, exactBoundary.upstreamCalls)
    }

    private fun repeatedHeaderProbe(maxRequestHeaderBytes: Int): HeaderProbe {
        lateinit var result: HeaderProbe
        testApplication {
            val handlerCalls = AtomicInteger()
            val upstreamCalls = AtomicInteger()
            val fakeUpstream = object : UpstreamHtmlTransport {
                override suspend fun get(url: Url): String {
                    upstreamCalls.incrementAndGet()
                    return "html"
                }
            }
            var headerBytes: Long? = null
            var repeatedHeaderValues: Int? = null
            application {
                configureObservedPublicProtection(
                    PublicApiObservability(),
                    PublicApiProtectionConfig(maxRequestHeaderBytes = maxRequestHeaderBytes),
                )
                routing {
                    get("/observed/repeated-header") {
                        headerBytes = call.request.headers.publicHeaderBytes()
                        repeatedHeaderValues = call.request.headers.getAll(HttpHeaders.Date)?.size
                        handlerCalls.incrementAndGet()
                        fakeUpstream.get(Url("https://www.vlr.gg/fixture"))
                        call.respondText("ok")
                    }
                }
            }

            val response = client.get("/observed/repeated-header") {
                headers {
                    // Ktor emits repeated date headers as separate HTTP fields rather than comma-joining them.
                    append(HttpHeaders.Date, "Tue, 01 Jan 2030 00:00:00 GMT")
                    append(HttpHeaders.Date, "Wed, 02 Jan 2030 00:00:00 GMT")
                }
            }
            val body = response.bodyAsText()
            result = HeaderProbe(
                status = response.status.value,
                errorCode = if (response.status.value == 431) {
                    Json.parseToJsonElement(body).jsonObject.getValue("code").jsonPrimitive.content
                } else {
                    null
                },
                headerBytes = headerBytes,
                repeatedHeaderValues = repeatedHeaderValues,
                handlerCalls = handlerCalls.get(),
                upstreamCalls = upstreamCalls.get(),
            )
        }
        return result
    }

    private data class HeaderProbe(
        val status: Int,
        val errorCode: String?,
        val headerBytes: Long?,
        val repeatedHeaderValues: Int?,
        val handlerCalls: Int,
        val upstreamCalls: Int,
    )

    private fun Application.configureObservedPublicProtection(
        observability: PublicApiObservability,
        config: PublicApiProtectionConfig = PublicApiProtectionConfig(
            apiRequestsPerSecond = 100,
            apiBurst = 20,
            maxActiveApiRequests = 8,
            upstreamRequestsPerSecond = 10,
            upstreamBurst = 4,
            maxActiveUpstreamRequests = 4,
            maxInFlightCanonicalUrls = 4,
        ),
    ) {
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

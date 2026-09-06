package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kotlin.test.*

class AcceptanceRequestContractTest {
    @Test
    fun `429 and 503 use safe envelopes with retry after while health stays available under API saturation`() = testApplication {
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        application {
            module(
                protectionConfig = PublicApiProtectionConfig(
                    apiRequestsPerSecond = 100,
                    apiBurst = 32,
                    maxActiveApiRequests = 1,
                    upstreamRequestsPerSecond = 10,
                    upstreamBurst = 4,
                    maxActiveUpstreamRequests = 4,
                    maxInFlightCanonicalUrls = 4,
                ),
            )
            routing {
                get("/acceptance/hold") {
                    held.complete(Unit)
                    release.await()
                    call.respondText("released")
                }
            }
        }

        coroutineScope {
            val first = async(Dispatchers.Default) { client.get("/acceptance/hold") }
            held.await()
            assertError(client.get("/acceptance/hold"), HttpStatusCode.ServiceUnavailable, ApiErrorCode.SERVER_BUSY, "2")
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
            release.complete(Unit)
            assertEquals(HttpStatusCode.OK, first.await().status)
        }
    }

    @Test
    fun `429 uses the rate limit envelope`() = testApplication {
        application {
            module(
                protectionConfig = PublicApiProtectionConfig(
                    apiRequestsPerSecond = 1,
                    apiBurst = 1,
                    maxActiveApiRequests = 8,
                    upstreamRequestsPerSecond = 10,
                    upstreamBurst = 4,
                    maxActiveUpstreamRequests = 4,
                    maxInFlightCanonicalUrls = 4,
                ),
            )
        }

        assertError(client.get("/unknown"), HttpStatusCode.NotFound, ApiErrorCode.NOT_FOUND, null)
        assertError(client.get("/another-unknown"), HttpStatusCode.TooManyRequests, ApiErrorCode.RATE_LIMITED, "1")
    }

    @Test
    fun `whole request deadline returns safe 504 envelope`() = testApplication {
        application {
            module(
                protectionConfig = PublicApiProtectionConfig(
                    apiRequestsPerSecond = 100,
                    apiBurst = 32,
                    maxActiveApiRequests = 8,
                    upstreamRequestsPerSecond = 10,
                    upstreamBurst = 4,
                    maxActiveUpstreamRequests = 4,
                    maxInFlightCanonicalUrls = 4,
                    wholeRequestTimeoutMillis = 20,
                ),
            )
            routing {
                get("/acceptance/deadline") {
                    delay(100)
                    call.respondText("must not complete")
                }
            }
        }

        assertError(client.get("/acceptance/deadline"), HttpStatusCode.GatewayTimeout, ApiErrorCode.REQUEST_TIMEOUT, null)
    }

    @Test
    fun `target header and declared body caps reject before route success`() = testApplication {
        application {
            module(
                protectionConfig = PublicApiProtectionConfig(
                    apiRequestsPerSecond = 100,
                    apiBurst = 32,
                    maxActiveApiRequests = 8,
                    upstreamRequestsPerSecond = 10,
                    upstreamBurst = 4,
                    maxActiveUpstreamRequests = 4,
                    maxInFlightCanonicalUrls = 4,
                    maxRequestTargetBytes = 16,
                    maxRequestHeaderBytes = 512,
                    maxRequestBodyBytes = 2,
                ),
            )
        }

        assertError(client.get("/acceptance-target-too-long"), HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST, null)
        assertError(
            client.post("/body") { setBody("123") },
            HttpStatusCode.PayloadTooLarge,
            ApiErrorCode.REQUEST_TOO_LARGE,
            null,
        )
        assertError(
            client.get("/header") { header("X-Acceptance-Header", "x".repeat(1_024)) },
            HttpStatusCode(431, "Request Header Fields Too Large"),
            ApiErrorCode.REQUEST_TOO_LARGE,
            null,
        )
    }

    private suspend fun assertError(
        response: io.ktor.client.statement.HttpResponse,
        status: HttpStatusCode,
        code: ApiErrorCode,
        retryAfter: String?,
    ) {
        assertEquals(status, response.status)
        assertEquals(retryAfter, response.headers[HttpHeaders.RetryAfter])
        val envelope = Json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(code, envelope.code)
        assertTrue(envelope.message.isNotBlank())
        assertFalse(envelope.message.contains("acceptance", ignoreCase = true))
    }
}

package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.module
import kotlinx.serialization.Serializable
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

    @Serializable
    private data class LargeResponse(val value: String)
}

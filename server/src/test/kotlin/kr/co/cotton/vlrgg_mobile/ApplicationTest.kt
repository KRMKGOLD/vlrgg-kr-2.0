package kr.co.cotton.vlrgg_mobile

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
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
                throw UpstreamNetworkFailure("https://www.vlr.gg/private")
            }
            get("/test/parsing") {
                throw SourceParsingFailure(
                    canonicalUpstreamUrl = "https://www.vlr.gg/private",
                    cause = IllegalStateException("selector details"),
                )
            }
            get("/test/internal") {
                throw IllegalStateException("private exception details")
            }
        }
    }
}

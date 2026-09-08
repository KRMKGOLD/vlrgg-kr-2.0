package kr.co.cotton.vlrgg_mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HeadersBuilder
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.data.remote.PublicApiBusyException
import kr.co.cotton.vlrgg_mobile.data.remote.PublicApiResponseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PublicApiJsonTest {

    @Test
    fun perCallOverrideMapsRecognized429And503ToBusy() = runTest {
        listOf(
            HttpStatusCode.TooManyRequests to "RATE_LIMITED",
            HttpStatusCode.ServiceUnavailable to "SERVER_BUSY",
        ).forEach { (status, code) ->
            val client = createClient(
                MockEngine {
                    respondJson(
                        content = """{"code":"$code","message":"safe"}""",
                        status = status,
                        retryAfter = "1",
                    )
                },
            )

            try {
                val failure = assertFailsWith<PublicApiBusyException> {
                    client.getPublicJson<String>("/api/v1/test")
                }

                assertEquals(1.seconds, failure.retryDelay)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun retryAfterOnlyAcceptsIntegerSecondsFromOneThroughSixty() = runTest {
        listOf(
            "1" to 1.seconds,
            "60" to 60.seconds,
            null to 2.seconds,
            "0" to 2.seconds,
            "-1" to 2.seconds,
            "1.5" to 2.seconds,
            "61" to 2.seconds,
        ).forEach { (header, expectedDelay) ->
            val client = createClient(
                MockEngine {
                    respondJson(
                        content = """{"code":"RATE_LIMITED"}""",
                        status = HttpStatusCode.TooManyRequests,
                        retryAfter = header,
                    )
                },
            )

            try {
                val failure = assertFailsWith<PublicApiBusyException> {
                    client.getPublicJson<String>("/api/v1/test")
                }
                assertEquals(expectedDelay, failure.retryDelay)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun oversizedEnvelopeStaysGenericAndCancelsAfterOnlyTheBoundedSentinelRead() = runTest {
        val body = CancellationTrackingChannel(
            """{"code":"RATE_LIMITED"}""" + " ".repeat(8 * 1024 + 1),
        )
        val client = createClient(
            MockEngine {
                respond(
                    content = body,
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                )
            },
        )

        try {
            assertFailsWith<PublicApiResponseException> {
                client.getPublicJson<String>("/api/v1/test")
            }
            assertTrue(body.wasCancelled)
        } finally {
            client.close()
        }
    }

    @Test
    fun unrecognizedStatusAndMalformedEnvelopeStayGeneric() = runTest {
        listOf(
            HttpStatusCode.BadGateway to """{"code":"UPSTREAM_NETWORK_FAILURE"}""",
            HttpStatusCode.ServiceUnavailable to "<html>platform busy</html>",
            HttpStatusCode.TooManyRequests to "{not-json}",
        ).forEach { (status, content) ->
            val client = createClient(MockEngine { respondJson(content, status) })

            try {
                assertFailsWith<PublicApiResponseException> {
                    client.getPublicJson<String>("/api/v1/test")
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun cancellationIsPropagated() = runTest {
        val cancellation = CancellationException("cancelled")
        val client = createClient(MockEngine { throw cancellation })

        try {
            val thrown = assertFailsWith<CancellationException> {
                client.getPublicJson<String>("/api/v1/test")
            }
            assertIs<CancellationException>(thrown)
        } finally {
            client.close()
        }
    }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        // The helper must defeat this global default for only its own request.
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            url(TEST_BASE_URL)
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode,
        retryAfter: String? = null,
    ) = respond(
        content = content,
        status = status,
        headers = HeadersBuilder().apply {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            retryAfter?.let { append(HttpHeaders.RetryAfter, it) }
        }.build(),
    )

    private class CancellationTrackingChannel(
        content: String,
        private val backingChannel: ByteChannel = ByteChannel(autoFlush = true),
    ) : ByteReadChannel by backingChannel {
        var wasCancelled = false
            private set

        init {
            runBlocking<Unit> {
                backingChannel.writeFully(content.encodeToByteArray())
            }
        }

        override fun cancel(cause: Throwable?) {
            wasCancelled = true
            backingChannel.cancel(cause)
        }

    }

    private companion object {
        const val TEST_BASE_URL = "https://example.invalid"
    }
}

package kr.co.cotton.vlrgg_mobile.common.scraping

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kotlin.test.*

class UpstreamHtmlTransportTest {

    @Test
    fun `transport sends explicit user agent and returns successful HTML`() = withTransport(
        engine = MockEngine { request ->
            assertEquals("VLR.GG-Mobile/test", request.headers[HttpHeaders.UserAgent])
            respond(
                content = ByteReadChannel("<html>ok</html>"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        },
        config = UpstreamHtmlTransportConfig(userAgent = "VLR.GG-Mobile/test"),
    ) { transport ->
        assertEquals("<html>ok</html>", transport.get(UPSTREAM_URL))
    }

    @Test
    fun `redirects map to upstream failure without another request`() {
        var requestCount = 0
        withTransport(
            engine = MockEngine {
                requestCount += 1
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://untrusted.example/redirect"),
                )
            },
        ) { transport ->
            val failure = assertFailsWith<UpstreamNetworkFailure> {
                runBlocking { transport.get(UPSTREAM_URL) }
            }

            assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
            assertIs<Exception>(failure.cause)
        }
        assertEquals(1, requestCount)
    }

    @Test
    fun `non successful responses map to upstream failure`() = withTransport(
        engine = MockEngine {
            respond(ByteReadChannel.Empty, HttpStatusCode.ServiceUnavailable)
        },
    ) { transport ->
        assertFailsWith<UpstreamNetworkFailure> {
            runBlocking { transport.get(UPSTREAM_URL) }
        }
    }

    @Test
    fun `response body larger than the configured limit maps safely`() = withTransport(
        engine = MockEngine {
            respond(ByteReadChannel("12345"), HttpStatusCode.OK)
        },
        config = UpstreamHtmlTransportConfig(maxResponseBodyBytes = 4),
    ) { transport ->
        val failure = assertFailsWith<UpstreamNetworkFailure> {
            runBlocking { transport.get(UPSTREAM_URL) }
        }

        assertIs<UpstreamResponseBodyTooLargeException>(failure.cause)
    }

    @Test
    fun `cancellation and fatal errors are preserved`() {
        withTransport(MockEngine { throw CancellationException("cancel") }) { transport ->
            assertFailsWith<CancellationException> {
                runBlocking { transport.get(UPSTREAM_URL) }
            }
        }
        withTransport(MockEngine { throw AssertionError("fatal") }) { transport ->
            assertFailsWith<AssertionError> {
                runBlocking { transport.get(UPSTREAM_URL) }
            }
        }
    }

    @Test
    fun `composition root reuses one non closeable transport and closes it on shutdown`() {
        var requestCount = 0
        val engine = MockEngine {
            requestCount += 1
            respond(ByteReadChannel("ok"), HttpStatusCode.OK)
        }
        lateinit var transport: UpstreamHtmlTransport

        testApplication {
            application {
                transport = createUpstreamHtmlTransportForTesting(engine)
                assertSame(transport, createUpstreamHtmlTransportForTesting(engine))
                assertFalse(transport is AutoCloseable)
            }

            startApplication()
            assertEquals("ok", transport.get(UPSTREAM_URL))
        }

        assertFailsWith<CancellationException> {
            runBlocking { transport.get(UPSTREAM_URL) }
        }
        assertEquals(1, requestCount)
    }

    private fun withTransport(
        engine: MockEngine,
        config: UpstreamHtmlTransportConfig = UpstreamHtmlTransportConfig(),
        block: suspend (UpstreamHtmlTransport) -> Unit,
    ) = testApplication {
        lateinit var transport: UpstreamHtmlTransport
        application {
            transport = createUpstreamHtmlTransportForTesting(engine, config)
        }

        startApplication()
        block(transport)
    }

    private companion object {
        val UPSTREAM_URL = Url("https://www.vlr.gg/")
    }
}

package kr.co.cotton.vlrgg_mobile.common.scraping

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.http.toSafeCanonicalUpstreamUrl

private const val DEFAULT_USER_AGENT = "VLR.GG-Mobile/2.0"
private const val DEFAULT_MAX_RESPONSE_BODY_BYTES = 1_048_576
private const val RESPONSE_READ_BUFFER_SIZE = 8_192

internal data class UpstreamHtmlTransportConfig(
    val userAgent: String = DEFAULT_USER_AGENT,
    val connectTimeoutMillis: Long = 5_000,
    val requestTimeoutMillis: Long = 10_000,
    val socketTimeoutMillis: Long = 10_000,
    val maxResponseBodyBytes: Int = DEFAULT_MAX_RESPONSE_BODY_BYTES,
) {
    init {
        require(userAgent.isNotBlank()) { "User-Agent must not be blank." }
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive." }
        require(requestTimeoutMillis > 0) { "Request timeout must be positive." }
        require(socketTimeoutMillis > 0) { "Socket timeout must be positive." }
        require(maxResponseBodyBytes > 0) { "Response body limit must be positive." }
    }
}

/** Feature modules receive this non-closeable contract from the application composition root. */
internal interface UpstreamHtmlTransport {
    suspend fun get(url: Url): String
}

/** Kept internal so a body-size overflow is observable in server logs but never returned to clients. */
internal class UpstreamResponseBodyTooLargeException : Exception()

private class UnexpectedUpstreamResponseStatusException : Exception()

private class KtorUpstreamHtmlTransport(
    private val client: HttpClient,
    private val maxResponseBodyBytes: Int,
) : UpstreamHtmlTransport {
    override suspend fun get(url: Url): String {
        val canonicalUrl = url.toSafeCanonicalUpstreamUrl()

        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                throw UpstreamNetworkFailure(canonicalUrl, UnexpectedUpstreamResponseStatusException())
            }
            response.bodyAsBoundedText(maxResponseBodyBytes)
        } catch (failure: UpstreamNetworkFailure) {
            throw failure
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw UpstreamNetworkFailure(canonicalUrl, exception)
        }
    }

    fun close() {
        client.close()
    }
}

private val upstreamHtmlTransportKey = AttributeKey<KtorUpstreamHtmlTransport>("upstream-html-transport")

/**
 * Creates exactly one reusable transport for an application and registers its cleanup once.
 * The returned contract has no close operation, so only the application lifecycle owns the client.
 */
internal fun Application.createUpstreamHtmlTransport(
    config: UpstreamHtmlTransportConfig = UpstreamHtmlTransportConfig(),
): UpstreamHtmlTransport = getOrCreateUpstreamHtmlTransport {
    KtorUpstreamHtmlTransport(
        client = createUpstreamHttpClient(config),
        maxResponseBodyBytes = config.maxResponseBodyBytes,
    )
}

/** Internal test seam that preserves production client configuration while replacing only the engine. */
internal fun Application.createUpstreamHtmlTransportForTesting(
    engine: HttpClientEngine,
    config: UpstreamHtmlTransportConfig = UpstreamHtmlTransportConfig(),
): UpstreamHtmlTransport = getOrCreateUpstreamHtmlTransport {
    KtorUpstreamHtmlTransport(
        client = createUpstreamHttpClient(config, engine),
        maxResponseBodyBytes = config.maxResponseBodyBytes,
    )
}

private fun Application.getOrCreateUpstreamHtmlTransport(
    factory: () -> KtorUpstreamHtmlTransport,
): UpstreamHtmlTransport {
    attributes.getOrNull(upstreamHtmlTransportKey)?.let { return it }

    return factory().also { transport ->
        attributes.put(upstreamHtmlTransportKey, transport)
        monitor.subscribe(ApplicationStopping) {
            transport.close()
        }
    }
}

private fun createUpstreamHttpClient(config: UpstreamHtmlTransportConfig): HttpClient = HttpClient(CIO) {
    configureUpstreamTransport(config)
}

private fun createUpstreamHttpClient(
    config: UpstreamHtmlTransportConfig,
    engine: HttpClientEngine,
): HttpClient = HttpClient(engine) {
    configureUpstreamTransport(config)
}

private fun HttpClientConfig<*>.configureUpstreamTransport(config: UpstreamHtmlTransportConfig) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        requestTimeoutMillis = config.requestTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, config.userAgent)
    }
}

@OptIn(InternalAPI::class)
private suspend fun HttpResponse.bodyAsBoundedText(maxResponseBodyBytes: Int): String {
    val declaredLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declaredLength != null && declaredLength > maxResponseBodyBytes) {
        throw UpstreamResponseBodyTooLargeException()
    }

    val output = ByteArrayOutputStream()
    val buffer = ByteArray(minOf(RESPONSE_READ_BUFFER_SIZE, maxResponseBodyBytes))
    val channel = bodyAsChannel()
    var totalBytes = 0

    while (true) {
        val read = channel.readAvailable(buffer)
        if (read == -1) {
            break
        }
        if (read == 0) {
            continue
        }
        if (read > maxResponseBodyBytes - totalBytes) {
            throw UpstreamResponseBodyTooLargeException()
        }

        output.write(buffer, 0, read)
        totalBytes += read
    }

    channel.rethrowCloseCauseIfNeeded()
    return String(output.toByteArray(), charset() ?: Charsets.UTF_8)
}

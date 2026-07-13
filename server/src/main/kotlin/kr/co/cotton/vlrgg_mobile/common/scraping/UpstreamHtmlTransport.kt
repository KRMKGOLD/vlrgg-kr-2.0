package kr.co.cotton.vlrgg_mobile.common.scraping

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure

private const val DEFAULT_USER_AGENT = "VLR.GG-Mobile/2.0"

internal data class UpstreamHtmlTransportConfig(
    val userAgent: String = DEFAULT_USER_AGENT,
    val connectTimeoutMillis: Long = 5_000,
    val requestTimeoutMillis: Long = 10_000,
    val socketTimeoutMillis: Long = 10_000,
) {
    init {
        require(userAgent.isNotBlank()) { "User-Agent must not be blank." }
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive." }
        require(requestTimeoutMillis > 0) { "Request timeout must be positive." }
        require(socketTimeoutMillis > 0) { "Socket timeout must be positive." }
    }
}

/**
 * Fetches a single upstream HTML document per invocation.
 *
 * It owns its [HttpClient] and is closed through [Application.createUpstreamHtmlTransport].
 * Retries are deliberately not installed: a feature can decide a bounded retry policy later.
 */
internal interface UpstreamHtmlTransport : AutoCloseable {
    suspend fun get(url: Url): String
}

internal class KtorUpstreamHtmlTransport private constructor(
    private val client: HttpClient,
) : UpstreamHtmlTransport {
    override suspend fun get(url: Url): String {
        val canonicalUrl = url.toCanonicalUrl()

        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                throw UpstreamNetworkFailure(canonicalUrl)
            }
            response.bodyAsText()
        } catch (failure: UpstreamNetworkFailure) {
            throw failure
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Throwable) {
            throw UpstreamNetworkFailure(canonicalUrl, cause)
        }
    }

    override fun close() {
        client.close()
    }

    internal companion object {
        fun create(config: UpstreamHtmlTransportConfig = UpstreamHtmlTransportConfig()): KtorUpstreamHtmlTransport =
            KtorUpstreamHtmlTransport(
                client = HttpClient(CIO) {
                    expectSuccess = false
                    install(HttpTimeout) {
                        connectTimeoutMillis = config.connectTimeoutMillis
                        requestTimeoutMillis = config.requestTimeoutMillis
                        socketTimeoutMillis = config.socketTimeoutMillis
                    }
                    defaultRequest {
                        header(HttpHeaders.UserAgent, config.userAgent)
                    }
                },
            )
    }
}

/** Creates one reusable transport and binds its lifecycle to this application. */
internal fun Application.createUpstreamHtmlTransport(
    config: UpstreamHtmlTransportConfig = UpstreamHtmlTransportConfig(),
): UpstreamHtmlTransport {
    val transport = KtorUpstreamHtmlTransport.create(config)
    monitor.subscribe(ApplicationStopping) {
        transport.close()
    }
    return transport
}

private fun Url.toCanonicalUrl(): String = buildString {
    append(protocol.name)
    append("://")
    append(host)
    if (port != protocol.defaultPort) {
        append(':')
        append(port)
    }
    append(encodedPath)
}

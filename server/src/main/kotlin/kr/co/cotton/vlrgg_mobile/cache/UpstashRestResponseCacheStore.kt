package kr.co.cotton.vlrgg_mobile.cache

import io.ktor.server.application.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.asDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/**
 * Leak backstops only; the cache wrapper bounds every lookup and store to 200 ms. Slightly longer so the
 * wrapper's own timeout, not the client's, decides the outcome.
 */
internal val UPSTASH_CONNECT_TIMEOUT: Duration = Duration.ofMillis(1_000)
internal val UPSTASH_REQUEST_TIMEOUT: Duration = Duration.ofMillis(1_000)

/** A stored body is at most 2 MiB of JSON; escaping inside the REST envelope can grow it, so allow headroom. */
private const val MAX_UPSTASH_RESPONSE_BYTES = 8 * 1024 * 1024

/**
 * Upstash Redis REST client: one JSON command array per `POST /`, answered by `{"result": ...}` or `{"error": ...}`.
 * Upstash error strings can echo command arguments, so they are reduced to a fixed [ResponseCacheFailureKind].
 *
 * It uses the JDK client because Ktor CIO 3.5.0 sends every non-GET request (and any request with connect or
 * socket timeouts) on a dedicated connection that closes after the response, so a POST would pay a new TCP and
 * TLS handshake each time. The JDK client pools connections, including for POST.
 */
internal class UpstashRestResponseCacheStore(
    private val client: HttpClient,
    restUri: URI,
    private val token: String,
    private val requestTimeout: Duration = UPSTASH_REQUEST_TIMEOUT,
) : ResponseCacheStore, AutoCloseable {
    private val endpoint: URI = restUri.resolve("/")

    override suspend fun get(key: String): String? {
        val result = execute(buildJsonArray { add("GET"); add(key) })
        return when {
            result is JsonNull -> null
            result is JsonPrimitive && result.isString -> result.content
            else -> throw ResponseCacheStoreException(ResponseCacheFailureKind.DECODE)
        }
    }

    override suspend fun set(key: String, value: String, ttlSeconds: Int, onlyIfAbsent: Boolean): Boolean {
        val command = buildJsonArray {
            add("SET"); add(key); add(value); add("EX"); add(ttlSeconds.toString())
            if (onlyIfAbsent) add("NX")
        }
        val result = execute(command)
        return when {
            result is JsonPrimitive && result.isString && result.content == "OK" -> true
            // `NX` on an existing key answers nil: not stored, but not a failure.
            result is JsonNull && onlyIfAbsent -> false
            else -> throw ResponseCacheStoreException(ResponseCacheFailureKind.DECODE)
        }
    }

    private suspend fun execute(command: JsonArray): JsonElement {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(command.toString(), Charsets.UTF_8))
            .build()
        val response = send(request)
        if (response.statusCode() != 200) throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP)
        val body = response.body() ?: throw ResponseCacheStoreException(ResponseCacheFailureKind.DECODE)
        val envelope = try {
            Json.parseToJsonElement(body.toString(Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw ResponseCacheStoreException(ResponseCacheFailureKind.DECODE)
        if (envelope.containsKey("error")) throw ResponseCacheStoreException(ResponseCacheFailureKind.HTTP)
        return envelope["result"] ?: throw ResponseCacheStoreException(ResponseCacheFailureKind.DECODE)
    }

    /**
     * Coroutine cancellation, including the wrapper's own timeout, aborts the in-flight exchange.
     * `CompletionStage.await()` would first call `cancel(false)` on this future, which completes it without
     * aborting the JDK exchange; waiting through [asDeferred] leaves the original future pending so that
     * `cancel(true)` reaches the exchange and closes its connection.
     */
    private suspend fun send(request: HttpRequest): HttpResponse<ByteArray?> {
        val future = client.sendAsync(request, BoundedBodyHandler)
        return try {
            future.asDeferred().await()
        } catch (cancellation: CancellationException) {
            future.cancel(true)
            throw cancellation
        } catch (_: HttpTimeoutException) {
            throw ResponseCacheStoreException(ResponseCacheFailureKind.TIMEOUT)
        } catch (_: Exception) {
            throw ResponseCacheStoreException(ResponseCacheFailureKind.IO)
        }
    }

    /**
     * Cache calls are best-effort, so stopping aborts in-flight exchanges instead of waiting for them:
     * `HttpClient.close()` would block shutdown until every exchange finished.
     */
    override fun close() {
        client.shutdownNow()
    }
}

/**
 * Error bodies are discarded so the connection stays reusable. Success bodies are read up to the cap; a body
 * past it (declared or streamed) yields null, which the store reports as a decode failure.
 */
private object BoundedBodyHandler : HttpResponse.BodyHandler<ByteArray?> {
    override fun apply(info: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber<ByteArray?> {
        if (info.statusCode() != 200) return HttpResponse.BodySubscribers.replacing(null)
        val declared = info.headers().firstValueAsLong("Content-Length")
        return BoundedBodySubscriber(rejectImmediately = declared.isPresent && declared.asLong > MAX_UPSTASH_RESPONSE_BYTES)
    }
}

private class BoundedBodySubscriber(private val rejectImmediately: Boolean) : HttpResponse.BodySubscriber<ByteArray?> {
    private val result = CompletableFuture<ByteArray?>()
    private val output = ByteArrayOutputStream()
    private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletionStage<ByteArray?> = result

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        if (rejectImmediately) {
            subscription.cancel()
            result.complete(null)
        } else {
            subscription.request(Long.MAX_VALUE)
        }
    }

    override fun onNext(item: List<ByteBuffer>) {
        if (result.isDone) return
        for (buffer in item) {
            if (buffer.remaining() > MAX_UPSTASH_RESPONSE_BYTES - output.size()) {
                subscription?.cancel()
                result.complete(null)
                return
            }
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            output.write(bytes)
        }
    }

    override fun onError(throwable: Throwable) {
        result.completeExceptionally(throwable)
    }

    override fun onComplete() {
        result.complete(output.toByteArray())
    }
}

/** Creates the Upstash store from the validated HTTPS config and closes its client when the application stops. */
internal fun Application.createUpstashRestResponseCacheStore(config: ResponseCacheConfig.Enabled): ResponseCacheStore =
    closeOnStop(UpstashRestResponseCacheStore(createUpstashHttpClient(), URI(config.restUrl.toString()), config.token))

internal fun Application.closeOnStop(store: UpstashRestResponseCacheStore): UpstashRestResponseCacheStore =
    store.also { monitor.subscribe(ApplicationStopping) { store.close() } }

/**
 * One shared client, separate from the upstream transport. HTTP/2 is preferred (the JDK default) and redirects
 * are never followed. Idle pooled connections live for the JDK default (`jdk.httpclient.keepalive.timeout`,
 * 30 seconds on JDK 21, also used for HTTP/2); no system property is changed here.
 */
internal fun createUpstashHttpClient(): HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .followRedirects(HttpClient.Redirect.NEVER)
    .connectTimeout(UPSTASH_CONNECT_TIMEOUT)
    .build()

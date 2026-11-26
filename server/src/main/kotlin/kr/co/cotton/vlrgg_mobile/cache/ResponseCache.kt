package kr.co.cotton.vlrgg_mobile.cache

import io.ktor.server.application.*
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.SOURCE_PARSING_FAILURE_STATUS
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.protection.MAX_PUBLIC_JSON_BYTES
import org.slf4j.LoggerFactory

internal const val DEFAULT_RESPONSE_CACHE_OPERATION_TIMEOUT_MILLIS = 200L

/** A cached `SOURCE_PARSING_FAILURE` keeps only its status and code; the message is rebuilt from the failure type. */
internal val SOURCE_PARSING_FAILURE_CACHE_VALUE =
    "[${SOURCE_PARSING_FAILURE_STATUS.value},\"${ApiErrorCode.SOURCE_PARSING_FAILURE.name}\"]"

internal sealed interface CachedPublicResponse {
    /** The exact success JSON bytes that the miss path sent. */
    class Success(val body: ByteArray) : CachedPublicResponse

    data object SourceParsingFailure : CachedPublicResponse
}

/**
 * Policy wrapper around [ResponseCacheStore]. A store failure or timeout never fails the request: lookups become
 * misses and stores are dropped with a sampled WARN. Only cancellation of the calling request propagates.
 */
internal class ResponseCache(
    private val store: ResponseCacheStore?,
    private val deploySha: String,
    private val observability: PublicApiObservability? = null,
    private val warnings: ResponseCacheWarnings? = null,
    private val operationTimeoutMillis: Long = DEFAULT_RESPONSE_CACHE_OPERATION_TIMEOUT_MILLIS,
) {
    init {
        require(operationTimeoutMillis > 0)
    }

    val isEnabled: Boolean get() = store != null

    fun key(route: ResponseCacheRoute, vararg identifier: String): ResponseCacheKey =
        ResponseCacheKey.of(deploySha, route, identifier.toList())

    suspend fun lookup(key: ResponseCacheKey): CachedPublicResponse? {
        val store = store ?: return null
        val value = when (val attempt = attempt { store.get(key.value) }) {
            is Attempt.Done -> attempt.value
            is Attempt.Failed -> return lookupFailed(key, attempt.kind)
        }
        if (value == null) {
            count(ResponseCacheCounter.MISS)
            return null
        }
        if (value == SOURCE_PARSING_FAILURE_CACHE_VALUE) {
            count(ResponseCacheCounter.NEGATIVE_HIT)
            return CachedPublicResponse.SourceParsingFailure
        }
        // Success values are always JSON objects; anything else is a damaged or foreign entry and is never served.
        if (!value.startsWith("{")) return lookupFailed(key, ResponseCacheFailureKind.DECODE)
        val body = value.toByteArray(StandardCharsets.UTF_8)
        if (body.size > MAX_PUBLIC_JSON_BYTES) return lookupFailed(key, ResponseCacheFailureKind.DECODE)
        count(ResponseCacheCounter.HIT)
        return CachedPublicResponse.Success(body)
    }

    /** Stores a final success body. Callers finish this before responding because Cloud Run throttles CPU afterwards. */
    suspend fun store(key: ResponseCacheKey, json: String, ttlSeconds: Int) {
        require(ttlSeconds in 1..ResponseCacheTtl.MAX_SECONDS) { "Response cache TTL must be between 1 and 300 seconds." }
        val store = store ?: return
        set(key) { store.set(key.value, json, ttlSeconds, onlyIfAbsent = false) }
    }

    /** `NX` keeps a just-stored success from being overwritten by a concurrent parsing failure. */
    suspend fun storeSourceParsingFailure(key: ResponseCacheKey) {
        val store = store ?: return
        set(key) {
            store.set(key.value, SOURCE_PARSING_FAILURE_CACHE_VALUE, ResponseCacheTtl.SOURCE_PARSING_FAILURE_SECONDS, onlyIfAbsent = true)
        }
    }

    private suspend fun set(key: ResponseCacheKey, operation: suspend () -> Boolean) {
        val attempt = attempt(operation)
        if (attempt is Attempt.Failed) {
            count(ResponseCacheCounter.STORE_FAILED)
            warnings?.warn(ResponseCacheOperation.SET, attempt.kind, key.route)
        }
    }

    private fun lookupFailed(key: ResponseCacheKey, kind: ResponseCacheFailureKind): CachedPublicResponse? {
        count(ResponseCacheCounter.LOOKUP_FAILED)
        warnings?.warn(ResponseCacheOperation.GET, kind, key.route)
        return null
    }

    /**
     * Only this wrapper's own timeout ([withTimeoutOrNull]) and store failures become [Attempt.Failed].
     * Every other [CancellationException] propagates unchanged.
     */
    private suspend fun <T> attempt(operation: suspend () -> T): Attempt<T> = try {
        withTimeoutOrNull(operationTimeoutMillis) { Attempt.Done(operation()) }
            ?: Attempt.Failed(ResponseCacheFailureKind.TIMEOUT)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: ResponseCacheStoreException) {
        Attempt.Failed(failure.kind)
    } catch (_: Exception) {
        Attempt.Failed(ResponseCacheFailureKind.IO)
    }

    private fun count(counter: ResponseCacheCounter) {
        observability?.responseCache(counter)
    }

    private sealed interface Attempt<out T> {
        class Done<T>(val value: T) : Attempt<T>
        class Failed(val kind: ResponseCacheFailureKind) : Attempt<Nothing>
    }

    companion object {
        /** Route default: no store, no key work, no counters. */
        val DISABLED = ResponseCache(store = null, deploySha = "disabled")
    }
}

internal enum class ResponseCacheOperation(val label: String) { GET("get"), SET("set") }

/**
 * One-line JSON WARN with fixed fields only. Keys, parameters, request paths, the store URL or host, tokens and
 * exception messages are never written. Each operation emits at most [maxPerWindow] lines per minute; totals
 * live in the `public_api_summary` cache counters. Without `@type`, Error Reporting does not collect these lines.
 */
internal class ResponseCacheWarnings(
    environment: Map<String, String> = System.getenv(),
    private val observability: PublicApiObservability? = null,
    private val sink: (String) -> Unit = { cacheLogger.warn(it) },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxPerWindow: Int = MAX_WARNINGS_PER_WINDOW,
) {
    private val service = environment["K_SERVICE"] ?: "vlrgg-server-local"
    private val version = environment["K_REVISION"] ?: "local"
    private val lock = Any()
    private val windowStartedAt = LongArray(ResponseCacheOperation.entries.size) { Long.MIN_VALUE }
    private val windowCounts = IntArray(ResponseCacheOperation.entries.size)

    fun warn(operation: ResponseCacheOperation, kind: ResponseCacheFailureKind, route: ResponseCacheRoute) {
        if (!admit(operation)) return
        try {
            sink(format(operation, kind, route))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            observability?.telemetryFailed()
        }
    }

    internal fun format(operation: ResponseCacheOperation, kind: ResponseCacheFailureKind, route: ResponseCacheRoute): String =
        buildJsonObject {
            put("severity", "WARN")
            put("message", WARNING_MESSAGE)
            put("operation", operation.label)
            put("kind", kind.label)
            put("route", route.label)
            putJsonObject("serviceContext") {
                put("service", service)
                put("version", version)
            }
        }.toString()

    private fun admit(operation: ResponseCacheOperation): Boolean = synchronized(lock) {
        val now = nowMillis()
        val index = operation.ordinal
        if (windowStartedAt[index] == Long.MIN_VALUE || now - windowStartedAt[index] >= WARNING_WINDOW_MILLIS) {
            windowStartedAt[index] = now
            windowCounts[index] = 0
        }
        if (windowCounts[index] >= maxPerWindow) return false
        windowCounts[index] += 1
        true
    }

    private companion object {
        const val WARNING_MESSAGE = "response cache operation failed"
        const val WARNING_WINDOW_MILLIS = 60_000L
        const val MAX_WARNINGS_PER_WINDOW = 4
    }
}

private val cacheLogger = LoggerFactory.getLogger("server.cache")

/**
 * Builds the application's cache; a disabled config creates no client and no store.
 * [store] is a test seam that replaces only the Upstash store of an enabled config.
 */
internal fun Application.createResponseCache(
    config: ResponseCacheConfig,
    observability: PublicApiObservability?,
    store: ResponseCacheStore? = null,
): ResponseCache = when (config) {
    ResponseCacheConfig.Disabled -> ResponseCache.DISABLED
    is ResponseCacheConfig.Enabled -> ResponseCache(
        store = store ?: createUpstashRestResponseCacheStore(config),
        deploySha = config.deploySha,
        observability = observability,
        warnings = ResponseCacheWarnings(observability = observability),
    )
}

package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kr.co.cotton.vlrgg_mobile.common.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability

internal val PublicRequestStartedAtNanosKey = AttributeKey<Long>("public-request-started-at-nanos")
internal val PublicRequestObservationRecordedKey = AttributeKey<Unit>("public-request-observation-recorded")

internal fun ApplicationCall.recordPublicRequestCompletion(observability: PublicApiObservability, status: Int) {
    val startedAt = attributes.getOrNull(PublicRequestStartedAtNanosKey) ?: return
    if (attributes.getOrNull(PublicRequestObservationRecordedKey) != null) return
    attributes.put(PublicRequestObservationRecordedKey, Unit)
    observability.completed(status, (System.nanoTime() - startedAt) / 1_000_000)
}

internal fun ApplicationCall.recordPublicRequestRejection(
    observability: PublicApiObservability,
    failure: ServerFailure,
) {
    val startedAt = attributes.getOrNull(PublicRequestStartedAtNanosKey) ?: return
    if (attributes.getOrNull(PublicRequestObservationRecordedKey) != null) return
    attributes.put(PublicRequestObservationRecordedKey, Unit)
    observability.rejected(failure, (System.nanoTime() - startedAt) / 1_000_000)
}

internal data class PublicApiProtectionConfig(
    val apiRequestsPerSecond: Int = 10,
    val apiBurst: Int = 20,
    val maxActiveApiRequests: Int = 8,
    val upstreamRequestsPerSecond: Int = 2,
    val upstreamBurst: Int = 4,
    val maxActiveUpstreamRequests: Int = 4,
    val maxInFlightCanonicalUrls: Int = 4,
    val wholeRequestTimeoutMillis: Long = 15_000,
    val maxRequestTargetBytes: Int = 4 * 1024,
    val maxRequestHeaderBytes: Int = 16 * 1024,
    val maxRequestBodyBytes: Int = 1024,
) {
    init {
        require(apiRequestsPerSecond > 0 && apiBurst > 0 && maxActiveApiRequests > 0)
        require(upstreamRequestsPerSecond > 0 && upstreamBurst > 0 && maxActiveUpstreamRequests > 0)
        require(maxInFlightCanonicalUrls > 0 && wholeRequestTimeoutMillis > 0)
        require(maxRequestTargetBytes in 1..<Int.MAX_VALUE)
        require(maxRequestHeaderBytes in 1..<Int.MAX_VALUE)
        require(maxRequestBodyBytes in 1..<Int.MAX_VALUE)
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String>): PublicApiProtectionConfig = PublicApiProtectionConfig(
            apiRequestsPerSecond = environment.requiredPositive("VLRGG_API_RATE_PER_SECOND", 10),
            apiBurst = environment.requiredPositive("VLRGG_API_RATE_BURST", 20),
            maxActiveApiRequests = environment.requiredPositive("VLRGG_API_MAX_CONCURRENCY", 8),
            upstreamRequestsPerSecond = environment.requiredPositive("VLRGG_UPSTREAM_RATE_PER_SECOND", 2),
            upstreamBurst = environment.requiredPositive("VLRGG_UPSTREAM_RATE_BURST", 4),
            maxActiveUpstreamRequests = environment.requiredPositive("VLRGG_UPSTREAM_MAX_CONCURRENCY", 4),
            maxInFlightCanonicalUrls = environment.requiredPositive("VLRGG_UPSTREAM_MAX_IN_FLIGHT_KEYS", 4),
            wholeRequestTimeoutMillis = environment.requiredPositive("VLRGG_REQUEST_TIMEOUT_MILLIS", 15_000).toLong(),
            maxRequestTargetBytes = environment.requiredPositive("VLRGG_MAX_REQUEST_TARGET_BYTES", 4 * 1024),
            maxRequestHeaderBytes = environment.requiredPositive("VLRGG_MAX_REQUEST_HEADER_BYTES", 16 * 1024),
            maxRequestBodyBytes = environment.requiredPositive("VLRGG_MAX_REQUEST_BODY_BYTES", 1024),
        )

        private fun Map<String, String>.requiredPositive(name: String, default: Int): Int {
            val raw = this[name] ?: return default
            return raw.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("$name must be a positive integer.")
        }
    }
}

internal fun interface MonotonicClock { fun nowMillis(): Long }

internal class TokenBucket(
    private val ratePerSecond: Int,
    private val burst: Int,
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()
    private var tokens = burst.toDouble()
    private var lastMillis = clock.nowMillis()

    suspend fun tryAcquire(): Boolean = mutex.withLock {
        val now = clock.nowMillis()
        val elapsed = (now - lastMillis).coerceAtLeast(0)
        tokens = minOf(burst.toDouble(), tokens + elapsed.toDouble() * ratePerSecond / 1_000)
        lastMillis = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        true
    }
}

internal class PublicApiProtection(
    private val config: PublicApiProtectionConfig,
    private val scope: CoroutineScope,
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000 },
    internal val observability: PublicApiObservability = PublicApiObservability(),
) {
    private val apiBucket = TokenBucket(config.apiRequestsPerSecond, config.apiBurst, clock)
    private val apiSemaphore = Semaphore(config.maxActiveApiRequests)
    private val upstreamBucket = TokenBucket(config.upstreamRequestsPerSecond, config.upstreamBurst, clock)
    private val upstreamSemaphore = Semaphore(config.maxActiveUpstreamRequests)
    private val singleFlight = HtmlSingleFlight(config.maxInFlightCanonicalUrls, scope, upstreamBucket, upstreamSemaphore, observability)

    suspend fun <T> admitApi(block: suspend () -> T): T {
        if (!apiBucket.tryAcquire()) throw RateLimitedFailure()
        if (!apiSemaphore.tryAcquire()) throw ServerBusyFailure()
        observability.apiStarted()
        return try { block() } finally { observability.apiFinished(); apiSemaphore.release() }
    }

    suspend fun getHtml(url: Url, fetch: suspend () -> String): String {
        if (!url.isAllowedVlrUpstreamUrl()) throw UpstreamNetworkFailure(url)
        return singleFlight.await(url.toString(), fetch)
    }
}

private class HtmlSingleFlight(
    private val maxEntries: Int,
    private val scope: CoroutineScope,
    private val bucket: TokenBucket,
    private val semaphore: Semaphore,
    private val observability: PublicApiObservability,
) {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    private class Entry(val deferred: Deferred<String>, var waiters: Int)

    suspend fun await(key: String, fetch: suspend () -> String): String {
        var entry: Entry? = null
        try {
            entry = withContext(NonCancellable) {
                mutex.withLock {
                    val current = entries[key]
                    when {
                        current == null -> createEntry(key, fetch)
                        current.deferred.isCompleted -> {
                            if (entries[key] === current) entries.remove(key)
                            createEntry(key, fetch)
                        }
                        else -> current.also { it.waiters += 1; observability.joinedSingleFlight() }
                    }
                }
            }
            return entry.deferred.await()
        } finally {
            entry?.let { releaseWaiter(key, it) }
        }
    }

    private suspend fun releaseWaiter(key: String, entry: Entry) = withContext(NonCancellable) {
        val last = mutex.withLock {
            entry.waiters -= 1
            if (entry.waiters != 0) false else {
                if (entries[key] === entry) entries.remove(key)
                true
            }
        }
        if (last) {
            if (entry.deferred.isActive) entry.deferred.cancel()
            entry.deferred.join()
            observability.upstreamFinished()
            semaphore.release()
        }
    }

    private suspend fun createEntry(key: String, fetch: suspend () -> String): Entry {
        if (entries.size >= maxEntries) throw ServerBusyFailure()
        val admitted = withContext(NonCancellable) {
            if (!semaphore.tryAcquire()) return@withContext false
            if (bucket.tryAcquire()) return@withContext true
            semaphore.release()
            false
        }
        if (!admitted) throw ServerBusyFailure()
        observability.upstreamStarted()
        val deferred = scope.async(start = CoroutineStart.LAZY) { fetch() }
        val entry = Entry(deferred, waiters = 1)
        entries[key] = entry
        deferred.start()
        return entry
    }
}

internal fun Application.createPublicApiProtection(
    config: PublicApiProtectionConfig = PublicApiProtectionConfig.fromEnvironment(System.getenv()),
    observability: PublicApiObservability = PublicApiObservability(),
): PublicApiProtection {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStopping) { scope.cancel() }
    return PublicApiProtection(config, scope, observability = observability)
}

internal fun Application.configurePublicRequestProtection(
    protection: PublicApiProtection,
    config: PublicApiProtectionConfig,
) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() == "/health") {
            proceed()
            return@intercept
        }
        protection.observability.requestStarted(protection.observability.routeClass(call.request.path()))
        val startedAt = System.nanoTime()
        call.attributes.put(PublicRequestStartedAtNanosKey, startedAt)
        try {
            withWholeRequestDeadline(config.wholeRequestTimeoutMillis) {
                call.validatePublicRequest(config)
                protection.admitApi { proceed() }
            }
            call.recordPublicRequestCompletion(protection.observability, call.response.status()?.value ?: 500)
        } catch (failure: ServerFailure) {
            // StatusPages owns failure accounting so handled responses are counted exactly once.
            throw failure
        }
    }
}

/** Only this enclosing deadline becomes the public 504; nested timeout cancellation remains cancellation. */
internal suspend fun withWholeRequestDeadline(timeoutMillis: Long, block: suspend () -> Unit) {
    val completed = withTimeoutOrNull(timeoutMillis) { block(); true }
    if (completed != true) throw RequestDeadlineFailure()
}

internal fun UpstreamHtmlTransport.withPublicProtection(protection: PublicApiProtection): UpstreamHtmlTransport =
    object : UpstreamHtmlTransport {
        override suspend fun get(url: Url): String = protection.getHtml(url) { this@withPublicProtection.get(url) }
    }

internal suspend fun ApplicationCall.validatePublicRequest(config: PublicApiProtectionConfig) {
    if (request.local.uri.toByteArray(StandardCharsets.UTF_8).size > config.maxRequestTargetBytes) {
        throw InvalidInputFailure()
    }
    if (request.headers.exceedsPublicHeaderByteLimit(config.maxRequestHeaderBytes)) {
        throw RequestHeadersTooLargeFailure()
    }
    val length = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
    if (request.header(HttpHeaders.ContentLength) != null && length == null) throw InvalidInputFailure()
    if (length != null && length < 0) throw InvalidInputFailure()
    if (length != null && length > config.maxRequestBodyBytes) throw RequestTooLargeFailure()
    val channel = receiveChannel()
    val buffer = ByteArray(minOf(config.maxRequestBodyBytes + 1, 1025))
    var total = 0
    while (!channel.isClosedForRead) {
        val read = channel.readAvailable(buffer)
        if (read <= 0) continue
        total += read
        if (total > config.maxRequestBodyBytes) throw RequestTooLargeFailure()
    }
}

/** Each value is a separate HTTP header field, including its own name, delimiter, and CRLF. */
internal fun Headers.exceedsPublicHeaderByteLimit(maxBytes: Int): Boolean =
    publicHeaderBytes() > maxBytes.toLong()

internal fun Headers.publicHeaderBytes(): Long = entries().sumOf { (name, values) ->
    values.sumOf { value ->
        name.toByteArray(StandardCharsets.UTF_8).size.toLong() +
            value.toByteArray(StandardCharsets.UTF_8).size.toLong() +
            4L // ": " plus CRLF on the wire
    }
}

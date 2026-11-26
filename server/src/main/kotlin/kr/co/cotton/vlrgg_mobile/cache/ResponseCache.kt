package kr.co.cotton.vlrgg_mobile.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import com.github.benmanes.caffeine.cache.Weigher
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtection

internal sealed interface CachedPublicResponse {
    /** The exact success JSON bytes that the miss path produced. */
    class Success(val body: ByteArray) : CachedPublicResponse

    /** A cached `SOURCE_PARSING_FAILURE`: only its status and code are replayed. */
    data object ParsingFailure : CachedPublicResponse
}

/** What one shared load produces: the final success JSON bytes and the route's TTL for them. */
internal class LoadedPublicResponse(val body: ByteArray, val ttlSeconds: Int)

/**
 * Process-local response cache on Caffeine plus a per-key in-flight map.
 *
 * Capacity is the byte sum of keys and values. Expiry is per entry. Caffeine runs its maintenance on the calling
 * thread (`executor(Runnable::run)`, no scheduler), so expiry and eviction happen while requests are processed and
 * never depend on background CPU, which Cloud Run withholds between requests. Writes are serialized under [mutex]
 * and each one finishes its maintenance (`cleanUp`) before returning, so the stored bytes are within the limit
 * whenever no write is in progress. An expired value is never returned.
 *
 * Concurrent misses of one key share one load. The load runs in the application scope holding one upstream work
 * slot; callers wait for it without blocking a thread. One waiter leaving does not stop the load; the last waiter
 * leaving cancels it. A failed load is not stored except `SOURCE_PARSING_FAILURE`, which is stored for 60 seconds.
 *
 * A load publishes its result under the same [mutex] that registers and releases waiters, and only while it still
 * owns the key's in-flight entry. Publishing stores the value and removes the entry in one step, so a request either
 * finds the stored value or joins a load that has not published yet, and a load cancelled after its last waiter left
 * never overwrites what a newer load stored. A success that capacity did not retain is not a failure; the next request
 * simply loads again, as after an eviction.
 */
internal class ResponseCache private constructor(
    private val store: Cache<String, StoredResponse>?,
    private val protection: PublicApiProtection?,
    private val observability: PublicApiObservability?,
    private val ticker: Ticker,
    private val afterInnerMiss: () -> Unit,
) {
    /** [afterInnerMiss] is a test seam: it runs under the in-flight lock right after the locked lookup missed. */
    constructor(
        maxBytes: Int,
        protection: PublicApiProtection,
        observability: PublicApiObservability? = null,
        ticker: Ticker = Ticker.systemTicker(),
        afterInnerMiss: () -> Unit = {},
    ) : this(buildStore(maxBytes, ticker), protection, observability, ticker, afterInnerMiss)

    private val mutex = Mutex()
    private val inFlight = HashMap<String, InFlight>()

    val isEnabled: Boolean get() = store != null

    /**
     * Returns a hit, a cached parsing failure, or the result of the shared load for [key]. [load] runs outside the
     * request, so it must use only validated route values and never the call. Admission failures of the shared
     * load (429/503) and load failures propagate unchanged; [load] failures reach every waiter of that load.
     */
    suspend fun get(key: ResponseCacheKey, load: suspend () -> LoadedPublicResponse): CachedPublicResponse {
        val store = checkNotNull(store) { "A disabled response cache has no entries." }
        lookup(store, key)?.let { return it }
        var flight: InFlight? = null
        try {
            val hit = withContext(NonCancellable) {
                mutex.withLock {
                    lookup(store, key) ?: run {
                        afterInnerMiss()
                        flight = joinOrStart(store, key, load)
                        null
                    }
                }
            }
            if (hit != null) return hit
            return checkNotNull(flight).deferred.await()
        } finally {
            flight?.let { leave(key, it) }
        }
    }

    /**
     * Called under [mutex]. Publication removes a finished load's entry under this lock, so a completed entry still in
     * the map never published (it failed, or was cancelled before it ran) and is replaced.
     */
    private suspend fun joinOrStart(
        store: Cache<String, StoredResponse>,
        key: ResponseCacheKey,
        load: suspend () -> LoadedPublicResponse,
    ): InFlight {
        val current = inFlight[key.value]
        if (current != null && !current.deferred.isCompleted) {
            current.waiters += 1
            count(ResponseCacheCounter.JOINED)
            return current
        }
        if (current != null) inFlight.remove(key.value)
        count(ResponseCacheCounter.MISS)
        val owner = Any()
        val deferred = checkNotNull(protection).startSharedUpstreamWork { loadAndPublish(store, key, owner, load) }
        return InFlight(owner, deferred, waiters = 1).also { inFlight[key.value] = it }
    }

    private suspend fun leave(key: ResponseCacheKey, flight: InFlight) = withContext(NonCancellable) {
        val last = mutex.withLock {
            flight.waiters -= 1
            if (flight.waiters != 0) {
                false
            } else {
                if (inFlight[key.value] === flight) inFlight.remove(key.value)
                true
            }
        }
        if (last && flight.deferred.isActive) flight.deferred.cancel()
    }

    private suspend fun loadAndPublish(
        store: Cache<String, StoredResponse>,
        key: ResponseCacheKey,
        owner: Any,
        load: suspend () -> LoadedPublicResponse,
    ): CachedPublicResponse.Success {
        try {
            val loaded = try {
                load()
            } catch (failure: SourceParsingFailure) {
                publish(store, key, owner, CachedPublicResponse.ParsingFailure, NEGATIVE_ENTRY_WEIGHT, ResponseCacheTtl.SOURCE_PARSING_FAILURE_SECONDS)
                throw failure
            }
            val success = CachedPublicResponse.Success(loaded.body)
            publish(store, key, owner, success, loaded.body.size, loaded.ttlSeconds)
            return success
        } finally {
            // Any other end (failure, cancellation) retires the entry, so the next request loads again.
            withContext(NonCancellable) {
                mutex.withLock { if (inFlight[key.value]?.owner === owner) inFlight.remove(key.value) }
            }
        }
    }

    /** Stores [response] and removes the entry atomically, only while this load still owns the key. */
    private suspend fun publish(
        store: Cache<String, StoredResponse>,
        key: ResponseCacheKey,
        owner: Any,
        response: CachedPublicResponse,
        weight: Int,
        ttlSeconds: Int,
    ) {
        require(ttlSeconds in 1..ResponseCacheTtl.MAX_SECONDS) { "Response cache TTL must be between 1 and 300 seconds." }
        withContext(NonCancellable) {
            mutex.withLock {
                // Not the owner: the last waiter left and cancelled this load, and a newer load may own the key.
                if (inFlight[key.value]?.owner !== owner) return@withLock
                put(store, key, response, weight, ttlSeconds)
                inFlight.remove(key.value)
            }
        }
    }

    /** Called under [mutex], which serializes writes; `cleanUp` finishes the eviction this write needs before returning. */
    private fun put(
        store: Cache<String, StoredResponse>,
        key: ResponseCacheKey,
        response: CachedPublicResponse,
        weight: Int,
        ttlSeconds: Int,
    ) {
        val ttlNanos = TimeUnit.SECONDS.toNanos(ttlSeconds.toLong())
        store.put(key.value, StoredResponse(response, weight, ttlNanos, ticker.read() + ttlNanos))
        store.cleanUp()
    }

    private fun lookup(store: Cache<String, StoredResponse>, key: ResponseCacheKey): CachedPublicResponse? {
        val stored = store.getIfPresent(key.value) ?: return null
        // Caffeine already hides expired entries; this keeps the guarantee independent of its expiry bookkeeping.
        if (ticker.read() - stored.expiresAtNanos >= 0) return null
        count(
            when (stored.response) {
                is CachedPublicResponse.Success -> ResponseCacheCounter.HIT
                CachedPublicResponse.ParsingFailure -> ResponseCacheCounter.NEGATIVE_HIT
            },
        )
        return stored.response
    }

    /**
     * Key plus value bytes of the entries the map holds right now, without running maintenance, so it shows only what
     * the writes themselves already cleaned up; for tests.
     */
    internal fun storedBytesWithoutMaintenance(): Long =
        checkNotNull(store).asMap().entries.sumOf { (key, value) -> (key.length + value.weight).toLong() }

    /** Runs pending maintenance and returns the stored byte total; for tests and diagnostics only. */
    internal fun weightedBytes(): Long {
        val store = checkNotNull(store)
        store.cleanUp()
        return store.policy().eviction().orElseThrow().weightedSize().orElseThrow()
    }

    private fun count(counter: ResponseCacheCounter) {
        observability?.responseCache(counter)
    }

    private class InFlight(val owner: Any, val deferred: Deferred<CachedPublicResponse.Success>, var waiters: Int)

    private class StoredResponse(
        val response: CachedPublicResponse,
        val weight: Int,
        val ttlNanos: Long,
        val expiresAtNanos: Long,
    )

    companion object {
        /** Route default: no entries, no counters, every request loads directly. */
        val DISABLED = ResponseCache(
            store = null, protection = null, observability = null, ticker = Ticker.systemTicker(), afterInnerMiss = {},
        )

        /** A negative entry holds no body; its weight keeps it inside the capacity accounting. */
        private const val NEGATIVE_ENTRY_WEIGHT = 16

        private fun buildStore(maxBytes: Int, ticker: Ticker): Cache<String, StoredResponse> {
            require(maxBytes > 0) { "Response cache capacity must be positive." }
            return Caffeine.newBuilder()
                .maximumWeight(maxBytes.toLong())
                .weigher(Weigher<String, StoredResponse> { key, value -> key.length + value.weight })
                .expireAfter(PerEntryTtl)
                .executor(Runnable::run)
                .ticker(ticker)
                .build()
        }
    }

    /** A write sets the entry's own TTL; a read never extends it. */
    private object PerEntryTtl : Expiry<String, StoredResponse> {
        override fun expireAfterCreate(key: String, value: StoredResponse, currentTime: Long): Long = value.ttlNanos

        override fun expireAfterUpdate(key: String, value: StoredResponse, currentTime: Long, currentDuration: Long): Long =
            value.ttlNanos

        override fun expireAfterRead(key: String, value: StoredResponse, currentTime: Long, currentDuration: Long): Long =
            currentDuration
    }
}

/** A disabled config builds nothing; an enabled one shares the protection's application-owned scope for loads. */
internal fun createResponseCache(
    config: ResponseCacheConfig,
    protection: PublicApiProtection,
    observability: PublicApiObservability?,
): ResponseCache = if (config.enabled) {
    ResponseCache(config.maxBytes, protection, observability)
} else {
    ResponseCache.DISABLED
}

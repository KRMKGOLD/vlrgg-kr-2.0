package kr.co.cotton.vlrgg_mobile.plugins

import io.ktor.server.application.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ServerFailure

internal enum class PublicRouteClass { API, OTHER }

/** Process-local, fixed-cardinality counters. No request-derived string is retained as a metric label. */
internal class PublicApiObservability(
    private val emit: (Snapshot) -> Unit = {},
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val requestCount = AtomicLong()
    private val lastSummaryMillis = AtomicLong(Long.MIN_VALUE)
    private val routeClasses = AtomicLongArray(PublicRouteClass.entries.size)
    private val statusClasses = AtomicLongArray(6)
    private val rejections = AtomicLongArray(ApiErrorCode.entries.size)
    private val latencyBuckets = AtomicLongArray(4)
    private val activeApi = AtomicInteger()
    private val activeUpstream = AtomicInteger()
    private val singleFlightJoins = AtomicLong()
    private val upstreamFailures = AtomicLong()

    fun routeClass(path: String): PublicRouteClass =
        if (path.startsWith("/api/v1/")) PublicRouteClass.API else PublicRouteClass.OTHER

    fun requestStarted(routeClass: PublicRouteClass) {
        requestCount.incrementAndGet()
        routeClasses.incrementAndGet(routeClass.ordinal)
    }
    fun apiStarted() = activeApi.incrementAndGet()
    fun apiFinished() = activeApi.decrementAndGet()
    fun upstreamStarted() = activeUpstream.incrementAndGet()
    fun upstreamFinished() = activeUpstream.decrementAndGet()
    fun joinedSingleFlight() = singleFlightJoins.incrementAndGet()

    fun completed(status: Int, elapsedMillis: Long) {
        statusClasses.incrementAndGet(status.coerceIn(0, 599) / 100)
        latencyBuckets.incrementAndGet(when { elapsedMillis < 100 -> 0; elapsedMillis < 1_000 -> 1; elapsedMillis < 3_000 -> 2; else -> 3 })
        emitIfDue()
    }

    fun rejected(failure: ServerFailure, elapsedMillis: Long) {
        rejections.incrementAndGet(failure.errorCode.ordinal)
        if (failure.errorCode == ApiErrorCode.UPSTREAM_NETWORK_FAILURE || failure.errorCode == ApiErrorCode.SOURCE_PARSING_FAILURE) {
            upstreamFailures.incrementAndGet()
        }
        completed(failure.status.value, elapsedMillis)
    }

    fun snapshot(): Snapshot = Snapshot(
        requests = requestCount.get(),
        routeClasses = List(PublicRouteClass.entries.size) { routeClasses[it] },
        statusClasses = List(6) { statusClasses[it] },
        rejections = ApiErrorCode.entries.associateWith { rejections[it.ordinal] },
        latencyBuckets = List(4) { latencyBuckets[it] },
        activeApi = activeApi.get(),
        activeUpstream = activeUpstream.get(),
        singleFlightJoins = singleFlightJoins.get(),
        upstreamFailures = upstreamFailures.get(),
    )

    internal data class Snapshot(
        val requests: Long,
        val routeClasses: List<Long>,
        val statusClasses: List<Long>,
        val rejections: Map<ApiErrorCode, Long>,
        val latencyBuckets: List<Long>,
        val activeApi: Int,
        val activeUpstream: Int,
        val singleFlightJoins: Long,
        val upstreamFailures: Long,
    )

    private fun emitIfDue() {
        val now = nowMillis()
        while (true) {
            val previous = lastSummaryMillis.get()
            if (previous != Long.MIN_VALUE && now - previous < SUMMARY_MIN_INTERVAL_MILLIS) return
            if (lastSummaryMillis.compareAndSet(previous, now)) {
                emit(snapshot())
                return
            }
        }
    }

    private companion object {
        const val SUMMARY_MIN_INTERVAL_MILLIS = 60_000L
    }
}

internal fun Application.configureMonitoring(): PublicApiObservability = PublicApiObservability(emit = { summary ->
    log.info(formatPublicApiSummary(summary))
})

/** The emitted summary uses only fixed labels, never request-derived values. */
internal fun formatPublicApiSummary(summary: PublicApiObservability.Snapshot): String =
    "public_api_summary requests=${summary.requests} " +
        "routes={api=${summary.routeClasses[PublicRouteClass.API.ordinal]},other=${summary.routeClasses[PublicRouteClass.OTHER.ordinal]}} " +
        "status={0xx=${summary.statusClasses[0]},1xx=${summary.statusClasses[1]},2xx=${summary.statusClasses[2]},3xx=${summary.statusClasses[3]},4xx=${summary.statusClasses[4]},5xx=${summary.statusClasses[5]}} " +
        "rejections={${ApiErrorCode.entries.joinToString(",") { code -> "${code.name}=${summary.rejections.getValue(code)}" }}} " +
        "latency={lt_100ms=${summary.latencyBuckets[0]},100ms_to_lt_1s=${summary.latencyBuckets[1]},1s_to_lt_3s=${summary.latencyBuckets[2]},gte_3s=${summary.latencyBuckets[3]}} " +
        "active_api=${summary.activeApi} active_upstream=${summary.activeUpstream} " +
        "joins=${summary.singleFlightJoins} upstream_failures=${summary.upstreamFailures}"

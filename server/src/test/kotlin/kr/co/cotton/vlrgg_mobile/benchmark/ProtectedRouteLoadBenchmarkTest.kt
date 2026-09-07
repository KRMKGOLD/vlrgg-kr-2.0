package kr.co.cotton.vlrgg_mobile.benchmark

import com.sun.management.OperatingSystemMXBean
import io.ktor.http.Url
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.matches.DefaultMatchesService
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesMapper
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesParser
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesScraper
import kr.co.cotton.vlrgg_mobile.feature.matches.configureMatchesRoutes
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtection
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kr.co.cotton.vlrgg_mobile.protection.configurePublicRequestProtection
import kr.co.cotton.vlrgg_mobile.protection.withPublicProtection
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in local measurement for Issue #52 I3/I4.  It starts one Netty API and uses the actual
 * matches route/service/parser/mapper/protection/serializer composition, but deliberately injects
 * a fixture transport.  It therefore never sends a request to VLR.GG and does not exercise CIO.
 */
class ProtectedRouteLoadBenchmarkTest {

    @Test
    fun `records protected route load with supplied fixture transport`() {
        val reportPath = System.getenv(REPORT_PATH_ENV)
        assumeTrue("$REPORT_PATH_ENV must be set; this 60-second benchmark is opt-in.", !reportPath.isNullOrBlank())

        val fixtureTransport = DelayedFixtureTransport(matchesListFixture(), matchesDetailFixture())
        val observability = PublicApiObservability()
        val protectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val protectionConfig = PublicApiProtectionConfig(
            maxActiveApiRequests = MAX_ACTIVE_API_REQUESTS,
            upstreamRequestsPerSecond = UPSTREAM_REQUESTS_PER_SECOND,
            upstreamBurst = UPSTREAM_BURST,
            maxActiveUpstreamRequests = MAX_ACTIVE_UPSTREAM_REQUESTS,
            maxInFlightCanonicalUrls = MAX_IN_FLIGHT_CANONICAL_URLS,
        )
        val protection = PublicApiProtection(
            config = protectionConfig,
            scope = protectionScope,
            observability = observability,
        )
        val service = DefaultMatchesService(
            scraper = VlrMatchesScraper(fixtureTransport.withPublicProtection(protection)),
            parser = VlrMatchesParser(),
            mapper = MatchesMapper(),
        )
        val api = embeddedServer(Netty, host = LOOPBACK, port = 0) {
            configureProtectedMatchesRoute(protection, protectionConfig, observability, service)
        }
        val metricsSampler = Executors.newSingleThreadScheduledExecutor()
        var client: HttpClient? = null

        try {
            api.start(wait = false)
            val apiPort = runBlocking { api.engine.resolvedConnectors().single().port }
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            val endpoint = URI("http://$LOOPBACK:$apiPort$UPCOMING_PATH")
            val peaks = Peaks(observability)
            metricsSampler.scheduleAtFixedRate(peaks::sample, 0, SAMPLE_PERIOD_MILLIS, TimeUnit.MILLISECONDS)

            TimeUnit.MILLISECONDS.sleep(IDLE_SETTLE_MILLIS)
            val idle = captureProcessMetrics()
            val normal = runNormalLoad(client, endpoint, fixtureTransport)
            val afterNormal = captureProcessMetrics()
            waitForUpstreamTokenRefill()
            val burst = runDistinctDetailBurst(client, apiPort, fixtureTransport)
            val saturation = runSaturationLoad(client, apiPort, peaks, fixtureTransport)
            assertEquals(0, saturation.clientFailures, "the bounded client must observe no transport failures")
            assertEquals(0, saturation.httpOther, "the protected route may only return 200, 429, or 503")
            assertEquals(
                saturation.submitted,
                saturation.http200 + saturation.http429 + saturation.http503,
                "every submitted request must receive an expected protected-route status",
            )
            assertTrue(
                saturation.upstreamFetches <= saturationUpstreamFetchLimit(),
                "upstream fetches exceeded the bucket burst plus bounded 2rps refill allowance",
            )
            waitForQuiescence(observability)
            waitForUpstreamTokenRefill()
            val beforeRecoveryFetches = fixtureTransport.fetches.get()
            val recovery = client.send(request(endpoint), HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, recovery.statusCode(), "the protected route must recover after the bounded workload")
            val recoveryFetchCost = fixtureTransport.fetches.get() - beforeRecoveryFetches
            assertEquals(1, recoveryFetchCost, "recovery must create one fresh fixture fetch after quiescence")
            waitForQuiescence(observability)
            peaks.sample()
            assertTrue(peaks.activeApi.get() <= MAX_ACTIVE_API_REQUESTS, "API concurrency exceeded its cap")
            assertTrue(peaks.activeUpstream.get() <= MAX_ACTIVE_UPSTREAM_REQUESTS, "upstream concurrency exceeded its cap")
            val finalMetrics = captureProcessMetrics()

            val report = renderReport(
                idle = idle,
                afterNormal = afterNormal,
                finalMetrics = finalMetrics,
                normal = normal,
                burst = burst,
                saturation = saturation,
                peaks = peaks,
                observability = observability.snapshot(),
                upstreamFetches = fixtureTransport.fetches.get(),
                recoveryFetchCost = recoveryFetchCost,
            )
            val output = Path.of(checkNotNull(reportPath))
            output.parent?.let(Files::createDirectories)
            Files.writeString(output, report)
        } finally {
            metricsSampler.shutdownNow()
            try {
                assertTrue(metricsSampler.awaitTermination(2, TimeUnit.SECONDS), "metric sampler did not stop")
            } finally {
                try {
                    client?.close()
                } finally {
                    try {
                        api.stop(1_000, 5_000)
                    } finally {
                        protectionScope.cancel()
                    }
                }
            }
        }
    }

    private fun Application.configureProtectedMatchesRoute(
        protection: PublicApiProtection,
        protectionConfig: PublicApiProtectionConfig,
        observability: PublicApiObservability,
        service: DefaultMatchesService,
    ) {
        configureSerialization()
        configureErrorHandling(observability)
        configurePublicRequestProtection(protection, protectionConfig)
        configureMatchesRoutes(service)
    }

    private fun runNormalLoad(
        client: HttpClient,
        endpoint: URI,
        fixtureTransport: DelayedFixtureTransport,
    ): NormalResult {
        val requestNanos = mutableListOf<Long>()
        val beforeFetches = fixtureTransport.fetches.get()
        val start = System.nanoTime()
        val cpuStart = processCpuNanos()
        repeat(NORMAL_REQUESTS) { index ->
            val requestStart = System.nanoTime()
            val response = client.send(request(endpoint), HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, response.statusCode(), "normal request $index")
            requestNanos += System.nanoTime() - requestStart
            val nextSlot = start + (index + 1L) * NORMAL_INTERVAL_NANOS
            val remaining = nextSlot - System.nanoTime()
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining)
        }
        val elapsed = System.nanoTime() - start
        val fetchCost = fixtureTransport.fetches.get() - beforeFetches
        assertEquals(NORMAL_REQUESTS, fetchCost, "paced normal requests are distinct completed fetches")
        return NormalResult(elapsed, processCpuNanos() - cpuStart, requestNanos, fetchCost)
    }

    private fun waitForUpstreamTokenRefill() {
        TimeUnit.MILLISECONDS.sleep(UPSTREAM_REFILL_MILLIS)
    }

    private fun runDistinctDetailBurst(
        client: HttpClient,
        apiPort: Int,
        fixtureTransport: DelayedFixtureTransport,
    ): BurstResult {
        val beforeFetches = fixtureTransport.fetches.get()
        val gate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(BURST_REQUESTS)
        val started = System.nanoTime()
        try {
            val futures = BURST_DETAIL_IDS.map { matchId ->
                executor.submit<Int> {
                    gate.await(2, TimeUnit.SECONDS)
                    client.send(
                        request(URI("http://$LOOPBACK:$apiPort/api/v1/matches/$matchId")),
                        HttpResponse.BodyHandlers.ofByteArray(),
                    ).statusCode()
                }
            }
            gate.countDown()
            futures.forEach { assertEquals(200, it.get(5, TimeUnit.SECONDS), "distinct-detail burst must pass") }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "burst executor did not stop")
        }
        val fetchCost = fixtureTransport.fetches.get() - beforeFetches
        assertEquals(BURST_REQUESTS, fetchCost, "four distinct detail URLs must create four fixture fetches after refill")
        return BurstResult(System.nanoTime() - started, fetchCost)
    }

    /**
     * A fixed-rate generator with at most [CLIENT_MAX_IN_FLIGHT] outstanding HTTP calls.  A full
     * client semaphore is counted as generator drop rather than being placed in an unbounded queue.
     */
    private fun runSaturationLoad(
        client: HttpClient,
        apiPort: Int,
        peaks: Peaks,
        fixtureTransport: DelayedFixtureTransport,
    ): SaturationResult {
        val counters = SaturationCounters()
        val beforeFetches = fixtureTransport.fetches.get()
        val clientSlots = Semaphore(CLIENT_MAX_IN_FLIGHT)
        val pending = Collections.synchronizedList(mutableListOf<java.util.concurrent.CompletableFuture<*>>())
        val start = System.nanoTime()
        val cpuStart = processCpuNanos()
        repeat(SATURATION_REQUESTS) { index ->
            val scheduled = start + index * SATURATION_INTERVAL_NANOS
            val remaining = scheduled - System.nanoTime()
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining)
            if (!clientSlots.tryAcquire()) {
                counters.generatorDropped.incrementAndGet()
                return@repeat
            }
            val endpoint = URI("http://$LOOPBACK:$apiPort/api/v1/matches/${index + 1}")
            val requestStart = System.nanoTime()
            counters.submitted.incrementAndGet()
            val future = client.sendAsync(request(endpoint), HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete { response, failure ->
                    try {
                        if (failure != null) {
                            counters.clientFailures.incrementAndGet()
                        } else {
                            counters.record(response.statusCode(), response.body().size, System.nanoTime() - requestStart)
                        }
                    } finally {
                        clientSlots.release()
                    }
                }
            pending += future
            pending.removeAll { it.isDone }
            peaks.sample()
        }
        while (pending.isNotEmpty()) {
            pending.toList().forEach { it.get(10, TimeUnit.SECONDS) }
            pending.removeAll { it.isDone }
        }
        return counters.finish(
            elapsedNanos = System.nanoTime() - start,
            cpuNanos = processCpuNanos() - cpuStart,
            upstreamFetches = fixtureTransport.fetches.get() - beforeFetches,
        )
    }

    private fun waitForQuiescence(observability: PublicApiObservability) {
        val deadline = System.nanoTime() + QUIESCENCE_TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            val snapshot = observability.snapshot()
            if (snapshot.activeApi == 0 && snapshot.activeUpstream == 0) return
            TimeUnit.MILLISECONDS.sleep(25)
        }
        val final = observability.snapshot()
        assertEquals(0, final.activeApi, "active API work leaked after workload")
        assertEquals(0, final.activeUpstream, "active upstream work leaked after workload")
    }

    private fun request(endpoint: URI): HttpRequest = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(4))
        .GET()
        .build()

    private fun captureProcessMetrics(): ProcessMetrics {
        val pid = ProcessHandle.current().pid().toString()
        val process = ProcessBuilder("ps", "-p", pid, "-o", "rss=,%cpu=,time=").start()
        val values = process.inputStream.bufferedReader().readText().trim().split(Regex("\\s+"))
        assertEquals(0, process.waitFor(), "ps measurement failed")
        return ProcessMetrics(values[0].toLong(), values[1].toDouble(), values[2], processCpuNanos())
    }

    private fun processCpuNanos(): Long = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean)
        .processCpuTime

    private fun matchesListFixture(): String = checkNotNull(
        ProtectedRouteLoadBenchmarkTest::class.java.getResource("/fixtures/matches/upcoming.html"),
    ).readText()

    private fun matchesDetailFixture(): String = checkNotNull(
        ProtectedRouteLoadBenchmarkTest::class.java.getResource("/fixtures/matches/detail-completed.html"),
    ).readText()

    private fun renderReport(
        idle: ProcessMetrics,
        afterNormal: ProcessMetrics,
        finalMetrics: ProcessMetrics,
        normal: NormalResult,
        burst: BurstResult,
        saturation: SaturationResult,
        peaks: Peaks,
        observability: PublicApiObservability.Snapshot,
        upstreamFetches: Int,
        recoveryFetchCost: Int,
    ): String = buildString {
        appendLine("issue52.protectedRouteLoad.version=1")
        appendLine("scope=one local JVM test worker hosting one loopback Netty API")
        appendLine("transport=supplied fixture-only transport; no external HTTP/DNS/VLR.GG request")
        appendLine("transport.notMeasured=production CIO transport, upstream network, TLS, DNS, and provider egress")
        appendLine("process.topology=one JVM only; this is not two-process I8 evidence")
        appendLine("cgroup.notMeasured=macOS local-harness ps RSS is not Linux cgroup/container memory evidence and does not prove a 512MiB deployment")
        appendLine("harness.overhead=RSS/CPU include JUnit, Netty API, fixture parser, Java HTTP client, sampler, and benchmark bookkeeping")
        appendLine("rss.sampling=macOS local-harness ps RSS sampled every ${SAMPLE_PERIOD_MILLIS}ms; it is not Linux cgroup measurement")
        appendLine("java.version=${System.getProperty("java.version")}")
        appendLine("java.vendor=${System.getProperty("java.vendor")}")
        appendLine("fixture.upstream.delayMillis=$UPSTREAM_DELAY_MILLIS")
        appendLine("idle.settleMillis=$IDLE_SETTLE_MILLIS")
        appendLine("idle.rssKiB=${idle.rssKiB}")
        appendLine("idle.psCpuPercent=${idle.psCpuPercent}")
        appendLine("normal.requests=$NORMAL_REQUESTS")
        appendLine("normal.elapsedMillis=${millis(normal.elapsedNanos)}")
        appendLine("normal.rateReqPerSecond=${NORMAL_REQUESTS / seconds(normal.elapsedNanos)}")
        appendLine("normal.cpuPercent=${percent(normal.cpuNanos, normal.elapsedNanos)}")
        appendLine("normal.requestMillis=${normal.requestNanos.joinToString(",") { millis(it).toString() }}")
        appendLine("normal.upstreamFetchCost=${normal.fetchCost}")
        appendLine("normal.after.rssKiB=${afterNormal.rssKiB}")
        appendLine("burst.tokenRefillMillis=$UPSTREAM_REFILL_MILLIS")
        appendLine("burst.detailIds=${BURST_DETAIL_IDS.joinToString(",")}")
        appendLine("burst.requests=$BURST_REQUESTS")
        appendLine("burst.elapsedMillis=${millis(burst.elapsedNanos)}")
        appendLine("burst.upstreamFetchCost=${burst.fetchCost}")
        appendLine("burst.contract=four distinct valid detail IDs; all 200; exactly four fixture fetches after refill")
        appendLine("saturation.targetRateReqPerSecond=$SATURATION_RATE_PER_SECOND")
        appendLine("saturation.targetDurationSeconds=$SATURATION_DURATION_SECONDS")
        appendLine("saturation.uniqueCanonicalWorkload=$SATURATION_REQUESTS")
        appendLine("saturation.clientMaxInFlight=$CLIENT_MAX_IN_FLIGHT")
        appendLine("saturation.clientQueue=0; full client slots are generatorDropped")
        appendLine("saturation.elapsedMillis=${millis(saturation.elapsedNanos)}")
        appendLine("saturation.cpuPercent=${percent(saturation.cpuNanos, saturation.elapsedNanos)}")
        appendLine("saturation.submitted=${saturation.submitted}")
        appendLine("saturation.generatorDropped=${saturation.generatorDropped}")
        appendLine("saturation.clientFailures=${saturation.clientFailures}")
        appendLine("saturation.http200=${saturation.http200}")
        appendLine("saturation.http429=${saturation.http429}")
        appendLine("saturation.http503=${saturation.http503}")
        appendLine("saturation.httpOther=${saturation.httpOther}")
        appendLine("saturation.clientObservedSuccess=${saturation.http200}")
        appendLine("saturation.clientObservedProtectedRejection=${saturation.http429 + saturation.http503}")
        appendLine("saturation.apiAdmissionCount=not-directly-exposed; use status/rejection counters without treating 503 as a unique admission decision")
        appendLine("saturation.p95RejectLatencyMillis=${saturation.p95RejectLatencyMillis}")
        appendLine("saturation.p95RejectLatencyStatuses=429,503")
        appendLine("saturation.p95RejectLatencyTargetMillis=200")
        appendLine("saturation.upstreamFetches=${saturation.upstreamFetches}")
        appendLine("saturation.upstreamFetchLimit=${saturationUpstreamFetchLimit()}")
        appendLine("saturation.upstreamFetchLimitFormula=upstreamBurst + ceil(durationSeconds * upstreamRatePerSecond)")
        appendLine("response.successBytes.total=${saturation.successBytes.total}")
        appendLine("response.successBytes.min=${saturation.successBytes.minOrZero()}")
        appendLine("response.successBytes.max=${saturation.successBytes.max}")
        appendLine("response.errorBytes.total=${saturation.errorBytes.total}")
        appendLine("response.errorBytes.min=${saturation.errorBytes.minOrZero()}")
        appendLine("response.errorBytes.max=${saturation.errorBytes.max}")
        appendLine("protection.requests=${observability.requests}")
        appendLine("protection.statusClasses=${observability.statusClasses.joinToString(",")}")
        appendLine("protection.rejections=${observability.rejections.entries.joinToString(",") { "${it.key.name}:${it.value}" }}")
        appendLine("protection.peakActiveApi=${peaks.activeApi.get()}")
        appendLine("protection.maxActiveApi=$MAX_ACTIVE_API_REQUESTS")
        appendLine("protection.peakActiveUpstream=${peaks.activeUpstream.get()}")
        appendLine("protection.maxActiveUpstream=$MAX_ACTIVE_UPSTREAM_REQUESTS")
        appendLine("protection.maxInFlightCanonicalUrls=$MAX_IN_FLIGHT_CANONICAL_URLS")
        appendLine("protection.queuedWork=0; production protection uses non-waiting semaphore admission")
        appendLine("protection.finalActiveApi=${observability.activeApi}")
        appendLine("protection.finalActiveUpstream=${observability.activeUpstream}")
        appendLine("protection.singleFlightJoins=${observability.singleFlightJoins}")
        appendLine("fixture.totalFetches=$upstreamFetches")
        appendLine("recovery.status=200")
        appendLine("recovery.upstreamFetchCost=$recoveryFetchCost")
        appendLine("final.rssKiB=${finalMetrics.rssKiB}")
        appendLine("final.psCpuPercent=${finalMetrics.psCpuPercent}")
        appendLine("final.processCpuNanos=${finalMetrics.processCpuNanos}")
    }

    private data class ProcessMetrics(val rssKiB: Long, val psCpuPercent: Double, val psCpuTime: String, val processCpuNanos: Long)
    private data class NormalResult(
        val elapsedNanos: Long,
        val cpuNanos: Long,
        val requestNanos: List<Long>,
        val fetchCost: Int,
    )
    private data class BurstResult(val elapsedNanos: Long, val fetchCost: Int)
    private data class SaturationResult(
        val elapsedNanos: Long,
        val cpuNanos: Long,
        val submitted: Int,
        val generatorDropped: Int,
        val clientFailures: Int,
        val http200: Int,
        val http429: Int,
        val http503: Int,
        val httpOther: Int,
        val p95RejectLatencyMillis: String,
        val upstreamFetches: Int,
        val successBytes: ByteSummary,
        val errorBytes: ByteSummary,
    )

    private class DelayedFixtureTransport(
        private val listHtml: String,
        private val detailHtml: String,
    ) : UpstreamHtmlTransport {
        val fetches = AtomicInteger()
        override suspend fun get(url: Url): String {
            fetches.incrementAndGet()
            delay(UPSTREAM_DELAY_MILLIS)
            return if (url.encodedPath == "/matches") listHtml else detailHtml
        }
    }

    private class Peaks(private val observability: PublicApiObservability) {
        val activeApi = AtomicInteger()
        val activeUpstream = AtomicInteger()
        fun sample() {
            val snapshot = observability.snapshot()
            activeApi.accumulateAndGet(snapshot.activeApi, ::maxOf)
            activeUpstream.accumulateAndGet(snapshot.activeUpstream, ::maxOf)
        }
    }

    private class SaturationCounters {
        val submitted = AtomicInteger()
        val generatorDropped = AtomicInteger()
        val clientFailures = AtomicInteger()
        private val http200 = AtomicInteger()
        private val http429 = AtomicInteger()
        private val http503 = AtomicInteger()
        private val httpOther = AtomicInteger()
        private val rejectionNanos = Collections.synchronizedList(mutableListOf<Long>())
        val successBytes = ByteSummary()
        val errorBytes = ByteSummary()

        fun record(status: Int, bytes: Int, elapsedNanos: Long) {
            if (status == 200) {
                http200.incrementAndGet()
                successBytes.add(bytes)
            } else {
                errorBytes.add(bytes)
                when (status) {
                    429 -> http429.incrementAndGet()
                    503 -> http503.incrementAndGet()
                    else -> httpOther.incrementAndGet()
                }
                if (status == 429 || status == 503) rejectionNanos += elapsedNanos
            }
        }

        fun finish(elapsedNanos: Long, cpuNanos: Long, upstreamFetches: Int): SaturationResult = SaturationResult(
            elapsedNanos = elapsedNanos,
            cpuNanos = cpuNanos,
            submitted = submitted.get(),
            generatorDropped = generatorDropped.get(),
            clientFailures = clientFailures.get(),
            http200 = http200.get(),
            http429 = http429.get(),
            http503 = http503.get(),
            httpOther = httpOther.get(),
            p95RejectLatencyMillis = rejectionP95Millis(),
            upstreamFetches = upstreamFetches,
            successBytes = successBytes.snapshot(),
            errorBytes = errorBytes.snapshot(),
        )

        private fun rejectionP95Millis(): String = synchronized(rejectionNanos) {
            if (rejectionNanos.isEmpty()) return@synchronized "not-observed"
            val ordered = rejectionNanos.sorted()
            millis(ordered[(ceil(ordered.size * 0.95).toInt() - 1).coerceAtLeast(0)]).toString()
        }
    }

    private class ByteSummary {
        private val count = AtomicLong()
        private val bytes = AtomicLong()
        private val min = AtomicLong(Long.MAX_VALUE)
        private val maximum = AtomicLong()
        val total: Long get() = bytes.get()
        val max: Long get() = maximum.get()
        fun minOrZero(): Long = min.get().takeUnless { it == Long.MAX_VALUE } ?: 0
        fun add(value: Int) {
            count.incrementAndGet()
            bytes.addAndGet(value.toLong())
            min.accumulateAndGet(value.toLong(), ::minOf)
            maximum.accumulateAndGet(value.toLong(), ::maxOf)
        }
        fun snapshot(): ByteSummary = ByteSummary().also { copy ->
            copy.count.set(count.get()); copy.bytes.set(bytes.get()); copy.min.set(min.get()); copy.maximum.set(maximum.get())
        }
    }

    private companion object {
        const val REPORT_PATH_ENV = "PROTECTED_ROUTE_LOAD_REPORT_PATH"
        const val LOOPBACK = "127.0.0.1"
        const val UPCOMING_PATH = "/api/v1/matches/upcoming"
        const val UPSTREAM_DELAY_MILLIS = 1_000L
        const val IDLE_SETTLE_MILLIS = 5_000L
        const val NORMAL_REQUESTS = 5
        const val NORMAL_INTERVAL_NANOS = 5_000_000_000L
        const val BURST_REQUESTS = 4
        val BURST_DETAIL_IDS = listOf("709685", "709686", "709687", "709688")
        const val UPSTREAM_REQUESTS_PER_SECOND = 2
        const val UPSTREAM_BURST = 4
        const val UPSTREAM_REFILL_MILLIS = UPSTREAM_BURST * 1_000L / UPSTREAM_REQUESTS_PER_SECOND
        const val SATURATION_RATE_PER_SECOND = 100
        const val SATURATION_DURATION_SECONDS = 60
        const val SATURATION_REQUESTS = SATURATION_RATE_PER_SECOND * SATURATION_DURATION_SECONDS
        const val SATURATION_INTERVAL_NANOS = 1_000_000_000L / SATURATION_RATE_PER_SECOND
        const val CLIENT_MAX_IN_FLIGHT = 16
        const val MAX_ACTIVE_API_REQUESTS = 8
        const val MAX_ACTIVE_UPSTREAM_REQUESTS = 4
        const val MAX_IN_FLIGHT_CANONICAL_URLS = 4
        const val SAMPLE_PERIOD_MILLIS = 250L
        const val QUIESCENCE_TIMEOUT_NANOS = 10_000_000_000L

        fun millis(nanos: Long): Double = nanos / 1_000_000.0
        fun seconds(nanos: Long): Double = nanos / 1_000_000_000.0
        fun percent(cpuNanos: Long, elapsedNanos: Long): Double = cpuNanos * 100.0 / elapsedNanos
        fun saturationUpstreamFetchLimit(): Int = UPSTREAM_BURST + ceil(
            SATURATION_DURATION_SECONDS.toDouble() * UPSTREAM_REQUESTS_PER_SECOND,
        ).toInt()
    }
}

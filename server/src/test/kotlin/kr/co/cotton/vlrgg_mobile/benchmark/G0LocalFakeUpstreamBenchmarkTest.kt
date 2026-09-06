package kr.co.cotton.vlrgg_mobile.benchmark

import com.sun.management.OperatingSystemMXBean
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in local G0 measurement seam. It is deliberately separate from production routes:
 * current production upstream URLs are fixed to VLR.GG and this benchmark must never load them.
 */
class G0LocalFakeUpstreamBenchmarkTest {

    @Test
    fun `records bounded fake upstream local baseline`() {
        val reportPath = System.getenv(REPORT_PATH_ENV)
        assumeTrue("$REPORT_PATH_ENV must be set for the opt-in local benchmark.", !reportPath.isNullOrBlank())
        val evidencePath = checkNotNull(reportPath)
        val upstreamRequests = AtomicInteger()
        val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build()
        val upstream = embeddedServer(Netty, host = LOOPBACK, port = 0) {
            routing {
                get("/fixture") {
                    upstreamRequests.incrementAndGet()
                    delay(UPSTREAM_DELAY_MILLIS)
                    call.respondText("fixture")
                }
            }
        }.start(wait = false)

        val upstreamPort = runBlocking { upstream.engine.resolvedConnectors().single().port }
        val api = embeddedServer(Netty, host = LOOPBACK, port = 0) {
            routing {
                get("/benchmark") {
                    val response = client.send(
                        HttpRequest.newBuilder(URI("http://$LOOPBACK:$upstreamPort/fixture")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                    call.respondText(response.body())
                }
            }
        }.start(wait = false)

        try {
            val apiPort = runBlocking { api.engine.resolvedConnectors().single().port }
            val endpoint = URI("http://$LOOPBACK:$apiPort/benchmark")
            val idle = captureMetrics()

            val pacedStart = System.nanoTime()
            val pacedCpuStart = processCpuNanos()
            val pacedDurations = (0 until PACED_REQUESTS).map { requestIndex ->
                val requestStart = System.nanoTime()
                assertEquals(200, client.send(request(endpoint), HttpResponse.BodyHandlers.ofString()).statusCode())
                val responseElapsed = System.nanoTime() - requestStart
                val nextSlot = pacedStart + (requestIndex + 1L) * PACED_INTERVAL_NANOS
                val remaining = nextSlot - System.nanoTime()
                if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining)
                responseElapsed
            }
            val pacedElapsed = System.nanoTime() - pacedStart
            val pacedCpu = processCpuNanos() - pacedCpuStart
            assertEquals(PACED_REQUESTS, upstreamRequests.get())
            val paced = captureMetrics()

            val beforeBurst = upstreamRequests.get()
            val burstStart = System.nanoTime()
            val burstCpuStart = processCpuNanos()
            val gate = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(BURST_REQUESTS)
            try {
                val futures = (1..BURST_REQUESTS).map {
                    executor.submit<Int> {
                        gate.await(2, TimeUnit.SECONDS)
                        client.send(request(endpoint), HttpResponse.BodyHandlers.ofString()).statusCode()
                    }
                }
                gate.countDown()
                futures.forEach { assertEquals(200, it.get(5, TimeUnit.SECONDS)) }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            }
            val burstElapsed = System.nanoTime() - burstStart
            val burstCpu = processCpuNanos() - burstCpuStart
            val burstUpstreamCost = upstreamRequests.get() - beforeBurst
            assertEquals(BURST_REQUESTS, burstUpstreamCost)
            val burst = captureMetrics()

            Files.writeString(
                Path.of(evidencePath),
                renderReport(
                    idle = idle,
                    paced = paced,
                    burst = burst,
                    pacedElapsedNanos = pacedElapsed,
                    pacedCpuNanos = pacedCpu,
                    pacedDurations = pacedDurations,
                    burstElapsedNanos = burstElapsed,
                    burstCpuNanos = burstCpu,
                    totalUpstreamRequests = upstreamRequests.get(),
                    burstUpstreamCost = burstUpstreamCost,
                ),
            )
        } finally {
            api.stop(1_000, 3_000)
            upstream.stop(1_000, 3_000)
        }
    }

    private fun request(endpoint: URI): HttpRequest = HttpRequest.newBuilder(endpoint)
        .timeout(java.time.Duration.ofSeconds(4))
        .GET()
        .build()

    private fun captureMetrics(): Metrics {
        val pid = ProcessHandle.current().pid().toString()
        val ps = ProcessBuilder("ps", "-p", pid, "-o", "rss=,%cpu=,etime=,time=").start()
        val output = ps.inputStream.bufferedReader().readText().trim().split(Regex("\\s+"))
        assertEquals(0, ps.waitFor())
        return Metrics(
            rssKiB = output[0].toLong(),
            psCpuPercent = output[1].toDouble(),
            elapsed = output[2],
            cpuTime = output[3],
        )
    }

    private fun processCpuNanos(): Long = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean)
        .processCpuTime

    private fun renderReport(
        idle: Metrics,
        paced: Metrics,
        burst: Metrics,
        pacedElapsedNanos: Long,
        pacedCpuNanos: Long,
        pacedDurations: List<Long>,
        burstElapsedNanos: Long,
        burstCpuNanos: Long,
        totalUpstreamRequests: Int,
        burstUpstreamCost: Int,
    ): String = buildString {
        appendLine("java.version=${System.getProperty("java.version")}")
        appendLine("java.vendor=${System.getProperty("java.vendor")}")
        appendLine("process.pid=${ProcessHandle.current().pid()}")
        appendLine("synthetic.upstream.delayMillis=$UPSTREAM_DELAY_MILLIS")
        appendLine("idle.ps=$idle")
        appendLine("paced.requests=$PACED_REQUESTS")
        appendLine("paced.elapsedMillis=${pacedElapsedNanos / 1_000_000.0}")
        appendLine("paced.rateReqPerSecond=${PACED_REQUESTS / (pacedElapsedNanos / 1_000_000_000.0)}")
        appendLine("paced.cpuNanos=$pacedCpuNanos")
        appendLine("paced.cpuPercent=${percent(pacedCpuNanos, pacedElapsedNanos)}")
        appendLine("paced.requestMillis=${pacedDurations.joinToString(",") { (it / 1_000_000.0).toString() }}")
        appendLine("paced.ps=$paced")
        appendLine("burst.requests=$BURST_REQUESTS")
        appendLine("burst.elapsedMillis=${burstElapsedNanos / 1_000_000.0}")
        appendLine("burst.cpuNanos=$burstCpuNanos")
        appendLine("burst.cpuPercent=${percent(burstCpuNanos, burstElapsedNanos)}")
        appendLine("burst.upstreamCost=$burstUpstreamCost")
        appendLine("total.upstreamRequests=$totalUpstreamRequests")
        appendLine("burst.ps=$burst")
    }

    private fun percent(cpuNanos: Long, elapsedNanos: Long): Double = cpuNanos * 100.0 / elapsedNanos

    private data class Metrics(
        val rssKiB: Long,
        val psCpuPercent: Double,
        val elapsed: String,
        val cpuTime: String,
    )

    private companion object {
        const val REPORT_PATH_ENV = "G0_BENCH_REPORT_PATH"
        const val LOOPBACK = "127.0.0.1"
        const val UPSTREAM_DELAY_MILLIS = 1_000L
        const val PACED_REQUESTS = 5
        const val PACED_INTERVAL_NANOS = 5_000_000_000L
        const val BURST_REQUESTS = 4
    }
}

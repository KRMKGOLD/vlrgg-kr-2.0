package kr.co.cotton.vlrgg_mobile.benchmark

import com.sun.management.GarbageCollectionNotificationInfo
import io.ktor.http.Url
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.management.NotificationEmitter
import javax.management.openmbean.CompositeData
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.cache.DEFAULT_RESPONSE_CACHE_MAX_BYTES
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheConfig
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.module
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig

/**
 * Child-JVM probe for [ResponseCacheMemoryBenchmarkTest]; run it only with the production heap flags
 * (`-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`). It serves the real `module` on loopback Netty with a fixture
 * transport, so nothing leaves the machine.
 *
 * Phases: fill the response cache past its 32 MiB cap with distinct match-list pages, then hold
 * [SLOW_CONSUMERS] responses of other distinct pages open by clients that stop reading, and meanwhile run
 * [LOAD_ROUNDS] rounds of [CONCURRENT_LOADS] concurrent misses of further distinct pages. Concurrency limits are the
 * production defaults (upstream work 8, fetch 8, in-flight keys 8, Netty running limit 32); only token rates are raised
 * so the run is not paced by refill. Every fetch allocates a fresh copy of the page, as a real upstream read does.
 *
 * Args: page size in bytes, output file, then optionally the cache capacity in bytes (default 32 MiB) and the VLR.GG
 * fetch concurrency and in-flight key limit (default 8), for sensitivity runs; capacity 0 disables the cache. Writes `key=value` lines.
 */
fun main(args: Array<String>) {
    val pageBytes = args[0].toInt()
    val output = Path.of(args[1])
    val cacheBytes = args.getOrNull(2)?.toInt() ?: DEFAULT_RESPONSE_CACHE_MAX_BYTES
    val fetchConcurrency = args.getOrNull(3)?.toInt() ?: PublicApiProtectionConfig().maxActiveUpstreamRequests
    val heap = HeapPeaks().also { it.start() }

    val page = buildMatchesListHtml(pageBytes).toByteArray(Charsets.UTF_8)
    val fetches = AtomicInteger()
    val transport = object : UpstreamHtmlTransport {
        override suspend fun get(url: Url): String {
            fetches.incrementAndGet()
            return String(page.copyOf(), Charsets.UTF_8)
        }
    }
    val unpaced = 100_000
    val config = PublicApiProtectionConfig(
        apiRequestsPerSecond = unpaced,
        apiBurst = unpaced,
        upstreamRequestsPerSecond = unpaced,
        upstreamBurst = unpaced,
        requestsPerSecond = unpaced,
        requestBurst = unpaced,
        maxActiveUpstreamRequests = fetchConcurrency,
        maxInFlightCanonicalUrls = fetchConcurrency,
    )
    val server = embeddedServer(Netty, host = LOOPBACK, port = 0) {
        module(protectionConfig = config, responseCacheConfig = if (cacheBytes == 0) ResponseCacheConfig.DISABLED else ResponseCacheConfig(maxBytes = cacheBytes), upstreamTransport = transport)
    }.start(wait = false)
    val port = runBlocking { server.engine.resolvedConnectors().single().port }
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    val nextPage = AtomicInteger(2)
    val statuses = ConcurrentHashMap<Int, AtomicInteger>()
    fun get(pageNumber: Int): HttpResponse<ByteArray> = client.send(
        HttpRequest.newBuilder(URI("http://$LOOPBACK:$port/api/v1/matches/results?page=$pageNumber"))
            .timeout(Duration.ofSeconds(30)).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray(),
    ).also { statuses.computeIfAbsent(it.statusCode()) { AtomicInteger() }.incrementAndGet() }

    val first = get(nextPage.getAndIncrement())
    check(first.statusCode() == 200) { "fixture page was not served: ${first.statusCode()}" }
    val jsonBytes = first.body().size
    // A disabled cache (capacity 0) skips the fill; the run then measures the same traffic without any cache.
    val prefillPages = if (cacheBytes == 0) 0 else (cacheBytes * 1.2 / jsonBytes).toInt() + 1
    repeat(prefillPages) { check(get(nextPage.getAndIncrement()).statusCode() == 200) }
    heap.reset()

    val slowReady = CountDownLatch(SLOW_CONSUMERS)
    val releaseSlow = CountDownLatch(1)
    val slowExecutor = Executors.newFixedThreadPool(SLOW_CONSUMERS)
    val slowHeld = AtomicInteger()
    repeat(SLOW_CONSUMERS) {
        val pageNumber = nextPage.getAndIncrement()
        slowExecutor.execute {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (System.nanoTime() < deadline) {
                val socket = Socket()
                socket.receiveBufferSize = 1024
                socket.connect(InetSocketAddress(LOOPBACK, port), 5_000)
                socket.getOutputStream().write(
                    "GET /api/v1/matches/results?page=$pageNumber HTTP/1.1\r\nHost: $LOOPBACK\r\n\r\n".toByteArray(),
                )
                val statusLine = ByteArray(12)
                var read = 0
                while (read < statusLine.size) {
                    val n = socket.getInputStream().read(statusLine, read, statusLine.size - read)
                    if (n < 0) break
                    read += n
                }
                if (String(statusLine, 0, read).endsWith("200")) {
                    slowHeld.incrementAndGet()
                    slowReady.countDown()
                    // Stops reading: the response stays queued in the server until the socket closes.
                    releaseSlow.await()
                    socket.close()
                    return@execute
                }
                socket.close()
                Thread.sleep(10)
            }
            slowReady.countDown()
        }
    }
    slowReady.await(90, TimeUnit.SECONDS)

    val loadExecutor = Executors.newFixedThreadPool(CONCURRENT_LOADS)
    repeat(LOAD_ROUNDS) {
        val round = List(CONCURRENT_LOADS) {
            val pageNumber = nextPage.getAndIncrement()
            loadExecutor.submit {
                // A 503 means the eight upstream work slots were busy; retry like a client would.
                repeat(200) { if (get(pageNumber).statusCode() != 503) return@submit; Thread.sleep(5) }
            }
        }
        round.forEach { it.get(120, TimeUnit.SECONDS) }
    }
    val peakUsed = heap.peakUsed.get()
    val peakAfterGc = heap.peakAfterGc.get()
    val peakDirect = heap.peakDirect.get()
    val collections = heap.collections.get()
    releaseSlow.countDown()
    slowExecutor.shutdown()
    loadExecutor.shutdown()

    val maxHeap = Runtime.getRuntime().maxMemory()
    Files.writeString(
        output,
        buildString {
            appendLine("pageBytes=${page.size}")
            appendLine("cacheMaxBytes=$cacheBytes")
            appendLine("fetchConcurrencyAndKeys=$fetchConcurrency")
            appendLine("jsonBytes=$jsonBytes")
            appendLine("prefillPages=$prefillPages")
            appendLine("slowConsumersHeld=${slowHeld.get()}")
            appendLine("concurrentLoads=$CONCURRENT_LOADS x $LOAD_ROUNDS rounds")
            appendLine("fetches=${fetches.get()}")
            appendLine("statuses=${statuses.toSortedMap().map { (code, count) -> "$code:${count.get()}" }.joinToString(",")}")
            appendLine("maxHeapBytes=$maxHeap")
            appendLine("peakHeapUsedBytes=$peakUsed")
            appendLine("peakHeapAfterGcBytes=$peakAfterGc")
            appendLine("peakDirectBufferBytes=$peakDirect")
            appendLine("collectionsDuringWindow=$collections")
            appendLine("peakHeapUsedPercent=${"%.1f".format(peakUsed * 100.0 / maxHeap)}")
            appendLine("peakHeapAfterGcPercent=${"%.1f".format(peakAfterGc * 100.0 / maxHeap)}")
            appendLine("oom=false")
        },
    )
    exitProcess(0)
}

/** One date group whose card holds as many match rows as fit in [targetBytes], each padded like a real row. */
internal fun buildMatchesListHtml(targetBytes: Int): String = buildString(targetBytes + 4096) {
    append("<!doctype html><html><body>")
    append("<div class=\"wf-label mod-large\">Mon, July 13, 2026 <span class=\"wf-tag\">Today</span></div>")
    append("<div class=\"wf-card\">")
    var index = 0
    while (length < targetBytes - 2_400) {
        val id = 700_000 + index++
        append("<a href=\"/$id/team-alpha-vs-team-beta\" class=\"wf-module-item match-item\">")
        append("<div class=\"match-item-time\">5:00 PM</div><div class=\"match-item-vs\">")
        append("<div class=\"match-item-vs-team\"><div class=\"match-item-vs-team-name\"><div class=\"text-of\">Team Alpha $id</div></div><div class=\"match-item-vs-team-score\">2</div></div>")
        append("<div class=\"match-item-vs-team\"><div class=\"match-item-vs-team-name\"><div class=\"text-of\">Team Beta $id</div></div><div class=\"match-item-vs-team-score\">1</div></div>")
        append("</div><div class=\"match-item-eta\"><div class=\"ml\"><div class=\"ml-status\">Completed</div><div class=\"ml-eta\">2h</div></div></div>")
        append("<div class=\"match-item-event text-of\"><div class=\"match-item-icon\"><img src=\"//owcdn.net/img/events/challengers-korea.png\"></div>")
        append("<div class=\"match-item-event-series text-of\">Playoffs&ndash;Lower Final</div>Challengers 2026: Korea WDG Split 2</div>")
        // Markup the parser ignores, so a row weighs about as much as a real results row (~2.2 KB).
        repeat(12) { append("<div class=\"match-item-vod\"><span class=\"mod-filler\">vod link $it</span></div>") }
        append("</a>")
    }
    append("</div></body></html>")
}

/**
 * Raw heap peak sampled every 2 ms (includes garbage G1 has not collected yet), the peak heap occupancy right after a
 * young or full collection (survivor + old, i.e. what survived: the live-set estimate), and the direct buffer peak.
 * G1's concurrent-cycle notifications are skipped because they report an uncollected Eden.
 */
private class HeapPeaks {
    val peakUsed = AtomicLong()
    val peakAfterGc = AtomicLong()
    val peakDirect = AtomicLong()
    val collections = AtomicInteger()

    fun start() {
        val memory = ManagementFactory.getMemoryMXBean()
        val direct = ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean::class.java)
            .filter { it.name == "direct" }
        Thread {
            while (true) {
                peakUsed.accumulateAndGet(memory.heapMemoryUsage.used, ::maxOf)
                peakDirect.accumulateAndGet(direct.sumOf { it.memoryUsed }, ::maxOf)
                Thread.sleep(2)
            }
        }.apply { isDaemon = true }.start()
        val heapPools = ManagementFactory.getMemoryPoolMXBeans()
            .filter { it.type == MemoryType.HEAP && !it.name.contains("Eden") }
            .map { it.name }.toSet()
        ManagementFactory.getGarbageCollectorMXBeans().forEach { gc ->
            (gc as NotificationEmitter).addNotificationListener({ notification, _ ->
                if (notification.type != GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION) return@addNotificationListener
                val info = GarbageCollectionNotificationInfo.from(notification.userData as CompositeData)
                if (info.gcName.contains("Concurrent")) return@addNotificationListener
                collections.incrementAndGet()
                val after = info.gcInfo.memoryUsageAfterGc.filterKeys { it in heapPools }.values.sumOf { it.used }
                peakAfterGc.accumulateAndGet(after, ::maxOf)
            }, null, null)
        }
    }

    fun reset() {
        peakUsed.set(0)
        peakAfterGc.set(0)
        peakDirect.set(0)
        collections.set(0)
    }
}

private const val LOOPBACK = "127.0.0.1"
private const val SLOW_CONSUMERS = 24
private const val CONCURRENT_LOADS = 8
private const val LOAD_ROUNDS = 3

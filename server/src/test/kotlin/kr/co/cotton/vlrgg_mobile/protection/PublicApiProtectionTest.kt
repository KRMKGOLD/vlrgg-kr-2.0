package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.Url
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kr.co.cotton.vlrgg_mobile.common.http.RateLimitedFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerBusyFailure
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.PublicRouteClass
import kr.co.cotton.vlrgg_mobile.plugins.FailureDiagnostics
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kotlin.test.*

class PublicApiProtectionTest {
    private val testScopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun cancelTestScopes() {
        testScopes.forEach { it.cancel() }
        testScopes.clear()
    }

    @Test
    fun `api burst is capped and refills from a monotonic test clock`() = runBlocking {
        val clock = TestClock()
        val protection = protection(clock, apiBurst = 2, apiRate = 10)

        repeat(2) { protection.admitApi {} }
        assertSuspendFailsWith<RateLimitedFailure> { protection.admitApi {} }

        clock.advanceBy(100)
        protection.admitApi {}
    }

    @Test
    fun `same canonical in flight fetch is shared and completion is not cached`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val fetches = AtomicInteger()
        val protection = protection(TestClock())
        val url = Url("https://www.vlr.gg/matches")
        val first = async { protection.getHtml(url) { fetches.incrementAndGet(); started.complete(Unit); gate.await(); "html" } }
        started.await()
        val second = async { protection.getHtml(url) { fetches.incrementAndGet(); gate.await(); "html" } }
        yield()
        assertEquals(1, fetches.get())
        gate.complete(Unit)
        assertEquals("html", first.await())
        assertEquals("html", second.await())

        assertEquals("new", protection.getHtml(url) { fetches.incrementAndGet(); "new" })
        assertEquals(2, fetches.get())
    }

    @Test
    fun `unique canonical key over capacity is rejected without queuing`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val protection = protection(TestClock(), maxKeys = 1)
        val first = async { protection.getHtml(Url("https://www.vlr.gg/a")) { gate.await(); "a" } }
        yield()
        assertSuspendFailsWith<ServerBusyFailure> {
            protection.getHtml(Url("https://www.vlr.gg/b")) { "b" }
        }
        gate.complete(Unit)
        assertEquals("a", first.await())
    }

    @Test
    fun `one cancelled waiter leaves the shared fetch alive and last waiter cancels it`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val protection = protection(TestClock())
        val url = Url("https://www.vlr.gg/cancellation")
        val first = async {
            protection.getHtml(url) {
                try { started.complete(Unit); gate.await(); "html" } finally { if (!gate.isCompleted) cancelled.complete(Unit) }
            }
        }
        started.await()
        val second = async { protection.getHtml(url) { error("must join the first fetch") } }
        yield()
        first.cancelAndJoin()
        gate.complete(Unit)
        assertEquals("html", second.await())

        val lastStarted = CompletableDeferred<Unit>()
        val last = async {
            protection.getHtml(Url("https://www.vlr.gg/last")) {
                try { lastStarted.complete(Unit); awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        lastStarted.await()
        last.cancelAndJoin()
        cancelled.await()
    }

    @Test
    fun `nested timeout cancellation is not translated into the public request deadline`() = runBlocking {
        try {
            withWholeRequestDeadline(1_000) {
                withTimeout(1) { awaitCancellation() }
            }
            fail("Expected the nested timeout to propagate")
        } catch (_: TimeoutCancellationException) {
            // The outer 15-second boundary did not own this cancellation.
        }
    }

    @Test
    fun `varied attacker paths keep observability cardinality and sampled output bounded`() {
        val summaries = mutableListOf<PublicApiObservability.Snapshot>()
        val observability = PublicApiObservability({ summaries.add(it) }) { 0L }

        repeat(10_000) { index ->
            assertEquals(PublicRouteClass.OTHER, observability.routeClass("/unknown/$index?token=$index"))
            observability.requestStarted(PublicRouteClass.OTHER)
            observability.completed(status = 404, elapsedMillis = 1)
        }

        val snapshot = observability.snapshot()
        assertEquals(10_000L, snapshot.requests)
        assertEquals(10_000L, snapshot.statusClasses[4])
        assertEquals(10_000L, snapshot.routeClasses[PublicRouteClass.OTHER.ordinal])
        assertEquals(ApiErrorCode.entries.size, snapshot.rejections.size)
        assertEquals(1, summaries.size)
    }

    @Test
    fun `concurrent completion flood with a frozen clock emits one aggregate summary`() = runBlocking {
        val summaries = java.util.concurrent.ConcurrentLinkedQueue<PublicApiObservability.Snapshot>()
        val observability = PublicApiObservability({ summaries.add(it) }) { 0L }

        List(8) {
            async(Dispatchers.Default) {
                repeat(2_048) {
                    observability.requestStarted(PublicRouteClass.OTHER)
                    observability.completed(429, 1)
                }
            }
        }.awaitAll()

        assertEquals(1, summaries.size)
        assertEquals(16_384L, observability.snapshot().statusClasses[4])
    }

    @Test
    fun `low traffic emits on first completion then no more than once per minute`() {
        var now = 0L
        val summaries = mutableListOf<PublicApiObservability.Snapshot>()
        val observability = PublicApiObservability({ summaries.add(it) }) { now }

        observability.requestStarted(PublicRouteClass.API)
        observability.completed(200, 1)
        now = 59_999
        observability.requestStarted(PublicRouteClass.API)
        observability.completed(200, 1)
        now = 60_000
        observability.requestStarted(PublicRouteClass.API)
        observability.completed(200, 1)

        assertEquals(2, summaries.size)
        assertEquals(3L, observability.snapshot().statusClasses[2])
    }

    @Test
    fun `failure diagnostics safely redact details and cap samples per minute`() {
        var now = 0L
        val diagnostics = FailureDiagnostics({ now }, maxSamplesPerWindow = 2)
        val failure = UpstreamNetworkFailure(
            Url("https://user:secret@vlr.gg/private?token=credential-sentinel"),
            IllegalStateException("credential-sentinel"),
        )

        val first = assertNotNull(diagnostics.sample(failure))
        assertEquals("https://www.vlr.gg/", first.canonicalUpstreamUrl)
        assertFalse(first.toString().contains("credential-sentinel"))
        assertNotNull(diagnostics.sample(failure))
        assertNull(diagnostics.sample(failure))
        now = 60_000
        assertNotNull(diagnostics.sample(failure))
    }

    private fun protection(
        clock: TestClock,
        apiRate: Int = 10,
        apiBurst: Int = 20,
        maxKeys: Int = 4,
    ): PublicApiProtection {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        testScopes += scope
        return PublicApiProtection(
        config = PublicApiProtectionConfig(
            apiRequestsPerSecond = apiRate,
            apiBurst = apiBurst,
            maxActiveApiRequests = 8,
            upstreamRequestsPerSecond = 2,
            upstreamBurst = 4,
            maxActiveUpstreamRequests = 4,
            maxInFlightCanonicalUrls = maxKeys,
        ),
        scope = scope,
        clock = clock,
    )
    }

    private class TestClock : MonotonicClock {
        private var now = 0L
        override fun nowMillis(): Long = now
        fun advanceBy(milliseconds: Long) { now += milliseconds }
    }

    private suspend inline fun <reified T : Throwable> assertSuspendFailsWith(crossinline block: suspend () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            assertIs<T>(failure)
            return
        }
        fail("Expected ${T::class.simpleName}")
    }
}

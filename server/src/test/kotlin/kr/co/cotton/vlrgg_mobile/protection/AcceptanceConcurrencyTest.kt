package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.Url
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kr.co.cotton.vlrgg_mobile.common.http.ServerBusyFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.feature.matches.DefaultMatchesService
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchListCategory
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesMapper
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesParser
import kr.co.cotton.vlrgg_mobile.feature.matches.VlrMatchesScraper
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kotlin.test.*

/**
 * Deterministic process-local acceptance checks for the #52 admission and single-flight contract.
 * These exercise the production protection seam; they do not claim cgroup or multi-process proof.
 */
class AcceptanceConcurrencyTest {
    @Test
    fun `API admits exactly eight active calls and releases permits after exception and cancellation`() = runBlocking {
        val protection = protection(apiBurst = 32)
        val entered = List(8) { CompletableDeferred<Unit>() }
        val release = CompletableDeferred<Unit>()
        val active = entered.map { enteredSignal ->
            async(Dispatchers.Default) {
                protection.admitApi {
                    enteredSignal.complete(Unit)
                    release.await()
                }
            }
        }
        entered.forEach { it.await() }

        assertSuspendFailsWith<ServerBusyFailure> { protection.admitApi {} }
        release.complete(Unit)
        active.awaitAll()

        assertSuspendFailsWith<IllegalStateException> { protection.admitApi { throw IllegalStateException("fixture failure") } }
        protection.admitApi {}

        val cancellationEntered = CompletableDeferred<Unit>()
        val cancelled = async(Dispatchers.Default) {
            protection.admitApi {
                cancellationEntered.complete(Unit)
                awaitCancellation()
            }
        }
        cancellationEntered.await()
        cancelled.cancelAndJoin()
        protection.admitApi {}
    }

    @Test
    fun `upstream admits four distinct canonical keys then rejects fifth without a queue and recovers`() = runBlocking {
        val protection = protection(upstreamBurst = 10, maxKeys = 4, maxUpstream = 4)
        val started = List(4) { CompletableDeferred<Unit>() }
        val release = CompletableDeferred<Unit>()
        val active = started.mapIndexed { index, signal ->
            async(Dispatchers.Default) {
                protection.getHtml(Url("https://www.vlr.gg/acceptance/$index")) {
                    signal.complete(Unit)
                    release.await()
                    "html-$index"
                }
            }
        }
        started.forEach { it.await() }

        assertSuspendFailsWith<ServerBusyFailure> {
            protection.getHtml(Url("https://www.vlr.gg/acceptance/overflow")) { "must not queue" }
        }
        release.complete(Unit)
        active.awaitAll()

        assertEquals("recovered", protection.getHtml(Url("https://www.vlr.gg/acceptance/recovered")) { "recovered" })
    }

    @Test
    fun `eight same key waiters consume one upstream token and share one fixture fetch`() = runBlocking {
        val observability = PublicApiObservability()
        val protection = protection(upstreamBurst = 1, maxKeys = 4, observability = observability)
        val fetches = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val url = Url("https://www.vlr.gg/matches")
        val waiters = List(8) {
            async(Dispatchers.Default) {
                protection.getHtml(url) {
                    fetches.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    matchesFixture()
                }
            }
        }
        started.await()
        eventually { observability.snapshot().singleFlightJoins == 7L }

        assertEquals(1, fetches.get())
        assertEquals(1, observability.snapshot().activeUpstream)
        assertSuspendFailsWith<ServerBusyFailure> {
            protection.getHtml(Url("https://www.vlr.gg/a-different-key")) { "must not receive a token" }
        }

        release.complete(Unit)
        assertTrue(waiters.awaitAll().all { it.contains("wf-card") })
        assertEquals(0, observability.snapshot().activeUpstream)
    }

    @Test
    fun `last waiter cancellation and parent shutdown clean up active upstream accounting`() = runBlocking {
        val observability = PublicApiObservability()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Default)
        val protection = protection(scope = scope, observability = observability)
        val started = CompletableDeferred<Unit>()
        val fetchCancelled = CompletableDeferred<Unit>()
        val waiting = async(Dispatchers.Default) {
            protection.getHtml(Url("https://www.vlr.gg/last-waiter")) {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    fetchCancelled.complete(Unit)
                }
            }
        }
        started.await()
        waiting.cancelAndJoin()
        fetchCancelled.await()
        eventually { observability.snapshot().activeUpstream == 0 }

        val shutdownStarted = CompletableDeferred<Unit>()
        val shutdownFetchCancelled = CompletableDeferred<Unit>()
        val shutdownWaiter = async(Dispatchers.Default) {
            protection.getHtml(Url("https://www.vlr.gg/shutdown")) {
                try {
                    shutdownStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    shutdownFetchCancelled.complete(Unit)
                }
            }
        }
        shutdownStarted.await()
        parent.cancel()
        assertSuspendFailsWith<CancellationException> { shutdownWaiter.await() }
        shutdownFetchCancelled.await()
        eventually { observability.snapshot().activeUpstream == 0 }
    }

    @Test
    fun `success failure and completed entry handoff never serve a cached upstream result`() = runBlocking {
        val protection = protection(upstreamBurst = 16)
        val url = Url("https://www.vlr.gg/not-cached")
        var calls = 0

        assertEquals("first", protection.getHtml(url) { calls += 1; "first" })
        assertEquals("second", protection.getHtml(url) { calls += 1; "second" })
        assertEquals(2, calls)

        assertSuspendFailsWith<IllegalStateException> {
            protection.getHtml(Url("https://www.vlr.gg/not-cached-failure")) {
                calls += 1
                throw IllegalStateException("fixture upstream failure")
            }
        }
        assertEquals("fresh-after-failure", protection.getHtml(Url("https://www.vlr.gg/not-cached-failure")) {
            calls += 1
            "fresh-after-failure"
        })

        val completionReached = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            protection.getHtml(Url("https://www.vlr.gg/completed-entry-race")) {
                calls += 1
                completionReached.complete(Unit)
                "completed"
            }
        }
        completionReached.await()
        val replacement = async(Dispatchers.Default) {
            protection.getHtml(Url("https://www.vlr.gg/completed-entry-race")) { calls += 1; "replacement" }
        }
        assertEquals("completed", first.await())
        assertEquals("replacement", replacement.await())
        assertEquals(6, calls)
    }

    @Test
    fun `disallowed upstream target fails before the supplied fetch and protected fixture composition stays local`() = runBlocking {
        val protection = protection()
        var fetches = 0
        assertSuspendFailsWith<UpstreamNetworkFailure> {
            protection.getHtml(Url("http://example.invalid/secret?token=sentinel")) { fetches += 1; "must not fetch" }
        }
        assertEquals(0, fetches)

        val protectedTransport = object : UpstreamHtmlTransport {
            override suspend fun get(url: Url): String {
                fetches += 1
                assertEquals("www.vlr.gg", url.host)
                return matchesFixture()
            }
        }.withPublicProtection(protection)
        val service = DefaultMatchesService(VlrMatchesScraper(protectedTransport), VlrMatchesParser(), MatchesMapper())

        assertEquals("709685", service.getMatches(MatchListCategory.UPCOMING, 1).groups.single().matches.single().id)
        assertEquals("709685", service.getMatches(MatchListCategory.UPCOMING, 1).groups.single().matches.single().id)
        assertEquals(2, fetches)
    }

    private fun protection(
        apiBurst: Int = 32,
        upstreamBurst: Int = 16,
        maxKeys: Int = 4,
        maxUpstream: Int = 4,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        observability: PublicApiObservability = PublicApiObservability(),
    ) = PublicApiProtection(
        config = PublicApiProtectionConfig(
            apiRequestsPerSecond = 100,
            apiBurst = apiBurst,
            maxActiveApiRequests = 8,
            upstreamRequestsPerSecond = 100,
            upstreamBurst = upstreamBurst,
            maxActiveUpstreamRequests = maxUpstream,
            maxInFlightCanonicalUrls = maxKeys,
        ),
        scope = scope,
        observability = observability,
    )

    private suspend fun eventually(assertion: () -> Boolean) = withTimeout(1_000) {
        while (!assertion()) yield()
    }

    private fun matchesFixture(): String = checkNotNull(
        AcceptanceConcurrencyTest::class.java.getResource("/fixtures/matches/upcoming.html"),
    ).readText()

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

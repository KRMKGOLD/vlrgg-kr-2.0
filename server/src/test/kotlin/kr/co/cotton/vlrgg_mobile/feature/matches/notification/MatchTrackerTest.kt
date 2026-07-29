package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchTrackerTest {
    @Test fun `baseline failures transitions duplicates and target isolation create only documented intents`() = kotlinx.coroutines.runBlocking {
        val path = Files.createTempDirectory("vlrgg-tracker").resolve("store").absolutePathString()
        store(path).use { store ->
            store.reconcileSubscription("target-a", 42, true, 1)
            store.reconcileSubscription("target-b", 42, true, 1)
            val observations = ArrayDeque<MatchObservation>(listOf(
                MatchObservation.Success(ObservationStatus.UPCOMING),
                MatchObservation.NetworkFailure,
                MatchObservation.Success(ObservationStatus.LIVE),
                MatchObservation.Success(ObservationStatus.LIVE),
                MatchObservation.ParsingFailure,
                MatchObservation.Success(ObservationStatus.COMPLETED),
            ))
            val tracker = MatchTracker(store, object : MatchObservationProvider {
                override suspend fun observe(matchId: Long) = observations.removeFirst()
            }, Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC))

            repeat(6) { tracker.runCycle() }

            assertEquals(2, store.deliveryIntentCount(42, NotificationEventType.START))
            assertEquals(2, store.deliveryIntentCount(42, NotificationEventType.END))
        }
    }

    @Test fun `upcoming directly to completed creates end only and one upstream lookup per unique match`() = kotlinx.coroutines.runBlocking {
        val path = Files.createTempDirectory("vlrgg-tracker").resolve("store").absolutePathString()
        store(path).use { store ->
            store.reconcileSubscription("target-a", 9, true, 1)
            store.reconcileSubscription("target-b", 9, true, 1)
            var calls = 0
            val states = ArrayDeque(listOf(MatchObservation.Success(ObservationStatus.POSTPONED), MatchObservation.Success(ObservationStatus.COMPLETED)))
            val tracker = MatchTracker(store, object : MatchObservationProvider {
                override suspend fun observe(matchId: Long): MatchObservation { calls++; return states.removeFirst() }
            })
            tracker.runCycle(); tracker.runCycle()
            assertEquals(2, calls)
            assertEquals(0, store.deliveryIntentCount(9, NotificationEventType.START))
            assertEquals(2, store.deliveryIntentCount(9, NotificationEventType.END))
        }
    }

    @Test fun `terminal observations stop future polling while missing stays distinct and first terminal is baseline`() = runBlocking {
        val path = Files.createTempDirectory("vlrgg-tracker").resolve("store").absolutePathString()
        store(path).use { store ->
            store.reconcileSubscription("completed", 1, true, 1)
            store.recordObservation(1, ObservationResult.SUCCESS, ObservationStatus.UPCOMING)
            store.recordObservation(1, ObservationResult.MISSING)
            assertEquals(listOf(1L), store.activeMatchIds())
            store.recordObservation(1, ObservationResult.SUCCESS, ObservationStatus.COMPLETED)
            assertEquals(1, store.deliveryIntentCount(1, NotificationEventType.END))
            assertEquals(emptyList(), store.activeMatchIds())

            store.reconcileSubscription("cancelled", 2, true, 1)
            store.recordObservation(2, ObservationResult.SUCCESS, ObservationStatus.CANCELLED)
            assertEquals(0, store.deliveryIntentCount(2, NotificationEventType.START))
            assertEquals(0, store.deliveryIntentCount(2, NotificationEventType.END))
            assertEquals(emptyList(), store.activeMatchIds())
        }
    }

    @Test fun `observation ordering is derived from durable state after reopen`() {
        val path = Files.createTempDirectory("vlrgg-tracker").resolve("store").absolutePathString()
        val instant = Instant.parse("2026-07-29T00:00:00Z")
        store(path).use { store ->
            store.reconcileSubscription("target", 42, true, 1)
            store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.UPCOMING, instant)
        }
        store(path).use { store ->
            store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.LIVE, instant)
            assertEquals(1, store.deliveryIntentCount(42, NotificationEventType.START))
        }
    }

    @Test fun `two stores cannot resurrect polling after a concurrent terminal observation`() {
        val path = Files.createTempDirectory("vlrgg-tracker").resolve("store").absolutePathString()
        val instant = Instant.parse("2026-07-29T00:00:00Z")
        store(path).use { initial ->
            initial.reconcileSubscription("target", 42, true, 1)
            initial.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.UPCOMING, instant)
        }
        store(path).use { completedStore ->
            store(path).use { liveStore ->
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
                val completed = Thread {
                    try { ready.countDown(); start.await(); completedStore.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.COMPLETED, instant) } catch (error: Throwable) { failures += error }
                }
                val live = Thread {
                    try { ready.countDown(); start.await(); liveStore.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.LIVE, instant) } catch (error: Throwable) { failures += error }
                }
                completed.start(); live.start()
                kotlin.test.assertTrue(ready.await(1, TimeUnit.SECONDS))
                start.countDown(); completed.join(5_000); live.join(5_000)
                assertEquals(emptyList(), failures)
                assertEquals(emptyList(), completedStore.activeMatchIds())
                assertEquals(1, completedStore.deliveryIntentCount(42, NotificationEventType.END))
            }
        }
    }

    @Test fun `cancellation propagates and owner closes only after tracker job completes`() = runBlocking {
        val events = mutableListOf<String>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val job = scope.launch {
            try { awaitCancellation() } finally { events += "cancelled" }
        }
        val owner = OwnedTrackingJob(job) { events += "closed" }
        owner.stopAndJoin()
        assertEquals(listOf("cancelled", "closed"), events)
        scope.cancel()
    }

    @Test fun `tracker does not reinterpret cancellation as a network observation`() = runBlocking {
        val path = Files.createTempDirectory("vlrgg-tracker").resolve("store").absolutePathString()
        store(path).use { store ->
            store.reconcileSubscription("target", 7, true, 1)
            val tracker = MatchTracker(store, object : MatchObservationProvider {
                override suspend fun observe(matchId: Long): MatchObservation = throw kotlinx.coroutines.CancellationException("test")
            })
            assertFailsWith<kotlinx.coroutines.CancellationException> { tracker.runCycle() }
            Unit
        }
    }

    private fun store(path: String) = NotificationStore.open(NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ), ServerListenerConfiguration("127.0.0.1", 8080)))
}

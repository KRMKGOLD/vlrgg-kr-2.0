package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun store(path: String) = NotificationStore.open(NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ), ServerListenerConfiguration("127.0.0.1", 8080)))
}

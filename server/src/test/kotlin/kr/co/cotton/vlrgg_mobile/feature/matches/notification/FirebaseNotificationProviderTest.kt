package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.api.core.ApiFutures
import com.google.api.core.SettableApiFuture
import com.google.firebase.messaging.Message
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FirebaseNotificationProviderTest {
    private val target = SendableDeliveryTarget(PushTarget(java.util.UUID.randomUUID()), "offline-target", FirebaseTargetMode.FID)

    @Test fun `actual async adapter maps completed sendAsync future to acceptance offline`() = runBlocking {
        val provider = FirebaseNotificationProvider(sendAsync = { _: Message -> ApiFutures.immediateFuture("message-id") })
        assertEquals(ProviderDeliveryResult.Accepted, provider.send(target, NotificationEventType.START))
    }

    @Test fun `actual async adapter timeout does not cancel pending Firebase future and late completion is harmless`() = runBlocking {
        val future = SettableApiFuture.create<String>()
        val entered = CompletableDeferred<Unit>()
        val provider = FirebaseNotificationProvider(sendAsync = { _: Message -> entered.complete(Unit); future })
        val timeout = try {
            withTimeout(50) { provider.send(target, NotificationEventType.START) }
            null
        } catch (error: TimeoutCancellationException) {
            error
        }
        assertNotNull(timeout)
        entered.await()
        assertFalse(future.isCancelled)
        future.set("late-message-id")
        assertFalse(future.isCancelled)
    }

    @Test fun `actual async adapter cancellation leaves pending Firebase future for late completion`() = runBlocking {
        val future = SettableApiFuture.create<String>()
        val entered = CompletableDeferred<Unit>()
        val provider = FirebaseNotificationProvider(sendAsync = { _: Message -> entered.complete(Unit); future })
        val call = async { provider.send(target, NotificationEventType.END) }
        entered.await()
        call.cancelAndJoin()
        assertFalse(future.isCancelled)
        future.set("late-message-id")
        assertFalse(future.isCancelled)
    }

    @Test fun `actual Firebase failure adapter accepts one list value and preserves application floor for 429 and 503`() = runBlocking {
        listOf(429, 503).forEach { status ->
            val configuration = config()
            NotificationStore.open(configuration).use { store ->
                intent(store)
                val provider = failingProvider(status, mapOf("rEtRy-AfTeR" to listOf("1")))
                val mapped = provider.send(target, NotificationEventType.START)
                assertEquals(ProviderDeliveryResult.Retryable(status, mapOf("Retry-After" to "1")), mapped)
                NotificationDeliveryService(store, provider, configuration, Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
                assertEquals(30_000L, store.deliveryDetails(42, NotificationEventType.START)?.retryDelayMillis)
            }
        }
    }

    @Test fun `actual Firebase failure adapter rejects duplicate and malformed retry header shapes`() = runBlocking {
        val shapes = listOf(
            linkedMapOf("Retry-After" to listOf("1"), "retry-after" to listOf("2")),
            mapOf("Retry-After" to emptyList<String>()),
            mapOf("Retry-After" to listOf(1)),
            mapOf("Retry-After" to setOf("1")),
        )
        shapes.forEach { headers ->
            assertEquals(
                ProviderDeliveryResult.Retryable(503),
                failingProvider(503, headers).send(target, NotificationEventType.START),
            )
        }
    }

    private fun failingProvider(status: Int, headers: Map<String, Any?>): FirebaseNotificationProvider = FirebaseNotificationProvider(
        sendAsync = { ApiFutures.immediateFailedFuture(FakeFirebaseTransportFailure()) },
        firebaseFailure = { error ->
            if (error is FakeFirebaseTransportFailure) FirebaseProviderFailure(null, status, headers) else null
        },
    )

    private fun intent(store: NotificationStore) {
        requireNotNull(store.reconcileSubscription("adapter-target", 42, true, 1).target)
        store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.UPCOMING, NOW)
        store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.LIVE, NOW.plusSeconds(1))
    }

    private fun config() = NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true",
        "VLRGG_NOTIFICATIONS_STORAGE_PATH" to Files.createTempDirectory("firebase-adapter").resolve("store").absolutePathString(),
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "VLRGG_NOTIFICATIONS_RETRY_JITTER_MILLIS" to "0",
    ), ServerListenerConfiguration("127.0.0.1", 8080))

    private class FakeFirebaseTransportFailure : RuntimeException()

    private companion object { val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z") }
}

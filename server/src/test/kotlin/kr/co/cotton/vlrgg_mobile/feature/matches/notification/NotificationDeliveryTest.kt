package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class NotificationDeliveryTest {
    @Test fun `marker precedes fake provider success and accepts exactly one application attempt`() = runBlocking {
        store().use { store ->
            intent(store)
            var calls = 0
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult { calls++; return ProviderDeliveryResult.Accepted }
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(1, calls)
            assertEquals(DeliveryState.ACCEPTED to 1, store.deliveryState(42, NotificationEventType.START))
        }
    }

    @Test fun `invalid target erases sendability without retrying`() = runBlocking {
        store().use { store ->
            val target = intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.InvalidTarget
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryState.INVALID_TARGET to 1, store.deliveryState(42, NotificationEventType.START))
            assertEquals(false, store.targetProjection(target)?.sendable)
        }
    }

    @Test fun `429 retry after is persisted as retry wait`() = runBlocking {
        store().use { store ->
            intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(429, mapOf("rEtRy-AfTeR" to "60"))
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryState.RETRY_WAIT to 1, store.deliveryState(42, NotificationEventType.START))
        }
    }

    private fun intent(store: NotificationStore): PushTarget {
        val target = requireNotNull(store.reconcileSubscription("target", 42, true, 1).target)
        store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.UPCOMING, NOW)
        store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.LIVE, NOW.plusSeconds(1))
        return target
    }

    private fun store(): NotificationStore = NotificationStore.open(configPath(Files.createTempDirectory("delivery").resolve("store").absolutePathString()))
    private fun config() = configPath(Files.createTempDirectory("delivery-config").resolve("store").absolutePathString())
    private fun configPath(path: String) = NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1", "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ), ServerListenerConfiguration("127.0.0.1", 8080))
    private companion object { val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z") }
}

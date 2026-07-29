package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

    @Test fun `UNREGISTERED equivalent tombstones and redacts the target without retrying`() = runBlocking {
        store().use { store ->
            val target = intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.InvalidTarget
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryState.INVALID_TARGET to 1, store.deliveryState(42, NotificationEventType.START))
            assertEquals(false, store.targetProjection(target)?.sendable)
            assertEquals(TargetResolution.TARGET_REFRESH_REQUIRED, store.findOrRegister("target", "register", 2).resolution)
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

    @Test fun `503 case insensitive retry after preserves the provider minimum`() = runBlocking {
        store().use { store ->
            intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(503, mapOf("RETRY-after" to "120"))
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryState.RETRY_WAIT to 1, store.deliveryState(42, NotificationEventType.START))
            assertEquals(120_000L, store.deliveryDetails(42, NotificationEventType.START)?.retryDelayMillis)
        }
    }

    @Test fun `generic invalid argument equivalent is terminal without target erasure`() = runBlocking {
        store().use { store ->
            val target = intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.NonRetryable("FIREBASE_INVALID_ARGUMENT")
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryState.TERMINAL_FAILURE to 1, store.deliveryState(42, NotificationEventType.START))
            assertEquals(true, store.targetProjection(target)?.sendable)
        }
    }

    @Test fun `timed out asynchronous provider becomes unknown and late completion cannot change it`() = runBlocking {
        store().use { store ->
            intent(store)
            val completed = CompletableDeferred<Unit>()
            val provider = object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult = suspendCancellableCoroutine { continuation ->
                    completed.invokeOnCompletion { continuation.resume(ProviderDeliveryResult.Accepted) }
                }
            }
            NotificationDeliveryService(store, provider, config(overrides = mapOf("VLRGG_NOTIFICATIONS_DELIVERY_TIMEOUT_MILLIS" to "1000")), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryState.UNKNOWN to 1, store.deliveryState(42, NotificationEventType.START))
            completed.complete(Unit)
            delay(20)
            assertEquals(DeliveryState.UNKNOWN to 1, store.deliveryState(42, NotificationEventType.START))
        }
    }

    @Test fun `cancelling an in-flight provider call quarantines the marked intent`() = runBlocking {
        store().use { store ->
            intent(store)
            val entered = CompletableDeferred<Unit>()
            val provider = object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult {
                    entered.complete(Unit)
                    return CompletableDeferred<ProviderDeliveryResult>().await()
                }
            }
            val job = launch { NotificationDeliveryService(store, provider, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce() }
            entered.await()
            job.cancelAndJoin()
            assertEquals(DeliveryState.UNKNOWN to 1, store.deliveryState(42, NotificationEventType.START))
        }
    }

    @Test fun `expired call marker recovers to unknown and remains non-resendable after reopen`() = runBlocking {
        val path = freshPath()
        val configuration = config(path)
        NotificationStore.open(configuration).use { store ->
            intent(store)
            val claim = requireNotNull(store.claimDueDelivery(NOW))
            requireNotNull(store.markDeliveryCallStarted(claim, NOW))
            assertNull(store.claimDueDelivery(NOW.plusMillis(configuration.claimLeaseMillis + 1)))
            assertEquals(DeliveryState.UNKNOWN to 1, store.deliveryState(42, NotificationEventType.START))
        }
        NotificationStore.open(configuration).use { reopened ->
            var calls = 0
            assertFalse(NotificationDeliveryService(reopened, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult { calls++; return ProviderDeliveryResult.Accepted }
            }, configuration, Clock.fixed(NOW.plusMillis(configuration.claimLeaseMillis + 1), ZoneOffset.UTC)).runOnce())
            assertEquals(0, calls)
            assertEquals(DeliveryState.UNKNOWN to 1, reopened.deliveryState(42, NotificationEventType.START))
        }
    }

    @Test fun `two stores concurrently claim one pending intent only once`() = runBlocking {
        val path = freshPath()
        val configuration = config(path)
        NotificationStore.open(configuration).use { first ->
            intent(first)
            NotificationStore.open(configuration).use { second ->
                val claimed = listOf(
                    async(Dispatchers.Default) { first.claimDueDelivery(NOW) },
                    async(Dispatchers.Default) { second.claimDueDelivery(NOW) },
                ).map { it.await() }
                assertEquals(1, claimed.count { it != null })
            }
        }
    }

    @Test fun `retry exhaustion is terminal before retry header processing`() = runBlocking {
        store().use { store ->
            intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(429, mapOf("Retry-After" to "999999999999999999999999"))
            }, config(overrides = mapOf("VLRGG_NOTIFICATIONS_MAX_APPLICATION_ATTEMPTS" to "1")), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(DeliveryDetails(DeliveryState.TERMINAL_FAILURE, 1, "RETRY_EXHAUSTED", null, null, null), store.deliveryDetails(42, NotificationEventType.START))
        }
    }

    @Test fun `huge retry after delta persists schedule overflow instead of escaping`() = runBlocking {
        store().use { store ->
            intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(503, mapOf("Retry-After" to "999999999999999999999999999999999"))
            }, config(), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals("RETRY_SCHEDULE_OVERFLOW", store.deliveryDetails(42, NotificationEventType.START)?.terminalReason)
        }
    }

    @Test fun `Instant boundary retry schedule persists overflow from DateTimeException`() = runBlocking {
        val configuration = config(overrides = mapOf(
            "VLRGG_NOTIFICATIONS_DELIVERY_TIMEOUT_MILLIS" to "1000",
            "VLRGG_NOTIFICATIONS_CLAIM_LEASE_MILLIS" to "10000",
            "VLRGG_NOTIFICATIONS_RETRY_JITTER_MILLIS" to "0",
        ))
        NotificationStore.open(configuration).use { store ->
            intent(store)
            val nearMaximum = Instant.MAX.minusSeconds(15)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(503)
            }, configuration, Clock.fixed(nearMaximum, ZoneOffset.UTC)).runOnce()
            assertEquals("RETRY_SCHEDULE_OVERFLOW", store.deliveryDetails(42, NotificationEventType.START)?.terminalReason)
        }
    }

    @Test fun `only ASCII OWS trims retry after and unicode whitespace falls back safely`() = runBlocking {
        store().use { store ->
            intent(store)
            NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(503, mapOf("Retry-After" to "\u00a0120\u00a0"))
            }, config(overrides = mapOf("VLRGG_NOTIFICATIONS_RETRY_JITTER_MILLIS" to "0")), Clock.fixed(NOW, ZoneOffset.UTC)).runOnce()
            assertEquals(30_000L, store.deliveryDetails(42, NotificationEventType.START)?.retryDelayMillis)
        }
    }

    @Test fun `persisted schedule is monotonic guarded and restart delays at most once`() = runBlocking {
        store().use { store ->
            intent(store)
            val configuration = config(overrides = mapOf(
                "VLRGG_NOTIFICATIONS_INITIAL_RETRY_MILLIS" to "1000",
                "VLRGG_NOTIFICATIONS_MAX_RETRY_MILLIS" to "1000",
                "VLRGG_NOTIFICATIONS_RETRY_JITTER_MILLIS" to "0",
            ))
            var nanos = 0L
            val first = NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Retryable(503)
            }, configuration, Clock.fixed(NOW, ZoneOffset.UTC), RetryMonotonicGuard { nanos })
            first.runOnce()
            val due = requireNotNull(store.deliveryDetails(42, NotificationEventType.START)?.retryDueAt)
            var calls = 0
            val guarded = NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult { calls++; return ProviderDeliveryResult.Accepted }
            }, configuration, Clock.fixed(due, ZoneOffset.UTC), RetryMonotonicGuard { nanos })
            assertFalse(guarded.runOnce())
            nanos = 1_000_000_000L
            assertTrue(guarded.runOnce())
            assertEquals(1, calls)
        }
    }

    private fun intent(store: NotificationStore): PushTarget {
        val target = requireNotNull(store.reconcileSubscription("target", 42, true, 1).target)
        store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.UPCOMING, NOW)
        store.recordObservation(42, ObservationResult.SUCCESS, ObservationStatus.LIVE, NOW.plusSeconds(1))
        return target
    }

    private fun store(): NotificationStore = NotificationStore.open(config(freshPath()))
    private fun freshPath() = Files.createTempDirectory("delivery").resolve("store").absolutePathString()
    private fun config(path: String = freshPath(), overrides: Map<String, String> = emptyMap()) = NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1", "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ) + overrides, ServerListenerConfiguration("127.0.0.1", 8080))
    private companion object { val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z") }
}

package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.cloud.firestore.Firestore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Real SDK/emulator regression suite; default test deliberately excludes this category. */
@Category(FirestoreEmulatorCategory::class)
class FirestoreNotificationStoreEmulatorTest {
    private val epochMillis = Instant.parse("2026-07-31T00:00:00Z").toEpochMilli()
    private lateinit var firestore: Firestore
    private lateinit var store: FirestoreNotificationStore

    @Before fun setUp() {
        val host = requireNotNull(System.getenv("FIRESTORE_EMULATOR_HOST"))
        val project = "demo-vlrgg-" + UUID.randomUUID().toString().replace("-", "").take(20)
        firestore = EmulatorFirestoreClientFactory.create(mapOf("FIRESTORE_EMULATOR_HOST" to host, "VLRGG_FIRESTORE_TEST_PROJECT_ID" to project))
        store = FirestoreNotificationStore(firestore, Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC))
    }

    @After fun tearDown() {
        if (this::store.isInitialized) store.close()
    }

    @Test fun `target secret is required without revealing target existence`() {
        val target = store.register("registration-token-a")
        assertNull(store.readAuthorized(target.targetId, "wrong"))
        assertNull(store.readAuthorized(UUID.randomUUID().toString(), target.targetSecret))
        assertNotNull(store.readAuthorized(target.targetId, target.targetSecret))
    }

    @Test fun `subscription replay and conflict keep revision and capacity atomic`() {
        val target = store.register("registration-token-a")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 7, true, 1))
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 7, true, 1))
        assertFailsWith<RevisionConflictException> { store.setSubscription(target.targetId, target.targetSecret, 8, true, 1) }
        val capacity = firestore.collection("notificationControl").document("capacity").get().get()
        assertEquals(1, capacity.getLong("activeUniqueMatchCount"))
        assertEquals(3, store.disableAll(target.targetId, target.targetSecret, 2))
        assertEquals(3, store.disableAll(target.targetId, target.targetSecret, 2))
        assertEquals(0, firestore.collection("notificationControl").document("capacity").get().get().getLong("activeUniqueMatchCount"))
    }

    @Test fun `global off and revoke read every subscription before decrementing multi match capacity`() {
        val target = store.register("registration-token-a")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 31, true, 1))
        assertEquals(3, store.setSubscription(target.targetId, target.targetSecret, 32, true, 2))
        assertEquals(4, store.disableAll(target.targetId, target.targetSecret, 3))
        assertEquals(0, firestore.collection("notificationControl").document("capacity").get().get().getLong("activeUniqueMatchCount"))
        assertEquals(5, store.setSubscription(target.targetId, target.targetSecret, 33, true, 4))
        assertEquals(6, store.setSubscription(target.targetId, target.targetSecret, 34, true, 5))
        assertEquals(7, store.revoke(target.targetId, target.targetSecret, 6))
        assertEquals(0, firestore.collection("notificationControl").document("capacity").get().get().getLong("activeUniqueMatchCount"))
        assertNull(store.readAuthorized(target.targetId, target.targetSecret))
    }

    @Test fun `concurrent same target subscribe commits one logical subscription`() {
        val target = store.register("registration-token-a")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val calls = List(2) { executor.submit(Callable {
                runCatching { store.setSubscription(target.targetId, target.targetSecret, 9, true, 1) }.getOrNull()
            }) }
            assertTrue(calls.map { it.get() }.all { it == 2L })
            val subscriptions = firestore.collection("notificationTargets").document(target.targetId).collection("subscriptions").get().get().documents
            assertEquals(1, subscriptions.size)
            assertEquals(1, firestore.collection("trackedMatches").document("9").get().get().getLong("enabledTargetCount"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `poll lease excludes a competing owner across schedule slots`() {
        assertTrue(store.acquirePollLease("2026-07-31T00:00Z", "owner-a", Duration.ofSeconds(550)))
        assertEquals(epochMillis + Duration.ofSeconds(550).toMillis(), firestore.collection("notificationControl").document("pollLease").get().get().getLong("leaseUntil"))
        assertFalse(store.acquirePollLease("2026-07-31T00:10Z", "owner-b", Duration.ofSeconds(550)))
    }

    @Test fun `due active query excludes future terminal and inactive matches and enforces its limit`() {
        val target = store.register("registration-token-due")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 710, true, 1))
        assertEquals(
            epochMillis,
            firestore.collection("trackedMatches").document("710").get().get().getLong("nextCheckAt"),
        )
        assertEquals(3, store.setSubscription(target.targetId, target.targetSecret, 711, true, 2))
        assertEquals(4, store.setSubscription(target.targetId, target.targetSecret, 712, true, 3))
        assertEquals(5, store.setSubscription(target.targetId, target.targetSecret, 713, true, 4))

        store.recordObservation(710, null)
        store.recordObservation(712, ObservationStatus.COMPLETED)
        firestore.collection("trackedMatches").document("711").update("nextCheckAt", epochMillis + 1).get()
        firestore.collection("trackedMatches").document("713").update("enabledTargetCount", 0L).get()

        assertTrue(store.dueActiveMatchIds(100).isEmpty())

        firestore.collection("trackedMatches").document("711").update("nextCheckAt", epochMillis).get()
        firestore.collection("trackedMatches").document("713").update("enabledTargetCount", 1L).get()
        val due = store.dueActiveMatchIds(2)
        assertEquals(2, due.size)
        assertTrue(due.all { it in setOf(711L, 713L) })
        assertFalse(due.contains(710L))
        assertFalse(due.contains(712L))
    }

    @Test fun `baseline then live fanout batch of two creates intents after all reads`() {
        val first = store.register("registration-token-a")
        val second = store.register("registration-token-b")
        assertEquals(2, store.setSubscription(first.targetId, first.targetSecret, 17, true, 1))
        assertEquals(2, store.setSubscription(second.targetId, second.targetSecret, 17, true, 1))
        store.recordObservation(17, ObservationStatus.UPCOMING)
        store.recordObservation(17, ObservationStatus.LIVE)
        assertTrue(store.resumeStartFanout(17, 2))
        assertFalse(store.resumeStartFanout(17, 2))
        val intents = firestore.collection("deliveryIntents").get().get().documents
        assertEquals(2, intents.size)
        assertTrue(intents.all { it.getString("event") == "START" && it.id == deterministicIntentId(it.getString("targetId")!!, 17) })
    }

    @Test fun `committed call marker accepts one result and preserves unknown ambiguity`() {
        val target = store.register("registration-token-a")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 22, true, 1))
        store.recordObservation(22, ObservationStatus.UPCOMING)
        store.recordObservation(22, ObservationStatus.LIVE)
        store.resumeStartFanout(22, 100)
        val accepted = requireNotNull(store.claimDueDelivery())
        val call = requireNotNull(store.markDeliveryCallStarted(accepted))
        assertTrue(store.finalizeDelivery(call, DeliveryState.ACCEPTED))
        assertFalse(store.finalizeDelivery(call, DeliveryState.UNKNOWN, "late"))
        val snapshot = firestore.collection("deliveryIntents").document(accepted.intentId).get().get()
        assertEquals(DeliveryState.ACCEPTED.name, snapshot.getString("state"))
    }

    @Test fun `expired pre call claim is safely reclaimed and expired call started is unknown`() {
        val target = store.register("registration-token-a")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 44, true, 1))
        store.recordObservation(44, ObservationStatus.UPCOMING)
        store.recordObservation(44, ObservationStatus.LIVE)
        store.resumeStartFanout(44, 100)
        val first = requireNotNull(store.claimDueDelivery())
        firestore.collection("deliveryIntents").document(first.intentId).update("leaseUntil", Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()).get()
        val reclaimed = requireNotNull(store.claimDueDelivery())
        assertEquals(first.intentId, reclaimed.intentId)
        val call = requireNotNull(store.markDeliveryCallStarted(reclaimed))
        firestore.collection("deliveryIntents").document(call.claim.intentId).update("leaseUntil", Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()).get()
        assertNull(store.claimDueDelivery())
        val intent = firestore.collection("deliveryIntents").document(call.claim.intentId).get().get()
        assertEquals(DeliveryState.UNKNOWN.name, intent.getString("state"))
    }

    @Test fun `expired call recovery processes only its explicit transaction limit`() {
        val intents = firestore.collection("deliveryIntents")
        val expired = mapOf(
            "state" to DeliveryState.CALL_STARTED.name,
            "leaseUntil" to Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
            "updatedAt" to "2025-01-01T00:00:00Z",
        )
        val firstBatch = firestore.batch()
        repeat(FirestoreNotificationStoreLimits.EXPIRED_CALL_STARTED_RECOVERY_LIMIT) { firstBatch.set(intents.document("expired-$it"), expired) }
        firstBatch.commit().get()
        intents.document("expired-over-limit").set(expired).get()

        assertNull(store.claimDueDelivery())
        val recovered = intents.whereEqualTo("state", DeliveryState.UNKNOWN.name).get().get().documents
        assertEquals(FirestoreNotificationStoreLimits.EXPIRED_CALL_STARTED_RECOVERY_LIMIT, recovered.size)
        assertEquals(1, intents.whereEqualTo("state", DeliveryState.CALL_STARTED.name).get().get().size())
    }

    @Test fun `expired recovery backlogs reserve a claim write below Firestore transaction limit`() {
        val intents = firestore.collection("deliveryIntents")
        val expiredLease = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        val targetId = UUID.randomUUID().toString()
        firestore.collection("notificationTargets").document(targetId)
            .set(mapOf("sendable" to true, "registrationToken" to "recovery-claim-token")).get()
        val batch = firestore.batch()
        repeat(FirestoreNotificationStoreLimits.EXPIRED_CALL_STARTED_RECOVERY_LIMIT + 1) { index ->
            batch.set(intents.document("started-$index"), mapOf("state" to DeliveryState.CALL_STARTED.name, "leaseUntil" to expiredLease))
        }
        repeat(FirestoreNotificationStoreLimits.EXPIRED_PRE_CALL_RECOVERY_LIMIT + 1) { index ->
            batch.set(intents.document("pre-call-$index"), mapOf("state" to DeliveryState.CLAIMED_NOT_STARTED.name, "leaseUntil" to expiredLease))
        }
        batch.set(intents.document("claimable"), mapOf("state" to DeliveryState.PENDING.name, "targetId" to targetId, "matchId" to 991L, "attempt" to 0L))
        batch.commit().get()

        val claim = requireNotNull(store.claimDueDelivery())
        assertEquals("claimable", claim.intentId)
        assertEquals(
            FirestoreNotificationStoreLimits.MAX_CLAIM_TRANSACTION_WRITES,
            401,
        )
        assertTrue(FirestoreNotificationStoreLimits.MAX_CLAIM_TRANSACTION_WRITES < 500)
        assertEquals(FirestoreNotificationStoreLimits.EXPIRED_CALL_STARTED_RECOVERY_LIMIT, intents.whereEqualTo("state", DeliveryState.UNKNOWN.name).get().get().size())
        assertEquals(1, intents.whereEqualTo("state", DeliveryState.CALL_STARTED.name).get().get().size())
        assertEquals(FirestoreNotificationStoreLimits.EXPIRED_PRE_CALL_RECOVERY_LIMIT, intents.whereEqualTo("state", DeliveryState.PENDING.name).get().get().size())
        assertEquals(2, intents.whereEqualTo("state", DeliveryState.CLAIMED_NOT_STARTED.name).get().get().size())
    }

    @Test fun `provider invalid target atomically makes target unsendable and releases capacity`() = runBlocking {
        val target = store.register("registration-token-a")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 55, true, 1))
        store.recordObservation(55, ObservationStatus.UPCOMING)
        store.recordObservation(55, ObservationStatus.LIVE)
        assertTrue(store.resumeStartFanout(55, 100))

        val delivery = NotificationDeliveryService(store, object : NotificationProvider {
            override suspend fun send(command: NotificationCommand) = ProviderResult.InvalidTarget
        })
        assertTrue(delivery.runOnce())

        assertNull(store.readAuthorized(target.targetId, target.targetSecret))
        assertEquals(0, firestore.collection("notificationControl").document("capacity").get().get().getLong("activeUniqueMatchCount"))
        assertEquals(false, firestore.collection("notificationTargets").document(target.targetId).collection("subscriptions").document("55").get().get().getBoolean("enabled"))
        assertEquals(DeliveryState.INVALID_TARGET.name, firestore.collection("deliveryIntents").get().get().documents.single().getString("state"))
    }

    @Test fun `offline delivery e2e accepts once and retry stays pending until due`() = runBlocking {
        val target = store.register("registration-token-a")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 66, true, 1))
        store.recordObservation(66, ObservationStatus.UPCOMING)
        store.recordObservation(66, ObservationStatus.LIVE)
        assertTrue(store.resumeStartFanout(66, 100))
        var sends = 0
        val accepted = NotificationDeliveryService(store, object : NotificationProvider {
            override suspend fun send(command: NotificationCommand): ProviderResult { sends++; return ProviderResult.Accepted() }
        })
        assertTrue(accepted.runOnce())
        assertFalse(accepted.runOnce())
        assertEquals(1, sends)

        val retryTarget = store.register("registration-token-b")
        assertEquals(2, store.setSubscription(retryTarget.targetId, retryTarget.targetSecret, 67, true, 1))
        store.recordObservation(67, ObservationStatus.UPCOMING)
        store.recordObservation(67, ObservationStatus.LIVE)
        assertTrue(store.resumeStartFanout(67, 100))
        val retry = NotificationDeliveryService(store, object : NotificationProvider {
            override suspend fun send(command: NotificationCommand) = ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE)
        }, now = { Instant.parse("2026-07-31T00:00:00Z") })
        assertTrue(retry.runOnce())
        assertFalse(retry.runOnce())
        val waiting = firestore.collection("deliveryIntents").whereEqualTo("matchId", 67L).get().get().documents.single()
        assertEquals(DeliveryState.RETRY_WAIT.name, waiting.getString("state"))
        assertEquals(1L, waiting.getLong("attempt"))
        assertNotNull(waiting.getLong("dueAt"))
        Unit
    }

    @Test fun `per target limit and global off remain bounded at one hundred subscriptions`() {
        val target = store.register("registration-token-a")
        (1L..100L).forEach { matchId ->
            assertEquals(matchId + 1, store.setSubscription(target.targetId, target.targetSecret, matchId, true, matchId))
        }
        assertFailsWith<SubscriptionLimitExceededException> {
            store.setSubscription(target.targetId, target.targetSecret, 101, true, 101)
        }
        assertEquals(102, store.disableAll(target.targetId, target.targetSecret, 101))
        assertEquals(0, firestore.collection("notificationControl").document("capacity").get().get().getLong("activeUniqueMatchCount"))
        assertTrue(firestore.collection("notificationTargets").document(target.targetId).collection("subscriptions").get().get().documents.all { it.getBoolean("enabled") == false })
    }

    @Test fun `global capacity accepts existing tracked match and races two new matches at the final slot`() {
        val seed = store.register("registration-token-seed")
        (1L..99L).forEach { matchId ->
            assertEquals(matchId + 1, store.setSubscription(seed.targetId, seed.targetSecret, matchId, true, matchId))
        }
        val existing = store.register("registration-token-existing")
        assertEquals(2, store.setSubscription(existing.targetId, existing.targetSecret, 1, true, 1))
        val first = store.register("registration-token-first")
        val second = store.register("registration-token-second")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val outcomes = listOf(
                executor.submit(Callable { runCatching { store.setSubscription(first.targetId, first.targetSecret, 100, true, 1) }.exceptionOrNull() }),
                executor.submit(Callable { runCatching { store.setSubscription(second.targetId, second.targetSecret, 101, true, 1) }.exceptionOrNull() }),
            ).map { it.get() }
            assertEquals(1, outcomes.count { it == null })
            assertEquals(1, outcomes.count { it is ActiveMatchCapacityExceededException })
            assertEquals(100, firestore.collection("notificationControl").document("capacity").get().get().getLong("activeUniqueMatchCount"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `fanout cursor resumes exactly for representative boundary sizes`() {
        listOf(0, 1, 99, 100, 101, 250).forEachIndexed { index, count ->
            val matchId = 1_000L + index
            val batch = firestore.batch()
            repeat(count) { number ->
                val targetId = UUID.nameUUIDFromBytes("$index-$number".encodeToByteArray()).toString()
                batch.set(firestore.collection("notificationTargets").document(targetId), mapOf("sendable" to true, "registrationToken" to "token-$targetId"))
                batch.set(
                    firestore.collection("notificationTargets").document(targetId).collection("subscriptions").document(matchId.toString()),
                    mapOf("targetId" to targetId, "matchId" to matchId, "enabled" to true, "enabledAt" to epochMillis),
                )
            }
            batch.commit().get()
            firestore.collection("trackedMatches").document(matchId.toString()).set(mapOf("enabledTargetCount" to count.toLong(), "terminal" to false)).get()
            store.recordObservation(matchId, ObservationStatus.UPCOMING)
            store.recordObservation(matchId, ObservationStatus.LIVE)
            while (store.resumeStartFanout(matchId, 100)) {
                // The persistent cursor makes each iteration advance one bounded page.
            }
            assertEquals(count, firestore.collection("deliveryIntents").whereEqualTo("matchId", matchId).get().get().size())
            assertFalse(store.resumeStartFanout(matchId, 100))
        }
    }

    @Test fun `fanout excludes post start subscriptions and unsendable targets`() {
        val beforeStart = store.register("registration-token-before")
        val afterStart = store.register("registration-token-after")
        val unsendable = store.register("registration-token-unsendable")
        assertEquals(2, store.setSubscription(beforeStart.targetId, beforeStart.targetSecret, 777, true, 1))
        store.recordObservation(777, ObservationStatus.UPCOMING)
        store.recordObservation(777, ObservationStatus.LIVE)
        assertEquals(epochMillis, firestore.collection("trackedMatches").document("777").get().get().getLong("startLatchedAt"))
        assertEquals(2, store.setSubscription(afterStart.targetId, afterStart.targetSecret, 777, true, 1))
        assertEquals(2, store.setSubscription(unsendable.targetId, unsendable.targetSecret, 777, true, 1))
        firestore.collection("notificationTargets").document(afterStart.targetId).collection("subscriptions").document("777")
            .update("enabledAt", epochMillis + 1).get()
        firestore.collection("notificationTargets").document(unsendable.targetId)
            .update("sendable", false, "registrationToken", null).get()

        while (store.resumeStartFanout(777, 100)) { }
        val intents = firestore.collection("deliveryIntents").whereEqualTo("matchId", 777L).get().get().documents
        assertEquals(listOf(beforeStart.targetId), intents.map { it.getString("targetId") })
    }

    @Test fun `provider timeout becomes unknown and cannot be automatically resent`() = runBlocking {
        val target = store.register("registration-token-timeout")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 888, true, 1))
        store.recordObservation(888, ObservationStatus.UPCOMING)
        store.recordObservation(888, ObservationStatus.LIVE)
        assertTrue(store.resumeStartFanout(888, 100))
        var sends = 0
        val delivery = NotificationDeliveryService(
            store,
            object : NotificationProvider {
                override suspend fun send(command: NotificationCommand): ProviderResult { sends++; delay(50); return ProviderResult.Accepted() }
            },
            providerTimeoutMillis = 1,
        )
        assertTrue(delivery.runOnce())
        assertFalse(delivery.runOnce())
        assertEquals(1, sends)
        val intent = firestore.collection("deliveryIntents").whereEqualTo("matchId", 888L).get().get().documents.single()
        assertEquals(DeliveryState.UNKNOWN.name, intent.getString("state"))
        assertEquals("PROVIDER_TIMEOUT", intent.getString("terminalReason"))
    }

    @Test fun `request bound scheduler isolates observations, orders delivery, and lease no-ops competitors`() = runBlocking {
        val target = store.register("registration-token-scheduler")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 901, true, 1))
        assertEquals(3, store.setSubscription(target.targetId, target.targetSecret, 902, true, 2))
        store.recordObservation(901, ObservationStatus.UPCOMING)
        store.recordObservation(902, ObservationStatus.UPCOMING)
        firestore.collection("trackedMatches").document("901").update("nextCheckAt", epochMillis).get()
        firestore.collection("trackedMatches").document("902").update("nextCheckAt", epochMillis).get()
        val observed = mutableListOf<Long>()
        var sends = 0
        val scheduler = NotificationSchedulerUseCase(
            store = store,
            observations = object : MatchObservationProvider {
                override suspend fun observe(matchId: Long): ObservationStatus? {
                    observed += matchId
                    if (matchId == 901L) throw IllegalStateException("upstream unavailable")
                    return ObservationStatus.LIVE
                }
            },
            delivery = NotificationDeliveryService(store, object : NotificationProvider {
                override suspend fun send(command: NotificationCommand): ProviderResult { sends++; return ProviderResult.Accepted() }
            }),
            clock = Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC),
        )
        val first = scheduler.run("2026-07-31T00:00Z", "owner-a")
        assertTrue(first.leaseAcquired)
        assertEquals(1, first.matchesScanned)
        assertTrue(observed.containsAll(listOf(901L, 902L)))
        assertEquals(1, sends)
        assertEquals(902L, firestore.collection("deliveryIntents").get().get().documents.single().getLong("matchId"))
        assertFalse(scheduler.run("2026-07-31T00:10Z", "owner-b").leaseAcquired)
    }

    @Test fun `scheduler deadline returns without a background observation loop`() = runBlocking {
        val target = store.register("registration-token-deadline")
        assertEquals(2, store.setSubscription(target.targetId, target.targetSecret, 903, true, 1))
        var observations = 0
        val scheduler = NotificationSchedulerUseCase(
            store,
            object : MatchObservationProvider { override suspend fun observe(matchId: Long): ObservationStatus? { observations++; return ObservationStatus.LIVE } },
            policy = NotificationSchedulerPolicy(deadlineSeconds = 0),
            clock = Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC),
        )
        val result = scheduler.run("2026-07-31T00:00Z", "owner-a")
        assertTrue(result.deadlineReached)
        assertEquals(0, observations)
    }
}

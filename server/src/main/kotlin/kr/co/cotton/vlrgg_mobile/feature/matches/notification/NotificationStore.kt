package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.TransactionOptions
import com.google.api.core.ApiFuture
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The only production-facing App Check boundary. Real verification belongs to Stage 2. */
@JvmInline value class AppCheckEvidence(val rawToken: String)
data class VerifiedApp(val firebaseAppId: String)
interface AppCheckVerifier { suspend fun verify(evidence: AppCheckEvidence): VerifiedApp? }

data class NotificationCommand(
    val targetId: String, val matchId: Long, val registrationToken: String, val intentId: String,
    val event: NotificationEventType = NotificationEventType.START,
    val title: String = "Match starting", val body: String = "Your selected match has started.",
)
enum class NotificationEventType { START }
enum class ProviderRetryKind { RATE_LIMITED, UNAVAILABLE }
sealed interface ProviderResult {
    data class Accepted(val providerMessageId: String? = null) : ProviderResult
    data object InvalidTarget : ProviderResult
    data class Retryable(val kind: ProviderRetryKind, val hintMillis: Long? = null) : ProviderResult
    data class NonRetryable(val safeCode: String) : ProviderResult
    data object Unknown : ProviderResult
}
interface NotificationProvider { suspend fun send(command: NotificationCommand): ProviderResult }

enum class DeliveryState { PENDING, RETRY_WAIT, CLAIMED_NOT_STARTED, CALL_STARTED, ACCEPTED, INVALID_TARGET, TERMINAL_FAILURE, UNKNOWN }
enum class ObservationStatus { UPCOMING, LIVE, COMPLETED, POSTPONED, CANCELLED }
data class TargetRecord(val targetId: String, val revision: Long, val sendable: Boolean, val subscriptions: List<SubscriptionRecord>)
data class SubscriptionRecord(val matchId: Long, val enabled: Boolean)
data class RegisteredTarget(val targetId: String, val targetSecret: String, val revision: Long)
data class DeliveryClaim(val intentId: String, val targetId: String, val matchId: Long, val registrationToken: String, val claimToken: String, val attempt: Int)
internal data class DeliveryCall(val claim: DeliveryClaim, val command: NotificationCommand)

class ActiveMatchCapacityExceededException : RuntimeException()
class SubscriptionLimitExceededException : RuntimeException()
class RevisionConflictException : RuntimeException()
class RevisionExhaustedException : RuntimeException()

/**
 * A claim transaction can recover both kinds of expired work and claim one delivery.  Keep the
 * combined write maximum deliberately below Firestore's 500-write transaction limit.
 */
internal object FirestoreNotificationStoreLimits {
    const val FIRESTORE_RPC_TIMEOUT_MILLIS = 30_000L
    const val TRANSACTION_MAX_ATTEMPTS = 5
    const val MAX_ACTIVE_UNIQUE_MATCHES = 100
    const val MAX_ENABLED_SUBSCRIPTIONS_PER_TARGET = 100
    const val EXPIRED_CALL_STARTED_RECOVERY_LIMIT = 200
    const val EXPIRED_PRE_CALL_RECOVERY_LIMIT = 200
    const val CLAIM_WRITE_RESERVE = 1
    const val MAX_CLAIM_TRANSACTION_WRITES = EXPIRED_CALL_STARTED_RECOVERY_LIMIT + EXPIRED_PRE_CALL_RECOVERY_LIMIT + CLAIM_WRITE_RESERVE
}

/** Explicit emulator-only client construction: it never discovers ADC or a production project. */
object EmulatorFirestoreClientFactory {
    fun create(environment: Map<String, String> = System.getenv()): Firestore {
        val host = environment["FIRESTORE_EMULATOR_HOST"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("FIRESTORE_EMULATOR_HOST is required")
        val projectId = environment["VLRGG_FIRESTORE_TEST_PROJECT_ID"]?.takeIf { it.matches(Regex("[a-z][a-z0-9-]{2,62}")) }
            ?: throw IllegalStateException("VLRGG_FIRESTORE_TEST_PROJECT_ID is required")
        return FirestoreOptions.newBuilder().setProjectId(projectId).setEmulatorHost(host)
            .build().service
    }
}

/**
 * Firestore-backed state. Mutations that affect subscriptions use one transaction for target
 * revision, target-local count, tracked count, and the global unique-Match capacity document.
 */
class FirestoreNotificationStore(private val firestore: Firestore, private val clock: Clock = Clock.systemUTC()) : AutoCloseable {
    private data class SubscriptionCapacity(
        val subscription: com.google.cloud.firestore.DocumentReference,
        val previousEnabledAt: Any?,
        val match: com.google.cloud.firestore.DocumentReference,
        val matchRow: com.google.cloud.firestore.DocumentSnapshot,
        val count: Long,
        val nextCount: Long,
        val usedCapacity: Long?,
    )
    /** An attempted observation consumes one scheduler interval, even when the upstream has no status. */
    private val observationInterval: Duration = Duration.ofMinutes(10)
    private val targets get() = firestore.collection("notificationTargets")
    private val tracked get() = firestore.collection("trackedMatches")
    private val intents get() = firestore.collection("deliveryIntents")
    private val control get() = firestore.collection("notificationControl")
    private fun <T> transaction(block: com.google.cloud.firestore.Transaction.Function<T>): ApiFuture<T> =
        firestore.runTransaction(
            block,
            TransactionOptions.createReadWriteOptionsBuilder()
                .setNumberOfAttempts(FirestoreNotificationStoreLimits.TRANSACTION_MAX_ATTEMPTS)
                .build(),
        )

    /** Firestore's Java SDK waits synchronously; application callers must use these IO-bound entry points. */
    suspend fun registerOnIo(registrationToken: String): RegisteredTarget = firestoreIo { register(registrationToken) }
    suspend fun readAuthorizedOnIo(targetId: String, secret: String): TargetRecord? = firestoreIo { readAuthorized(targetId, secret) }
    suspend fun refreshRegistrationTokenOnIo(targetId: String, secret: String, token: String, expectedRevision: Long): Long = firestoreIo { refreshRegistrationToken(targetId, secret, token, expectedRevision) }
    suspend fun setSubscriptionOnIo(targetId: String, secret: String, matchId: Long, enabled: Boolean, expectedRevision: Long): Long = firestoreIo { setSubscription(targetId, secret, matchId, enabled, expectedRevision) }
    suspend fun disableAllOnIo(targetId: String, secret: String, expectedRevision: Long): Long = firestoreIo { disableAll(targetId, secret, expectedRevision) }
    suspend fun revokeOnIo(targetId: String, secret: String, expectedRevision: Long): Long = firestoreIo { revoke(targetId, secret, expectedRevision) }
    suspend fun recordObservationOnIo(matchId: Long, status: ObservationStatus?) = firestoreIo { recordObservation(matchId, status) }
    suspend fun dueActiveMatchIdsOnIo(limit: Int): List<Long> = firestoreIo { dueActiveMatchIds(limit) }
    suspend fun acquirePollLeaseOnIo(scheduleSlot: String, ownerId: String, lease: Duration): Boolean = firestoreIo { acquirePollLease(scheduleSlot, ownerId, lease) }
    suspend fun resumeStartFanoutOnIo(matchId: Long, batchSize: Int): Boolean = firestoreIo { resumeStartFanout(matchId, batchSize) }
    suspend fun pendingFanoutMatchIdsOnIo(limit: Int = 100): List<Long> = firestoreIo { pendingFanoutMatchIds(limit) }
    suspend fun claimDueDeliveryOnIo(lease: Duration = Duration.ofMinutes(2)): DeliveryClaim? = firestoreIo { claimDueDelivery(lease) }
    internal suspend fun markDeliveryCallStartedOnIo(claim: DeliveryClaim, lease: Duration = Duration.ofMinutes(2)): DeliveryCall? = firestoreIo { markDeliveryCallStarted(claim, lease) }
    internal suspend fun finalizeDeliveryOnIo(call: DeliveryCall, state: DeliveryState, reason: String? = null, dueAt: Instant? = null): Boolean = firestoreIo { finalizeDelivery(call, state, reason, dueAt) }

    internal fun register(registrationToken: String): RegisteredTarget {
        requireToken(registrationToken)
        val id = UUID.randomUUID().toString()
        val secret = TargetSecrets.generate()
        transaction { tx ->
            tx.create(targets.document(id), mapOf("registrationToken" to registrationToken, "secretHash" to TargetSecrets.hash(secret), "revision" to 1L, "operationHash" to operationHash("register"), "operationExpectedRevision" to 0L, "sendable" to true, "createdAt" to now(), "updatedAt" to now()))
            RegisteredTarget(id, secret, 1)
        }.awaitTransaction()
        return RegisteredTarget(id, secret, 1)
    }

    internal fun readAuthorized(targetId: String, secret: String): TargetRecord? {
        requireCanonicalTargetId(targetId)
        val snapshot = targets.document(targetId).get().awaitOperation()
        if (!snapshot.exists() || snapshot.getBoolean("sendable") != true || !TargetSecrets.matches(secret, snapshot.getString("secretHash"))) return null
        val subs = targets.document(targetId).collection("subscriptions").get().awaitOperation().documents.mapNotNull { doc ->
            doc.getLong("matchId")?.let { SubscriptionRecord(it, doc.getBoolean("enabled") == true) }
        }.sortedBy { it.matchId }
        return TargetRecord(targetId, snapshot.getLong("revision") ?: 0, true, subs)
    }

    internal fun refreshRegistrationToken(targetId: String, secret: String, token: String, expectedRevision: Long): Long {
        requireToken(token)
        return mutate(targetId, secret, expectedRevision, "refresh:$token") { tx, target, _ ->
        tx.update(target, mapOf("registrationToken" to token))
        }
    }

    internal fun setSubscription(targetId: String, secret: String, matchId: Long, enabled: Boolean, expectedRevision: Long): Long {
        require(matchId > 0) { "match ID must be canonical positive decimal" }
        return mutate(targetId, secret, expectedRevision, "subscription:$matchId:$enabled") { tx, target, row ->
            val sub = target.collection("subscriptions").document(matchId.toString())
            val old = tx.get(sub).get()
            val wasEnabled = old.exists() && old.getBoolean("enabled") == true
            if (wasEnabled == enabled) return@mutate Unit
            val capacity = readSubscriptionCapacity(tx, target, sub, matchId, enabled, old)
            if (enabled && capacity.count == 0L && capacity.usedCapacity!! >= FirestoreNotificationStoreLimits.MAX_ACTIVE_UNIQUE_MATCHES) throw ActiveMatchCapacityExceededException()
            // Read phase ends above.  Firestore transactions reject every read after the first write.
            updateCapacity(tx, enabled, capacity)
            val fields = mutableMapOf<String, Any>(
                "enabledTargetCount" to capacity.nextCount,
                "terminal" to (capacity.matchRow.getBoolean("terminal") ?: false),
                "updatedAt" to now(),
            )
            if (enabled && capacity.count == 0L) fields["nextCheckAt"] = nowMillis()
            tx.set(capacity.match, fields, SetOptions.merge())
            tx.set(capacity.subscription, mapOf("targetId" to targetId, "matchId" to matchId, "enabled" to enabled, "enabledAt" to if (enabled) nowMillis() else capacity.previousEnabledAt, "updatedAt" to now()))
        }
    }

    internal fun disableAll(targetId: String, secret: String, expectedRevision: Long): Long = mutate(targetId, secret, expectedRevision, "global:off") { tx, target, _ ->
        disableEnabledSubscriptions(tx, target)
    }

    internal fun revoke(targetId: String, secret: String, expectedRevision: Long): Long = mutate(targetId, secret, expectedRevision, "revoke") { tx, target, _ ->
        disableEnabledSubscriptions(tx, target)
        tx.update(target, "sendable", false, "registrationToken", null, "revokedAt", now())
    }

    /**
     * Records the result of one observation attempt and schedules the next attempt ten minutes
     * from the store clock. Terminal matches stay terminal and are never reintroduced to polling.
     */
    internal fun recordObservation(matchId: Long, status: ObservationStatus?) {
        require(matchId > 0)
        transaction { tx ->
            val match = tracked.document(matchId.toString()); val row = tx.get(match).get()
            val previous = row.getString("lastObservation"); val terminal = row.getBoolean("terminal") == true
            if (terminal) return@transaction
            val current = nowMillis()
            val nextCheckAt = Math.addExact(current, observationInterval.toMillis())
            if (status == ObservationStatus.COMPLETED || status == ObservationStatus.CANCELLED) {
                tx.set(match, mapOf("terminal" to true, "lastObservation" to status.name, "nextCheckAt" to null, "updatedAt" to now()), SetOptions.merge())
            } else if (status == ObservationStatus.LIVE && previous in setOf(ObservationStatus.UPCOMING.name, ObservationStatus.POSTPONED.name) && row.get("startLatchedAt") == null) {
                tx.set(match, mapOf("startLatchedAt" to current, "fanoutCursor" to null, "lastObservation" to status.name, "nextCheckAt" to nextCheckAt, "updatedAt" to now()), SetOptions.merge())
                tx.create(firestore.collection("startFanoutJobs").document(matchId.toString()), mapOf("matchId" to matchId, "cursor" to null, "completed" to false, "createdAt" to now(), "updatedAt" to now()))
            } else {
                val fields = mutableMapOf<String, Any>("nextCheckAt" to nextCheckAt, "updatedAt" to now())
                if (status != null) fields["lastObservation"] = status.name
                tx.set(match, fields, SetOptions.merge())
            }
        }.awaitTransaction()
    }

    /** Firestore filters inactive, terminal, and not-yet-due matches before applying the bound. */
    internal fun dueActiveMatchIds(limit: Int): List<Long> {
        require(limit in 1..100)
        return tracked
            .whereEqualTo("terminal", false)
            .whereGreaterThan("enabledTargetCount", 0L)
            .whereLessThanOrEqualTo("nextCheckAt", nowMillis())
            .orderBy("nextCheckAt")
            .orderBy("enabledTargetCount")
            .limit(limit)
            .get().awaitOperation().documents.mapNotNull { it.id.toLongOrNull() }
    }

    /** Scheduler ownership is a durable lease; no process-local singleton is involved. */
    internal fun acquirePollLease(scheduleSlot: String, ownerId: String, lease: Duration): Boolean {
        require(scheduleSlot.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}Z$")))
        require(ownerId.isNotBlank() && lease.isPositive)
        return transaction { tx ->
            val ref = control.document("pollLease"); val row = tx.get(ref).get()
            val current = nowMillis()
            val until = row.getLong("leaseUntil")
            if (until != null && until > current && row.getString("ownerId") != ownerId) return@transaction false
            tx.set(ref, mapOf("ownerId" to ownerId, "scheduleSlot" to scheduleSlot, "leaseUntil" to Math.addExact(current, lease.toMillis()), "updatedAt" to now()))
            true
        }.awaitTransaction()
    }

    /** Creates at most one deterministic START intent per target/match and checkpoints the cursor atomically. */
    internal fun resumeStartFanout(matchId: Long, batchSize: Int): Boolean {
        require(matchId > 0 && batchSize in 1..100)
        val job = firestore.collection("startFanoutJobs").document(matchId.toString())
        return transaction { tx ->
            val row = tx.get(job).get()
            if (!row.exists() || row.getBoolean("completed") == true) return@transaction false
            val startLatchedAt = row.getLong("startLatchedAt")
                ?: tx.get(tracked.document(matchId.toString())).get().getLong("startLatchedAt")
                ?: return@transaction false
            val cursor = row.getString("cursor")
            var query = firestore.collectionGroup("subscriptions").whereEqualTo("matchId", matchId).whereEqualTo("enabled", true).orderBy("targetId").limit(batchSize)
            if (cursor != null) query = query.startAfter(cursor)
            val subscriptions = tx.get(query).get().documents
            data class FanoutCandidate(val targetId: String, val create: Boolean)
            val candidates = subscriptions.mapNotNull { subscription ->
                val targetId = subscription.getString("targetId") ?: return@mapNotNull null
                val target = tx.get(targets.document(targetId)).get()
                val enabledAt = subscription.getLong("enabledAt")
                if (enabledAt != null && enabledAt <= startLatchedAt && target.getBoolean("sendable") == true && target.getString("registrationToken") != null) {
                    val intentId = deterministicIntentId(targetId, matchId)
                    val intent = intents.document(intentId)
                    val existing = tx.get(intent).get()
                    if (existing.exists()) {
                        check(existing.getString("targetId") == targetId && existing.getLong("matchId") == matchId && existing.getString("event") == "START") { "delivery intent integrity failure" }
                    }
                    FanoutCandidate(targetId, !existing.exists())
                } else null
            }
            // All target and intent reads are complete before any create/checkpoint write.
            candidates.filter { it.create }.forEach { candidate ->
                tx.create(intents.document(deterministicIntentId(candidate.targetId, matchId)), mapOf("targetId" to candidate.targetId, "matchId" to matchId, "event" to "START", "state" to DeliveryState.PENDING.name, "attempt" to 0L, "createdAt" to now(), "updatedAt" to now()))
            }
            val last = subscriptions.lastOrNull()?.getString("targetId")
            tx.set(job, mapOf("cursor" to last, "completed" to (subscriptions.size < batchSize), "updatedAt" to now()), SetOptions.merge())
            subscriptions.isNotEmpty()
        }.awaitTransaction()
    }

    internal fun pendingFanoutMatchIds(limit: Int = 100): List<Long> = firestore.collection("startFanoutJobs").whereEqualTo("completed", false).limit(limit).get().awaitOperation().documents.mapNotNull { it.getLong("matchId") }

    /** Claims one due delivery and writes at most [FirestoreNotificationStoreLimits.MAX_CLAIM_TRANSACTION_WRITES] documents. */
    internal fun claimDueDelivery(lease: Duration = Duration.ofMinutes(2)): DeliveryClaim? = transaction { tx ->
        val current = nowMillis()
        val expiredStarted = tx.get(intents.whereEqualTo("state", DeliveryState.CALL_STARTED.name).whereLessThan("leaseUntil", current).limit(FirestoreNotificationStoreLimits.EXPIRED_CALL_STARTED_RECOVERY_LIMIT)).get().documents
        val expiredPreCall = tx.get(intents.whereEqualTo("state", DeliveryState.CLAIMED_NOT_STARTED.name).whereLessThan("leaseUntil", current).limit(FirestoreNotificationStoreLimits.EXPIRED_PRE_CALL_RECOVERY_LIMIT)).get().documents
        val pending = tx.get(intents.whereEqualTo("state", DeliveryState.PENDING.name).limit(1)).get().documents.firstOrNull()
            ?: tx.get(intents.whereEqualTo("state", DeliveryState.RETRY_WAIT.name).whereLessThanOrEqualTo("dueAt", current).limit(1)).get().documents.firstOrNull()
            ?: expiredPreCall.firstOrNull()
        val targetId = pending?.getString("targetId")
        val matchId = pending?.getLong("matchId")?.takeIf { it > 0 }
        val target = targetId?.let { tx.get(targets.document(it)).get() }
        // Read phase ends above; recovery and claim writes follow.
        expiredStarted.forEach { tx.update(it.reference, mapOf("state" to DeliveryState.UNKNOWN.name, "terminalReason" to "CALL_STARTED_LEASE_EXPIRED", "updatedAt" to now())) }
        expiredPreCall.forEach { tx.update(it.reference, mapOf("state" to DeliveryState.PENDING.name, "claimToken" to null, "leaseUntil" to null, "updatedAt" to now())) }
        if (pending == null) return@transaction null
        if (matchId == null) {
            tx.update(pending.reference, mapOf("state" to DeliveryState.UNKNOWN.name, "terminalReason" to "INTENT_MISSING_MATCH_ID", "claimToken" to null, "leaseUntil" to null, "updatedAt" to now()))
            return@transaction null
        }
        if (targetId == null || target == null) return@transaction null
        if (target.getBoolean("sendable") != true || target.getString("registrationToken") == null) {
            tx.update(pending.reference, mapOf("state" to DeliveryState.INVALID_TARGET.name, "updatedAt" to now())); return@transaction null
        }
        val token = UUID.randomUUID().toString(); val nextAttempt = (pending.getLong("attempt") ?: 0L).toInt() + 1
        tx.update(pending.reference, mapOf("state" to DeliveryState.CLAIMED_NOT_STARTED.name, "claimToken" to token, "leaseUntil" to Math.addExact(current, lease.toMillis()), "updatedAt" to now()))
        DeliveryClaim(pending.id, targetId, matchId, target.getString("registrationToken")!!, token, nextAttempt)
    }.awaitTransaction()

    /** The marker commits before a provider call, so cancellation/timeout can never cause an automatic resend. */
    internal fun markDeliveryCallStarted(claim: DeliveryClaim, lease: Duration = Duration.ofMinutes(2)): DeliveryCall? = transaction { tx ->
        val intent = tx.get(intents.document(claim.intentId)).get()
        if (intent.getString("state") != DeliveryState.CLAIMED_NOT_STARTED.name || intent.getString("claimToken") != claim.claimToken) return@transaction null
        tx.update(intent.reference, mapOf("state" to DeliveryState.CALL_STARTED.name, "callStartedAt" to now(), "leaseUntil" to Math.addExact(nowMillis(), lease.toMillis()), "attempt" to claim.attempt.toLong(), "updatedAt" to now()))
        DeliveryCall(claim, NotificationCommand(claim.targetId, claim.matchId, claim.registrationToken, claim.intentId))
    }.awaitTransaction()

    internal fun finalizeDelivery(call: DeliveryCall, state: DeliveryState, reason: String? = null, dueAt: Instant? = null): Boolean {
        require(state in setOf(DeliveryState.ACCEPTED, DeliveryState.INVALID_TARGET, DeliveryState.RETRY_WAIT, DeliveryState.TERMINAL_FAILURE, DeliveryState.UNKNOWN))
        return transaction { tx ->
            val intent = tx.get(intents.document(call.claim.intentId)).get()
            if (intent.getString("state") != DeliveryState.CALL_STARTED.name || intent.getString("claimToken") != call.claim.claimToken) return@transaction false
            val target = if (state == DeliveryState.INVALID_TARGET) tx.get(targets.document(call.claim.targetId)).get() else null
            // All reads, including the bounded subscription set used for invalid-target cleanup,
            // occur before this transaction writes its terminal intent state.
            if (target != null && target.exists() && target.getBoolean("sendable") == true) {
                disableEnabledSubscriptions(tx, targets.document(call.claim.targetId))
                tx.update(targets.document(call.claim.targetId), "sendable", false, "registrationToken", null, "invalidatedAt", now())
            }
            tx.update(intent.reference, mapOf("state" to state.name, "terminalReason" to reason, "dueAt" to dueAt?.toEpochMilli(), "claimToken" to null, "leaseUntil" to null, "updatedAt" to now()))
            true
        }.awaitTransaction()
    }

    private fun mutate(targetId: String, secret: String, expected: Long, operation: String, action: (com.google.cloud.firestore.Transaction, com.google.cloud.firestore.DocumentReference, com.google.cloud.firestore.DocumentSnapshot) -> Unit): Long {
        requireCanonicalTargetId(targetId); require(expected > 0)
        return transaction { tx ->
            val ref = targets.document(targetId); val row = tx.get(ref).get()
            if (!row.exists() || row.getBoolean("sendable") != true || !TargetSecrets.matches(secret, row.getString("secretHash"))) throw SecurityException("target auth")
            val current = row.getLong("revision") ?: 0
            val hash = operationHash(operation)
            if (row.getLong("operationExpectedRevision") == expected && row.getString("operationHash") == hash) return@transaction current
            if (current != expected) throw RevisionConflictException()
            if (current == Long.MAX_VALUE) throw RevisionExhaustedException()
            action(tx, ref, row)
            val next = current + 1; tx.update(ref, "revision", next, "operationHash", hash, "operationExpectedRevision", expected, "updatedAt", now()); next
        }.awaitTransaction()
    }
    private fun readSubscriptionCapacity(
        tx: com.google.cloud.firestore.Transaction,
        target: com.google.cloud.firestore.DocumentReference,
        subscription: com.google.cloud.firestore.DocumentReference,
        matchId: Long,
        enabled: Boolean,
        previous: com.google.cloud.firestore.DocumentSnapshot,
    ): SubscriptionCapacity {
        val active = tx.get(target.collection("subscriptions").whereEqualTo("enabled", true)).get().size()
        if (enabled && active >= FirestoreNotificationStoreLimits.MAX_ENABLED_SUBSCRIPTIONS_PER_TARGET) throw SubscriptionLimitExceededException()
        val match = tracked.document(matchId.toString())
        val matchRow = tx.get(match).get()
        val count = matchRow.getLong("enabledTargetCount") ?: 0L
        val nextCount = (count + if (enabled) 1 else -1).coerceAtLeast(0L)
        val needsCapacity = (enabled && count == 0L) || (!enabled && count > 0L && nextCount == 0L)
        val usedCapacity = if (needsCapacity) tx.get(control.document("capacity")).get().getLong("activeUniqueMatchCount") ?: 0L else null
        return SubscriptionCapacity(subscription, previous.get("enabledAt"), match, matchRow, count, nextCount, usedCapacity)
    }
    private fun updateCapacity(tx: com.google.cloud.firestore.Transaction, enabled: Boolean, change: SubscriptionCapacity) {
        if (enabled && change.count == 0L) {
            tx.set(control.document("capacity"), mapOf("activeUniqueMatchCount" to change.usedCapacity!! + 1, "updatedAt" to now()), SetOptions.merge())
        } else if (!enabled && change.count > 0L && change.nextCount == 0L) {
            tx.set(control.document("capacity"), mapOf("activeUniqueMatchCount" to (change.usedCapacity!! - 1).coerceAtLeast(0), "updatedAt" to now()), SetOptions.merge())
        }
    }
    private fun disableEnabledSubscriptions(tx: com.google.cloud.firestore.Transaction, target: com.google.cloud.firestore.DocumentReference) {
        data class EnabledSubscription(
            val document: com.google.cloud.firestore.QueryDocumentSnapshot,
            val match: com.google.cloud.firestore.DocumentReference,
            val nextCount: Long,
        )
        val enabled = tx.get(target.collection("subscriptions").whereEqualTo("enabled", true)).get().documents.mapNotNull { doc ->
            val matchId = doc.getLong("matchId") ?: return@mapNotNull null
            val match = tracked.document(matchId.toString())
            val nextCount = ((tx.get(match).get().getLong("enabledTargetCount") ?: 1L) - 1).coerceAtLeast(0)
            EnabledSubscription(doc, match, nextCount)
        }
        val capacity = control.document("capacity")
        val zeroed = enabled.count { it.nextCount == 0L }
        val used = if (zeroed > 0) tx.get(capacity).get().getLong("activeUniqueMatchCount") ?: 0L else null
        // Read phase is complete: write every bounded subscription/count update afterwards.
        enabled.forEach { subscription ->
            tx.update(subscription.document.reference, mapOf("enabled" to false, "updatedAt" to now()))
            tx.set(subscription.match, mapOf("enabledTargetCount" to subscription.nextCount, "updatedAt" to now()), SetOptions.merge())
        }
        if (zeroed > 0) tx.set(capacity, mapOf("activeUniqueMatchCount" to (used!! - zeroed).coerceAtLeast(0), "updatedAt" to now()), SetOptions.merge())
    }
    private fun now() = Instant.now(clock).toString()
    private fun nowMillis() = Instant.now(clock).toEpochMilli()
    override fun close() = firestore.close()
}

private suspend fun <T> firestoreIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

object TargetSecrets {
    private val random = SecureRandom()
    fun generate(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    fun hash(secret: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val saltText = Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
        return "v1:$saltText:" + digest(salt, secret)
    }
    fun matches(secret: String, stored: String?): Boolean {
        val parts = stored?.split(':') ?: return false
        if (parts.size != 3 || parts[0] != "v1") return false
        val salt = runCatching { Base64.getUrlDecoder().decode(parts[1]) }.getOrNull()?.takeIf { it.size == 16 } ?: return false
        return MessageDigest.isEqual(digest(salt, secret).toByteArray(UTF_8), parts[2].toByteArray(UTF_8))
    }
    private fun digest(salt: ByteArray, secret: String) = sha256("vlrgg-target-secret-v1".toByteArray(UTF_8) + salt + secret.toByteArray(UTF_8))
}

fun deterministicIntentId(targetId: String, matchId: Long): String {
    requireCanonicalTargetId(targetId); require(matchId > 0)
    fun lp(v: String) = ByteBuffer.allocate(4 + v.toByteArray(UTF_8).size).putInt(v.toByteArray(UTF_8).size).put(v.toByteArray(UTF_8)).array()
    return sha256(lp("vlrgg-match-start-intent-v1") + lp(targetId) + lp(matchId.toString()) + lp("START"))
}
private fun operationHash(value: String) = sha256(value.toByteArray(UTF_8))
private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
internal fun <T> ApiFuture<T>.awaitTransaction(): T = awaitFirestore("transaction")
private fun <T> ApiFuture<T>.awaitOperation(): T = awaitFirestore("operation")
private fun <T> ApiFuture<T>.awaitFirestore(label: String): T = try {
    get(FirestoreNotificationStoreLimits.FIRESTORE_RPC_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
} catch (error: ExecutionException) {
    throw normalizeFirestoreTransactionFailure(error)
} catch (error: InterruptedException) {
    Thread.currentThread().interrupt()
    throw IllegalStateException("Firestore $label interrupted", error)
} catch (error: TimeoutException) {
    cancel(true)
    throw IllegalStateException("Firestore $label timed out", error)
}
internal fun normalizeFirestoreTransactionFailure(error: Throwable): RuntimeException {
    var cause = error
    while (cause is ExecutionException || cause is CompletionException) cause = cause.cause ?: break
    return cause as? RuntimeException ?: IllegalStateException("Firestore transaction failed", cause)
}
fun requireCanonicalTargetId(value: String) { require(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(value) && runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) }
private fun requireToken(value: String) { require(value.isNotBlank() && value.encodeToByteArray().size <= 4096 && value.none { it.isISOControl() }) }

package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLTransactionRollbackException
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@JvmInline value class PushTarget(val id: UUID)

enum class TargetResolution { CREATED, EXISTING, TARGET_REFRESH_REQUIRED }
data class TargetLookupResult(val target: PushTarget?, val resolution: TargetResolution, val revision: RevisionResult?)
enum class RevisionResult { APPLIED, STALE, REPLAYED, CONFLICT, REVISION_EXHAUSTED }
enum class ObservationStatus { UPCOMING, LIVE, COMPLETED, POSTPONED, CANCELLED }
enum class ObservationResult { SUCCESS, NETWORK_FAILURE, PARSING_FAILURE, MISSING }
enum class NotificationEventType { START, END }
enum class DeliveryState { PENDING, RETRY_WAIT, CLAIMED_NOT_STARTED, CALL_STARTED, ACCEPTED, INVALID_TARGET, TERMINAL_FAILURE, UNKNOWN }
data class DeliveryClaim(val intentId: UUID, val target: PushTarget, val event: NotificationEventType, val token: UUID, val attemptCount: Int, val retryDelayMillis: Long?, val retryDecisionAt: Instant?, val retryDueAt: Instant?)
internal data class DeliveryCall(val claim: DeliveryClaim, val target: SendableDeliveryTarget)
internal data class DeliveryDetails(
    val state: DeliveryState,
    val attemptCount: Int,
    val terminalReason: String?,
    val retryDelayMillis: Long?,
    val retryDecisionAt: Instant?,
    val retryDueAt: Instant?,
)
data class SubscriptionProjection(val matchId: Long, val active: Boolean)
data class NotificationStateProjection(val acceptedRevision: Long, val subscriptions: List<SubscriptionProjection>)
internal typealias TargetDigest = (ByteArray, FirebaseTargetMode, String) -> ByteArray

/** Public persistence projection deliberately excludes a raw registration value. */
data class TargetProjection(
    val id: PushTarget,
    val sendable: Boolean,
    val targetMode: FirebaseTargetMode,
    val acceptedRevision: Long,
)

/** Internal-only boundary for the future delivery worker. */
internal data class SendableDeliveryTarget(val target: PushTarget, val registrationValue: String, val mode: FirebaseTargetMode)

class NotificationStore private constructor(
    private val dataSource: HikariDataSource,
    private val configuration: NotificationConfiguration,
    private val targetDigest: TargetDigest,
) : AutoCloseable {
    private val mode get() = configuration.targetMode
    private val lookupKey get() = requireNotNull(configuration.lookupDigestKey)
    private val keyId get() = sha256Hex(lookupKey)

    fun findOrRegister(registrationValue: String, operation: String, revision: Long, now: Instant = Instant.now()): TargetLookupResult {
        requireValidRevision(revision)
        requireValidRegistrationValue(registrationValue)
        val digest = targetDigest(lookupKey, mode, registrationValue)
        require(digest.size == 32) { "target digest must be 32 bytes" }
        return retryingTransaction { connection ->
            connection.prepareStatement("SELECT id, registration_value, sendable, accepted_revision, operation_hash FROM notification_targets WHERE provider=? AND target_mode=? AND lookup_digest=?").use { statement ->
                statement.setString(1, PROVIDER); statement.setString(2, mode.name); statement.setBytes(3, digest)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        val id = UUID.randomUUID()
                        connection.prepareStatement("INSERT INTO notification_targets (id, provider, target_mode, lookup_digest, lookup_key_id, registration_value, sendable, invalidated_at, accepted_revision, operation_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, TRUE, NULL, ?, ?, ?, ?)").use { insert ->
                            insert.setObject(1, id); insert.setString(2, PROVIDER); insert.setString(3, mode.name); insert.setBytes(4, digest); insert.setString(5, keyId); insert.setString(6, registrationValue); insert.setLong(7, revision); insert.setBytes(8, operationHash(operation)); insert.setObject(9, now); insert.setObject(10, now); insert.executeUpdate()
                        }
                        TargetLookupResult(PushTarget(id), TargetResolution.CREATED, RevisionResult.APPLIED)
                    } else {
                        val target = PushTarget(rows.getObject(1, UUID::class.java))
                        val raw = rows.getString(2)
                        if (!rows.getBoolean(3) || raw == null) return@retryingTransaction TargetLookupResult(null, TargetResolution.TARGET_REFRESH_REQUIRED, null)
                        if (!constantTimeEquals(raw.toByteArray(StandardCharsets.UTF_8), registrationValue.toByteArray(StandardCharsets.UTF_8))) throw TargetDigestCollisionException()
                        val result = applyRevision(connection, target, rows.getLong(4), rows.getBytes(5), revision, operation, now)
                        TargetLookupResult(target, TargetResolution.EXISTING, result)
                    }
                }
            }
        }
    }

    fun mutateSubscription(target: PushTarget, matchId: Long, active: Boolean, revision: Long, operation: String, now: Instant = Instant.now()): RevisionResult = retryingTransaction { connection ->
        requireValidRevision(revision)
        requireValidMatchId(matchId)
        val row = targetRow(connection, target) ?: return@retryingTransaction RevisionResult.CONFLICT
        if (!row.sendable) return@retryingTransaction RevisionResult.CONFLICT
        val revisionOutcome = revisionOutcome(row.revision, row.operationHash, revision, operation)
        if (revisionOutcome != RevisionResult.APPLIED) return@retryingTransaction revisionOutcome
        val priorActive = subscriptionActive(connection, target, matchId)
        if (active && priorActive != true && activeSubscriptionCount(connection, target) >= configuration.activeSubscriptionsMax) throw SubscriptionLimitExceededException()
        val result = applyRevision(connection, target, row.revision, row.operationHash, revision, operation, now)
        if (result != RevisionResult.APPLIED) return@retryingTransaction result
        if (priorActive == null) insertSubscription(connection, target, matchId, active, now) else updateSubscription(connection, target, matchId, active, now)
        result
    }

    /** Applies address resolution, target revision and the desired match state in one transaction. */
    fun reconcileSubscription(registrationValue: String, matchId: Long, active: Boolean, revision: Long, now: Instant = Instant.now()): TargetLookupResult {
        requireValidRegistrationValue(registrationValue)
        requireValidMatchId(matchId)
        requireValidRevision(revision)
        val operation = "subscription:$matchId:${if (active) "on" else "off"}"
        val digest = targetDigest(lookupKey, mode, registrationValue).also { require(it.size == 32) { "target digest must be 32 bytes" } }
        return retryingTransaction { connection ->
            val existing = targetByDigest(connection, digest)
            if (existing == null) {
                val target = insertTarget(connection, digest, registrationValue, revision, operation, now)
                insertSubscription(connection, target, matchId, active, now)
                TargetLookupResult(target, TargetResolution.CREATED, RevisionResult.APPLIED)
            } else {
                val (target, raw, row) = existing
                if (!row.sendable || raw == null) return@retryingTransaction TargetLookupResult(null, TargetResolution.TARGET_REFRESH_REQUIRED, null)
                if (!constantTimeEquals(raw.toByteArray(StandardCharsets.UTF_8), registrationValue.toByteArray(StandardCharsets.UTF_8))) throw TargetDigestCollisionException()
                val outcome = revisionOutcome(row.revision, row.operationHash, revision, operation)
                if (outcome != RevisionResult.APPLIED) return@retryingTransaction TargetLookupResult(target, TargetResolution.EXISTING, outcome)
                val priorActive = subscriptionActive(connection, target, matchId)
                if (active && priorActive != true && activeSubscriptionCount(connection, target) >= configuration.activeSubscriptionsMax) throw SubscriptionLimitExceededException()
                applyRevision(connection, target, row.revision, row.operationHash, revision, operation, now)
                if (priorActive == null) insertSubscription(connection, target, matchId, active, now) else updateSubscription(connection, target, matchId, active, now)
                TargetLookupResult(target, TargetResolution.EXISTING, RevisionResult.APPLIED)
            }
        }
    }

    fun reconcileGlobalOff(registrationValue: String, revision: Long, now: Instant = Instant.now()): TargetLookupResult {
        requireValidRegistrationValue(registrationValue)
        requireValidRevision(revision)
        val operation = "global:off"
        val digest = targetDigest(lookupKey, mode, registrationValue).also { require(it.size == 32) { "target digest must be 32 bytes" } }
        return retryingTransaction { connection ->
            val existing = targetByDigest(connection, digest)
                ?: return@retryingTransaction TargetLookupResult(null, TargetResolution.TARGET_REFRESH_REQUIRED, null)
            val (target, raw, row) = existing
            if (!row.sendable || raw == null) return@retryingTransaction TargetLookupResult(null, TargetResolution.TARGET_REFRESH_REQUIRED, null)
            if (!constantTimeEquals(raw.toByteArray(StandardCharsets.UTF_8), registrationValue.toByteArray(StandardCharsets.UTF_8))) throw TargetDigestCollisionException()
            val outcome = revisionOutcome(row.revision, row.operationHash, revision, operation)
            if (outcome != RevisionResult.APPLIED) return@retryingTransaction TargetLookupResult(target, TargetResolution.EXISTING, outcome)
            applyRevision(connection, target, row.revision, row.operationHash, revision, operation, now)
            connection.prepareStatement("UPDATE notification_subscriptions SET active=FALSE, updated_at=? WHERE target_id=? AND active=TRUE").use { statement ->
                statement.setObject(1, now); statement.setObject(2, target.id); statement.executeUpdate()
            }
            TargetLookupResult(target, TargetResolution.EXISTING, RevisionResult.APPLIED)
        }
    }

    fun stateForRegistration(registrationValue: String): NotificationStateProjection? {
        requireValidRegistrationValue(registrationValue)
        val digest = targetDigest(lookupKey, mode, registrationValue).also { require(it.size == 32) { "target digest must be 32 bytes" } }
        return transaction { connection ->
            val existing = targetByDigest(connection, digest) ?: return@transaction null
            val (target, raw, row) = existing
            if (!row.sendable || raw == null || !constantTimeEquals(raw.toByteArray(StandardCharsets.UTF_8), registrationValue.toByteArray(StandardCharsets.UTF_8))) return@transaction null
            NotificationStateProjection(row.revision, subscriptions(connection, target))
        }
    }

    fun activeMatchIds(): List<Long> = transaction { connection ->
        connection.prepareStatement("""
            SELECT DISTINCT s.match_id
            FROM notification_subscriptions s
            LEFT JOIN notification_match_tracking tracking ON tracking.match_id=s.match_id
            WHERE s.active=TRUE AND COALESCE(tracking.terminal, FALSE)=FALSE
            ORDER BY s.match_id
        """.trimIndent()).use { statement ->
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
        }
    }

    /** Recovers only pre-call claims.  A committed call marker is deliberately ambiguous and quarantined. */
    internal fun claimDueDelivery(now: Instant): DeliveryClaim? = retryingTransaction { connection ->
        connection.prepareStatement("UPDATE notification_delivery_intents SET state='UNKNOWN', terminal_reason='CALL_STARTED_LEASE_EXPIRED', updated_at=? WHERE state='CALL_STARTED' AND lease_until<?").use {
            it.setObject(1, now); it.setObject(2, now); it.executeUpdate()
        }
        connection.prepareStatement("UPDATE notification_delivery_intents SET state='PENDING', claim_token=NULL, claimed_at=NULL, lease_until=NULL, updated_at=? WHERE state='CLAIMED_NOT_STARTED' AND lease_until<?").use {
            it.setObject(1, now); it.setObject(2, now); it.executeUpdate()
        }
        connection.prepareStatement("SELECT id, target_id, event_type, application_attempt_count, retry_delay_millis, retry_decision_at, due_at FROM notification_delivery_intents WHERE state='PENDING' OR (state='RETRY_WAIT' AND due_at<=?) ORDER BY COALESCE(due_at, created_at), id LIMIT 1 FOR UPDATE").use { select ->
            select.setObject(1, now); select.executeQuery().use { rows ->
                if (!rows.next()) return@retryingTransaction null
                val id = rows.getObject(1, UUID::class.java)
                val target = PushTarget(rows.getObject(2, UUID::class.java))
                val event = NotificationEventType.valueOf(rows.getString(3))
                val token = UUID.randomUUID()
                connection.prepareStatement("UPDATE notification_delivery_intents SET state='CLAIMED_NOT_STARTED', claim_token=?, claimed_at=?, lease_until=?, updated_at=? WHERE id=? AND state IN ('PENDING','RETRY_WAIT')").use { update ->
                    update.setObject(1, token); update.setObject(2, now); update.setObject(3, now.plusMillis(configuration.claimLeaseMillis)); update.setObject(4, now); update.setObject(5, id)
                    check(update.executeUpdate() == 1)
                }
                DeliveryClaim(id, target, event, token, rows.getInt(4), rows.getLong(5).takeUnless { rows.wasNull() }, rows.getObject(6, Instant::class.java), rows.getObject(7, Instant::class.java))
            }
        }
    }

    internal fun releaseDeliveryClaim(claim: DeliveryClaim, now: Instant): Boolean = transaction { connection ->
        connection.prepareStatement("UPDATE notification_delivery_intents SET state=CASE WHEN retry_delay_millis IS NULL THEN 'PENDING' ELSE 'RETRY_WAIT' END, claim_token=NULL, claimed_at=NULL, lease_until=NULL, updated_at=? WHERE id=? AND state='CLAIMED_NOT_STARTED' AND claim_token=?").use {
            it.setObject(1, now); it.setObject(2, claim.intentId); it.setObject(3, claim.token); it.executeUpdate() == 1
        }
    }

    /** This committed marker is the sole boundary before an adapter invocation. */
    internal fun markDeliveryCallStarted(claim: DeliveryClaim, now: Instant): DeliveryCall? = transaction { connection ->
        val target = readSendableTargetForDelivery(connection, claim.target) ?: run {
            connection.prepareStatement("UPDATE notification_delivery_intents SET state='INVALID_TARGET', terminal_reason='TARGET_UNSENDABLE', updated_at=? WHERE id=? AND state='CLAIMED_NOT_STARTED' AND claim_token=?").use {
                it.setObject(1, now); it.setObject(2, claim.intentId); it.setObject(3, claim.token); it.executeUpdate()
            }
            return@transaction null
        }
        connection.prepareStatement("UPDATE notification_delivery_intents SET state='CALL_STARTED', application_attempt_count=application_attempt_count+1, call_started_at=?, updated_at=? WHERE id=? AND state='CLAIMED_NOT_STARTED' AND claim_token=?").use { update ->
            update.setObject(1, now); update.setObject(2, now); update.setObject(3, claim.intentId); update.setObject(4, claim.token)
            if (update.executeUpdate() != 1) return@transaction null
        }
        DeliveryCall(claim.copy(attemptCount = claim.attemptCount + 1), target)
    }

    internal fun finalizeAccepted(call: DeliveryCall, now: Instant): Boolean = finalizeCall(call.claim, now, "ACCEPTED", null, null)
    internal fun finalizeUnknown(call: DeliveryCall, reason: String, now: Instant): Boolean = finalizeCall(call.claim, now, "UNKNOWN", reason, null)
    internal fun finalizeTerminal(call: DeliveryCall, reason: String, now: Instant): Boolean = finalizeCall(call.claim, now, "TERMINAL_FAILURE", reason, null)
    internal fun finalizeRetry(call: DeliveryCall, decisionAt: Instant, delayMillis: Long, dueAt: Instant): Boolean = finalizeCall(call.claim, decisionAt, "RETRY_WAIT", null, dueAt, delayMillis)

    internal fun invalidateDeliveryTarget(call: DeliveryCall, now: Instant): Boolean = transaction { connection ->
        val claimed = connection.prepareStatement("UPDATE notification_delivery_intents SET state='INVALID_TARGET', terminal_reason='PROVIDER_INVALID_TARGET', updated_at=? WHERE id=? AND state='CALL_STARTED' AND claim_token=?").use {
            it.setObject(1, now); it.setObject(2, call.claim.intentId); it.setObject(3, call.claim.token); it.executeUpdate() == 1
        }
        if (claimed) invalidateTargetInTransaction(connection, call.claim.target, now)
        claimed
    }

    internal fun deliveryIntentCount(matchId: Long, event: NotificationEventType): Int = transaction { connection ->
        connection.prepareStatement("SELECT COUNT(*) FROM notification_delivery_intents WHERE match_id=? AND event_type=?").use { statement ->
            statement.setLong(1, matchId); statement.setString(2, event.name); statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }
    }

    internal fun deliveryState(matchId: Long, event: NotificationEventType): Pair<DeliveryState, Int>? = transaction { connection ->
        connection.prepareStatement("SELECT state, application_attempt_count FROM notification_delivery_intents WHERE match_id=? AND event_type=? LIMIT 1").use { statement ->
            statement.setLong(1, matchId); statement.setString(2, event.name); statement.executeQuery().use { rows ->
                if (rows.next()) DeliveryState.valueOf(rows.getString(1)) to rows.getInt(2) else null
            }
        }
    }

    internal fun deliveryDetails(matchId: Long, event: NotificationEventType): DeliveryDetails? = transaction { connection ->
        connection.prepareStatement("SELECT state, application_attempt_count, terminal_reason, retry_delay_millis, retry_decision_at, due_at FROM notification_delivery_intents WHERE match_id=? AND event_type=? LIMIT 1").use { statement ->
            statement.setLong(1, matchId); statement.setString(2, event.name); statement.executeQuery().use { rows ->
                if (!rows.next()) null else DeliveryDetails(
                    state = DeliveryState.valueOf(rows.getString(1)),
                    attemptCount = rows.getInt(2),
                    terminalReason = rows.getString(3),
                    retryDelayMillis = rows.getLong(4).takeUnless { rows.wasNull() },
                    retryDecisionAt = rows.getObject(5, Instant::class.java),
                    retryDueAt = rows.getObject(6, Instant::class.java),
                )
            }
        }
    }

    /** Writes every source result, but transitions compare only the prior successful observation. */
    fun recordObservation(matchId: Long, result: ObservationResult, status: ObservationStatus? = null, now: Instant = Instant.now()): Int = retryingTransaction { connection ->
        requireValidMatchId(matchId)
        require((result == ObservationResult.SUCCESS) == (status != null)) { "successful observations require a status" }
        val terminalAlreadyObserved = trackingTerminalForUpdate(connection, matchId, now)
        val previous = if (result == ObservationResult.SUCCESS) lastSuccessfulStatus(connection, matchId) else null
        // This comes from committed H2 state, not process memory, so reopen and another store preserve ordering.
        val observedAt = nextObservationTime(connection, matchId, now)
        connection.prepareStatement("INSERT INTO notification_observations (id, match_id, status, observed_at, source_result) VALUES (?, ?, ?, ?, ?)").use { statement ->
            statement.setObject(1, UUID.randomUUID()); statement.setLong(2, matchId); statement.setString(3, status?.name ?: "NONE"); statement.setObject(4, observedAt); statement.setString(5, result.name); statement.executeUpdate()
        }
        if (result != ObservationResult.SUCCESS || terminalAlreadyObserved) return@retryingTransaction 0
        if (status in TERMINAL_OBSERVATION_STATUSES) markTrackingTerminal(connection, matchId, now)
        val event = transition(previous, requireNotNull(status)) ?: return@retryingTransaction 0
        activeTargetsForMatch(connection, matchId).sumOf { target -> insertIntentIfAbsent(connection, target, matchId, event, now) }
    }

    /** Global OFF is one target-level mutation and never changes another target. */
    fun disableAllSubscriptions(target: PushTarget, revision: Long, operation: String, now: Instant = Instant.now()): RevisionResult = transaction { connection ->
        requireValidRevision(revision)
        val row = targetRow(connection, target) ?: return@transaction RevisionResult.CONFLICT
        if (!row.sendable) return@transaction RevisionResult.CONFLICT
        val result = applyRevision(connection, target, row.revision, row.operationHash, revision, operation, now)
        if (result != RevisionResult.APPLIED) return@transaction result
        connection.prepareStatement("UPDATE notification_subscriptions SET active=FALSE, updated_at=? WHERE target_id=? AND active=TRUE").use { statement ->
            statement.setObject(1, now); statement.setObject(2, target.id); statement.executeUpdate()
        }
        result
    }

    /** Provider-proven invalidity: one transaction performs only logical erasure and target-local cleanup. */
    fun invalidateTarget(target: PushTarget, now: Instant = Instant.now()) = transaction { connection ->
        invalidateTargetInTransaction(connection, target, now)
    }

    private fun invalidateTargetInTransaction(connection: java.sql.Connection, target: PushTarget, now: Instant) {
        connection.prepareStatement("UPDATE notification_targets SET sendable=FALSE, registration_value=NULL, invalidated_at=?, updated_at=? WHERE id=?").use { statement ->
            statement.setObject(1, now); statement.setObject(2, now); statement.setObject(3, target.id); statement.executeUpdate()
        }
        connection.prepareStatement("UPDATE notification_subscriptions SET active=FALSE, updated_at=? WHERE target_id=?").use { statement ->
            statement.setObject(1, now); statement.setObject(2, target.id); statement.executeUpdate()
        }
        connection.prepareStatement("UPDATE notification_delivery_intents SET state='TERMINAL_FAILURE', terminal_reason='INVALID_TARGET', updated_at=? WHERE target_id=? AND state IN ('PENDING','RETRY_WAIT','CLAIMED_NOT_STARTED')").use { statement ->
            statement.setObject(1, now); statement.setObject(2, target.id); statement.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO notification_audit_events (id, target_id, category, created_at) VALUES (?, ?, 'target_invalidated', ?)").use { statement ->
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, target.id); statement.setObject(3, now); statement.executeUpdate()
        }
    }

    fun targetProjection(target: PushTarget): TargetProjection? = transaction { connection ->
        targetRow(connection, target)?.let { TargetProjection(target, it.sendable, mode, it.revision) }
    }

    private fun targetByDigest(connection: java.sql.Connection, digest: ByteArray): ExistingTarget? = connection.prepareStatement("SELECT id, registration_value, sendable, accepted_revision, operation_hash FROM notification_targets WHERE provider=? AND target_mode=? AND lookup_digest=?").use { statement ->
        statement.setString(1, PROVIDER); statement.setString(2, mode.name); statement.setBytes(3, digest); statement.executeQuery().use { rows ->
            if (rows.next()) ExistingTarget(PushTarget(rows.getObject(1, UUID::class.java)), rows.getString(2), TargetRow(rows.getLong(4), rows.getBytes(5), rows.getBoolean(3))) else null
        }
    }

    private fun insertTarget(connection: java.sql.Connection, digest: ByteArray, registrationValue: String, revision: Long, operation: String, now: Instant): PushTarget {
        val target = PushTarget(UUID.randomUUID())
        connection.prepareStatement("INSERT INTO notification_targets (id, provider, target_mode, lookup_digest, lookup_key_id, registration_value, sendable, invalidated_at, accepted_revision, operation_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, TRUE, NULL, ?, ?, ?, ?)").use { insert ->
            insert.setObject(1, target.id); insert.setString(2, PROVIDER); insert.setString(3, mode.name); insert.setBytes(4, digest); insert.setString(5, keyId); insert.setString(6, registrationValue); insert.setLong(7, revision); insert.setBytes(8, operationHash(operation)); insert.setObject(9, now); insert.setObject(10, now); insert.executeUpdate()
        }
        return target
    }

    private fun subscriptions(connection: java.sql.Connection, target: PushTarget): List<SubscriptionProjection> = connection.prepareStatement("SELECT match_id, active FROM notification_subscriptions WHERE target_id=? ORDER BY match_id").use { statement ->
        statement.setObject(1, target.id); statement.executeQuery().use { rows -> buildList { while (rows.next()) add(SubscriptionProjection(rows.getLong(1), rows.getBoolean(2))) } }
    }

    private fun lastSuccessfulStatus(connection: java.sql.Connection, matchId: Long): ObservationStatus? = connection.prepareStatement("SELECT status FROM notification_observations WHERE match_id=? AND source_result='SUCCESS' ORDER BY observed_at DESC, id DESC LIMIT 1").use { statement ->
        statement.setLong(1, matchId); statement.executeQuery().use { rows -> if (rows.next()) ObservationStatus.valueOf(rows.getString(1)) else null }
    }

    private fun transition(previous: ObservationStatus?, current: ObservationStatus): NotificationEventType? = when {
        previous == null -> null
        previous in setOf(ObservationStatus.UPCOMING, ObservationStatus.POSTPONED) && current == ObservationStatus.LIVE -> NotificationEventType.START
        previous == ObservationStatus.LIVE && current == ObservationStatus.COMPLETED -> NotificationEventType.END
        previous in setOf(ObservationStatus.UPCOMING, ObservationStatus.POSTPONED) && current == ObservationStatus.COMPLETED -> NotificationEventType.END
        else -> null
    }

    /** A row lock turns terminal observation into a durable one-way latch across concurrent stores. */
    private fun trackingTerminalForUpdate(connection: java.sql.Connection, matchId: Long, now: Instant): Boolean {
        connection.prepareStatement("INSERT INTO notification_match_tracking (match_id, terminal, updated_at) SELECT ?, FALSE, ? WHERE NOT EXISTS (SELECT 1 FROM notification_match_tracking WHERE match_id=?)").use { statement ->
            statement.setLong(1, matchId); statement.setObject(2, now); statement.setLong(3, matchId); statement.executeUpdate()
        }
        return connection.prepareStatement("SELECT terminal FROM notification_match_tracking WHERE match_id=? FOR UPDATE").use { statement ->
            statement.setLong(1, matchId); statement.executeQuery().use { rows -> check(rows.next()); rows.getBoolean(1) }
        }
    }

    private fun markTrackingTerminal(connection: java.sql.Connection, matchId: Long, now: Instant) = connection.prepareStatement("UPDATE notification_match_tracking SET terminal=TRUE, updated_at=? WHERE match_id=?").use { statement ->
        statement.setObject(1, now); statement.setLong(2, matchId); statement.executeUpdate()
    }

    private fun requireValidRegistrationValue(value: String) {
        require(value.isNotBlank() && value.none { it.isISOControl() }) { "registration value is invalid" }
        require(value.toByteArray(StandardCharsets.UTF_8).size <= configuration.registrationValueMaxBytes) { "registration value is too large" }
    }

    private fun activeTargetsForMatch(connection: java.sql.Connection, matchId: Long): List<PushTarget> = connection.prepareStatement("SELECT target_id FROM notification_subscriptions WHERE match_id=? AND active=TRUE").use { statement ->
        statement.setLong(1, matchId); statement.executeQuery().use { rows -> buildList { while (rows.next()) add(PushTarget(rows.getObject(1, UUID::class.java))) } }
    }

    private fun insertIntentIfAbsent(connection: java.sql.Connection, target: PushTarget, matchId: Long, event: NotificationEventType, now: Instant): Int = connection.prepareStatement("INSERT INTO notification_delivery_intents (id, target_id, match_id, event_type, state, application_attempt_count, created_at, updated_at) SELECT ?, ?, ?, ?, 'PENDING', 0, ?, ? WHERE NOT EXISTS (SELECT 1 FROM notification_delivery_intents WHERE target_id=? AND match_id=? AND event_type=?)").use { statement ->
        statement.setObject(1, UUID.randomUUID()); statement.setObject(2, target.id); statement.setLong(3, matchId); statement.setString(4, event.name); statement.setObject(5, now); statement.setObject(6, now); statement.setObject(7, target.id); statement.setLong(8, matchId); statement.setString(9, event.name); statement.executeUpdate()
    }

    // H2's timestamp precision is microseconds.  Retrying on the V1 unique key also covers concurrent cycles.
    private fun nextObservationTime(connection: java.sql.Connection, matchId: Long, requested: Instant): Instant = connection.prepareStatement("SELECT MAX(observed_at) FROM notification_observations WHERE match_id=?").use { statement ->
        statement.setLong(1, matchId); statement.executeQuery().use { rows ->
            rows.next()
            val prior = rows.getObject(1, Instant::class.java)
            if (prior == null || requested > prior) requested else prior.plusMillis(1)
        }
    }

    internal fun readSendableTargetForDelivery(target: PushTarget): SendableDeliveryTarget? = transaction { connection ->
        readSendableTargetForDelivery(connection, target)
    }

    private fun readSendableTargetForDelivery(connection: java.sql.Connection, target: PushTarget): SendableDeliveryTarget? {
        return connection.prepareStatement("SELECT registration_value, sendable, target_mode FROM notification_targets WHERE id=?").use { statement ->
            statement.setObject(1, target.id); statement.executeQuery().use { rows ->
                if (!rows.next() || !rows.getBoolean(2)) null else rows.getString(1)?.let { SendableDeliveryTarget(target, it, FirebaseTargetMode.valueOf(rows.getString(3))) }
            }
        }
    }

    private fun finalizeCall(claim: DeliveryClaim, now: Instant, state: String, reason: String?, dueAt: Instant?, delayMillis: Long? = null): Boolean = transaction { connection ->
        connection.prepareStatement("UPDATE notification_delivery_intents SET state=?, terminal_reason=?, retry_decision_at=?, retry_delay_millis=?, due_at=?, updated_at=? WHERE id=? AND state='CALL_STARTED' AND claim_token=?").use { update ->
            update.setString(1, state); update.setString(2, reason); update.setObject(3, if (state == "RETRY_WAIT") now else null); update.setObject(4, delayMillis); update.setObject(5, dueAt); update.setObject(6, now); update.setObject(7, claim.intentId); update.setObject(8, claim.token)
            update.executeUpdate() == 1
        }
    }

    private fun targetRow(connection: java.sql.Connection, target: PushTarget): TargetRow? = connection.prepareStatement("SELECT accepted_revision, operation_hash, sendable FROM notification_targets WHERE id=?").use { statement ->
        statement.setObject(1, target.id); statement.executeQuery().use { rows -> if (rows.next()) TargetRow(rows.getLong(1), rows.getBytes(2), rows.getBoolean(3)) else null }
    }

    private fun subscriptionActive(connection: java.sql.Connection, target: PushTarget, matchId: Long): Boolean? = connection.prepareStatement("SELECT active FROM notification_subscriptions WHERE target_id=? AND match_id=?").use { statement ->
        statement.setObject(1, target.id); statement.setLong(2, matchId); statement.executeQuery().use { rows -> if (rows.next()) rows.getBoolean(1) else null }
    }

    private fun activeSubscriptionCount(connection: java.sql.Connection, target: PushTarget): Int = connection.prepareStatement("SELECT COUNT(*) FROM notification_subscriptions WHERE target_id=? AND active=TRUE").use { statement ->
        statement.setObject(1, target.id); statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
    }

    private fun insertSubscription(connection: java.sql.Connection, target: PushTarget, matchId: Long, active: Boolean, now: Instant) = connection.prepareStatement("INSERT INTO notification_subscriptions (id, target_id, match_id, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)").use { statement ->
        statement.setObject(1, UUID.randomUUID()); statement.setObject(2, target.id); statement.setLong(3, matchId); statement.setBoolean(4, active); statement.setObject(5, now); statement.setObject(6, now); statement.executeUpdate()
    }

    private fun updateSubscription(connection: java.sql.Connection, target: PushTarget, matchId: Long, active: Boolean, now: Instant) = connection.prepareStatement("UPDATE notification_subscriptions SET active=?, updated_at=? WHERE target_id=? AND match_id=?").use { statement ->
        statement.setBoolean(1, active); statement.setObject(2, now); statement.setObject(3, target.id); statement.setLong(4, matchId); statement.executeUpdate()
    }

    private fun applyRevision(connection: java.sql.Connection, target: PushTarget, current: Long, currentOperationHash: ByteArray, requested: Long, operation: String, now: Instant): RevisionResult {
        requireValidRevision(requested)
        val outcome = revisionOutcome(current, currentOperationHash, requested, operation)
        if (outcome != RevisionResult.APPLIED) return outcome
        connection.prepareStatement("UPDATE notification_targets SET accepted_revision=?, operation_hash=?, updated_at=? WHERE id=? AND accepted_revision=?").use { statement ->
            statement.setLong(1, requested); statement.setBytes(2, operationHash(operation)); statement.setObject(3, now); statement.setObject(4, target.id); statement.setLong(5, current)
            if (statement.executeUpdate() != 1) throw RevisionCasRaceException()
        }
        return RevisionResult.APPLIED
    }

    private fun revisionOutcome(current: Long, currentOperationHash: ByteArray, requested: Long, operation: String): RevisionResult {
        val hash = operationHash(operation)
        return when {
            requested < current -> RevisionResult.STALE
            current == Long.MAX_VALUE -> if (constantTimeEquals(currentOperationHash, hash)) RevisionResult.REPLAYED else RevisionResult.REVISION_EXHAUSTED
            requested == current -> if (constantTimeEquals(currentOperationHash, hash)) RevisionResult.REPLAYED else RevisionResult.CONFLICT
            else -> RevisionResult.APPLIED
        }
    }

    private fun <T> transaction(block: (java.sql.Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try { block(connection).also { connection.commit() } } catch (error: Throwable) { connection.rollback(); throw error }
    }

    private fun <T> retryingTransaction(block: (java.sql.Connection) -> T): T {
        repeat(MAX_MUTATION_RETRIES - 1) {
            try { return transaction(block) } catch (_: SQLIntegrityConstraintViolationException) { }
            catch (_: SQLTransactionRollbackException) { }
            catch (_: RevisionCasRaceException) { }
        }
        return transaction(block)
    }

    override fun close() = dataSource.close()

    private data class TargetRow(val revision: Long, val operationHash: ByteArray, val sendable: Boolean)
    private data class ExistingTarget(val target: PushTarget, val registrationValue: String?, val row: TargetRow)

    companion object {
        private const val PROVIDER = "FIREBASE"
        private const val MAX_MUTATION_RETRIES = 4
        private val TERMINAL_OBSERVATION_STATUSES = setOf(ObservationStatus.COMPLETED, ObservationStatus.CANCELLED)

        fun open(configuration: NotificationConfiguration, random: SecureRandom = SecureRandom()): NotificationStore = openInternal(configuration, random, ::lookupDigest)

        internal fun openForTesting(configuration: NotificationConfiguration, targetDigest: TargetDigest): NotificationStore = openInternal(configuration, SecureRandom(), targetDigest)

        private fun openInternal(configuration: NotificationConfiguration, random: SecureRandom, targetDigest: TargetDigest): NotificationStore {
            check(configuration.enabled) { "disabled notification configuration must not create a store" }
            val config = HikariConfig().apply {
                jdbcUrl = h2JdbcUrl(requireNotNull(configuration.databasePath))
                driverClassName = "org.h2.Driver"
                maximumPoolSize = configuration.jdbcPoolSize
                minimumIdle = 0
                isAutoCommit = false
            }
            val dataSource = HikariDataSource(config)
            try {
                Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
                return NotificationStore(dataSource, configuration, targetDigest).also { it.verifyMetadata(random) }
            } catch (error: Throwable) { dataSource.close(); throw error }
        }

        fun lookupDigest(key: ByteArray, mode: FirebaseTargetMode, registrationValue: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(listOf("vlrgg-match-notification-target-v1", PROVIDER, mode.name, registrationValue).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8))
        }

        private fun h2JdbcUrl(path: java.nio.file.Path): String {
            val normalized = path.toAbsolutePath().normalize()
            val value = normalized.toString()
            if (!normalized.isAbsolute || value.startsWith("//") || value.startsWith("\\\\") || value.any { it.code < 32 || it.code == 127 } || value.any { it in ";?#" }) {
                throw NotificationConfigurationException(ConfigurationCategory.UNSAFE_STORAGE, ConfigurationField.STORAGE_PATH)
            }
            return "jdbc:h2:file:$value;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0"
        }
    }

    private fun verifyMetadata(random: SecureRandom) = transaction { connection ->
        connection.prepareStatement("SELECT target_mode, key_id, verifier_challenge, verifier FROM notification_store_metadata WHERE singleton_id=1").use { statement ->
            statement.executeQuery().use { rows ->
                if (!rows.next()) {
                    val challenge = ByteArray(32).also(random::nextBytes)
                    connection.prepareStatement("INSERT INTO notification_store_metadata (singleton_id, target_mode, key_id, verifier_challenge, verifier, created_at) VALUES (1, ?, ?, ?, ?, ?)").use { insert ->
                        insert.setString(1, mode.name); insert.setString(2, keyId); insert.setBytes(3, challenge); insert.setBytes(4, hmac(lookupKey, challenge)); insert.setObject(5, Instant.now()); insert.executeUpdate()
                    }
                } else {
                    if (rows.getString(1) != mode.name) throw NotificationConfigurationException(ConfigurationCategory.TARGET_MODE_MISMATCH, ConfigurationField.TARGET_MODE)
                    if (!constantTimeEquals(rows.getString(2).toByteArray(StandardCharsets.UTF_8), keyId.toByteArray(StandardCharsets.UTF_8)) || !constantTimeEquals(rows.getBytes(4), hmac(lookupKey, rows.getBytes(3)))) throw NotificationConfigurationException(ConfigurationCategory.DIGEST_KEY_MISMATCH, ConfigurationField.LOOKUP_DIGEST_KEY)
                }
            }
        }
    }
}

class TargetDigestCollisionException : IllegalStateException("target lookup collision")
class SubscriptionLimitExceededException : IllegalStateException("subscription limit exceeded")
private class RevisionCasRaceException : RuntimeException()

private fun requireValidRevision(value: Long) { require(value > 0) { "revision must be a positive signed Long" } }
private fun requireValidMatchId(value: Long) { require(value > 0) { "match id must be positive" } }
private fun operationHash(operation: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(operation.toByteArray(StandardCharsets.UTF_8))
private fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value) }
private fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean = MessageDigest.isEqual(first, second)

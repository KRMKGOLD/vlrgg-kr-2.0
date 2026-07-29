package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@JvmInline value class PushTarget(val id: UUID)

enum class TargetResolution { CREATED, EXISTING, TARGET_REFRESH_REQUIRED }
data class TargetLookupResult(val target: PushTarget?, val resolution: TargetResolution, val revision: RevisionResult?)
enum class RevisionResult { APPLIED, STALE, REPLAYED, CONFLICT, REVISION_EXHAUSTED }
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
        require(registrationValue.toByteArray(StandardCharsets.UTF_8).size <= configuration.registrationValueMaxBytes)
        val digest = targetDigest(lookupKey, mode, registrationValue)
        require(digest.size == 32) { "target digest must be 32 bytes" }
        return transaction { connection ->
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
                        if (!rows.getBoolean(3) || raw == null) return@transaction TargetLookupResult(null, TargetResolution.TARGET_REFRESH_REQUIRED, null)
                        if (!constantTimeEquals(raw.toByteArray(StandardCharsets.UTF_8), registrationValue.toByteArray(StandardCharsets.UTF_8))) throw TargetDigestCollisionException()
                        val result = applyRevision(connection, target, rows.getLong(4), rows.getBytes(5), revision, operation, now)
                        TargetLookupResult(target, TargetResolution.EXISTING, result)
                    }
                }
            }
        }
    }

    fun mutateSubscription(target: PushTarget, matchId: Long, active: Boolean, revision: Long, operation: String, now: Instant = Instant.now()): RevisionResult = transaction { connection ->
        requireValidRevision(revision)
        val row = targetRow(connection, target) ?: return@transaction RevisionResult.CONFLICT
        if (!row.sendable) return@transaction RevisionResult.CONFLICT
        val revisionOutcome = revisionOutcome(row.revision, row.operationHash, revision, operation)
        if (revisionOutcome != RevisionResult.APPLIED) return@transaction revisionOutcome
        val priorActive = subscriptionActive(connection, target, matchId)
        if (active && priorActive != true && activeSubscriptionCount(connection, target) >= configuration.activeSubscriptionsMax) throw SubscriptionLimitExceededException()
        val result = applyRevision(connection, target, row.revision, row.operationHash, revision, operation, now)
        if (result != RevisionResult.APPLIED) return@transaction result
        if (priorActive == null) insertSubscription(connection, target, matchId, active, now) else updateSubscription(connection, target, matchId, active, now)
        result
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

    internal fun readSendableTargetForDelivery(target: PushTarget): SendableDeliveryTarget? = transaction { connection ->
        connection.prepareStatement("SELECT registration_value, sendable, target_mode FROM notification_targets WHERE id=?").use { statement ->
            statement.setObject(1, target.id); statement.executeQuery().use { rows ->
                if (!rows.next() || !rows.getBoolean(2)) null else rows.getString(1)?.let { SendableDeliveryTarget(target, it, FirebaseTargetMode.valueOf(rows.getString(3))) }
            }
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
            if (statement.executeUpdate() != 1) return RevisionResult.CONFLICT
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

    override fun close() = dataSource.close()

    private data class TargetRow(val revision: Long, val operationHash: ByteArray, val sendable: Boolean)

    companion object {
        private const val PROVIDER = "FIREBASE"

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

private fun requireValidRevision(value: Long) { require(value > 0) { "revision must be a positive signed Long" } }
private fun operationHash(operation: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(operation.toByteArray(StandardCharsets.UTF_8))
private fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value) }
private fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean = MessageDigest.isEqual(first, second)

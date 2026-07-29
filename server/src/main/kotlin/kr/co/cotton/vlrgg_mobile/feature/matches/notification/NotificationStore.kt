package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@JvmInline value class PushTarget(val id: UUID)

enum class TargetResolution { CREATED, EXISTING, TARGET_REFRESH_REQUIRED }
data class TargetLookupResult(val target: PushTarget?, val resolution: TargetResolution)
enum class RevisionResult { APPLIED, STALE, REPLAYED, CONFLICT, REVISION_EXHAUSTED }

class NotificationStore private constructor(
    private val dataSource: HikariDataSource,
    private val configuration: NotificationConfiguration,
) : AutoCloseable {
    private val mode get() = configuration.targetMode
    private val lookupKey get() = requireNotNull(configuration.lookupDigestKey)
    private val keyId get() = sha256Hex(lookupKey)

    /** Exposed owns database registration; repository SQL remains explicit to isolate H2-only claim syntax. */
    private val exposedDatabase = Database.connect(dataSource)

    fun findOrRegister(registrationValue: String, operation: String, revision: Long, now: Instant = Instant.now()): TargetLookupResult {
        require(registrationValue.toByteArray(StandardCharsets.UTF_8).size <= configuration.registrationValueMaxBytes)
        require(revision > 0)
        val digest = lookupDigest(lookupKey, mode, registrationValue)
        return transaction { connection ->
            connection.prepareStatement("SELECT id, registration_value, sendable, accepted_revision, operation_hash FROM notification_targets WHERE provider=? AND target_mode=? AND lookup_digest=?").use { statement ->
                statement.setString(1, PROVIDER)
                statement.setString(2, mode.name)
                statement.setBytes(3, digest)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        val id = UUID.randomUUID()
                        connection.prepareStatement("INSERT INTO notification_targets (id, provider, target_mode, lookup_digest, lookup_key_id, registration_value, sendable, invalidated_at, accepted_revision, operation_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)").use { insert ->
                            insert.setObject(1, id); insert.setString(2, PROVIDER); insert.setString(3, mode.name); insert.setBytes(4, digest); insert.setString(5, keyId); insert.setString(6, registrationValue); insert.setBoolean(7, true); insert.setLong(8, revision); insert.setBytes(9, operationHash(operation)); insert.setObject(10, now); insert.setObject(11, now); insert.executeUpdate()
                        }
                        TargetLookupResult(PushTarget(id), TargetResolution.CREATED)
                    } else {
                        val id = rows.getObject(1, UUID::class.java)
                        val raw = rows.getString(2)
                        if (!rows.getBoolean(3) || raw == null) return@transaction TargetLookupResult(null, TargetResolution.TARGET_REFRESH_REQUIRED)
                        if (!constantTimeEquals(raw.toByteArray(StandardCharsets.UTF_8), registrationValue.toByteArray(StandardCharsets.UTF_8))) throw TargetDigestCollisionException()
                        applyRevision(connection, id, rows.getLong(4), rows.getBytes(5), revision, operation, now)
                        TargetLookupResult(PushTarget(id), TargetResolution.EXISTING)
                    }
                }
            }
        }
    }

    fun mutateSubscription(target: PushTarget, matchId: Long, active: Boolean, revision: Long, operation: String, now: Instant = Instant.now()): RevisionResult = transaction { connection ->
        val row = connection.prepareStatement("SELECT accepted_revision, operation_hash, sendable FROM notification_targets WHERE id=?").use { statement ->
            statement.setObject(1, target.id); statement.executeQuery().use { rows -> if (rows.next()) Triple(rows.getLong(1), rows.getBytes(2), rows.getBoolean(3)) else null }
        } ?: return@transaction RevisionResult.CONFLICT
        if (!row.third) return@transaction RevisionResult.CONFLICT
        val result = applyRevision(connection, target.id, row.first, row.second, revision, operation, now)
        if (result != RevisionResult.APPLIED) return@transaction result
        if (active) {
            connection.prepareStatement("SELECT COUNT(*) FROM notification_subscriptions WHERE target_id=? AND active=TRUE").use { statement ->
                statement.setObject(1, target.id); statement.executeQuery().use { count -> count.next(); if (count.getInt(1) >= configuration.activeSubscriptionsMax) throw SubscriptionLimitExceededException() }
            }
        }
        val updated = connection.prepareStatement("UPDATE notification_subscriptions SET active=?, updated_at=? WHERE target_id=? AND match_id=?").use { statement ->
            statement.setBoolean(1, active); statement.setObject(2, now); statement.setObject(3, target.id); statement.setLong(4, matchId); statement.executeUpdate()
        }
        if (updated == 0) connection.prepareStatement("INSERT INTO notification_subscriptions (id, target_id, match_id, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)").use { statement ->
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, target.id); statement.setLong(3, matchId); statement.setBoolean(4, active); statement.setObject(5, now); statement.setObject(6, now); statement.executeUpdate()
        }
        result
    }

    fun invalidateTarget(target: PushTarget, now: Instant = Instant.now()) = transaction { connection ->
        connection.prepareStatement("UPDATE notification_targets SET sendable=FALSE, registration_value=NULL, invalidated_at=?, updated_at=? WHERE id=?").use { it.setObject(1, now); it.setObject(2, now); it.setObject(3, target.id); it.executeUpdate() }
        connection.prepareStatement("UPDATE notification_subscriptions SET active=FALSE, updated_at=? WHERE target_id=?").use { it.setObject(1, now); it.setObject(2, target.id); it.executeUpdate() }
        connection.prepareStatement("UPDATE notification_delivery_intents SET state='TERMINAL_FAILURE', terminal_reason='INVALID_TARGET', updated_at=? WHERE target_id=? AND state IN ('PENDING','RETRY_WAIT','CLAIMED_NOT_STARTED')").use { it.setObject(1, now); it.setObject(2, target.id); it.executeUpdate() }
        connection.prepareStatement("INSERT INTO notification_audit_events (id, target_id, category, created_at) VALUES (?, ?, 'target_invalidated', ?)").use { it.setObject(1, UUID.randomUUID()); it.setObject(2, target.id); it.setObject(3, now); it.executeUpdate() }
    }

    fun rawValueForTest(target: PushTarget): String? = transaction { connection ->
        connection.prepareStatement("SELECT registration_value FROM notification_targets WHERE id=?").use { statement -> statement.setObject(1, target.id); statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null } }
    }

    private fun applyRevision(connection: java.sql.Connection, id: UUID, current: Long, currentOperationHash: ByteArray, requested: Long, operation: String, now: Instant): RevisionResult {
        if (requested <= 0) return RevisionResult.CONFLICT
        val hash = operationHash(operation)
        when {
            requested < current -> return RevisionResult.STALE
            requested == current -> return if (constantTimeEquals(currentOperationHash, hash)) RevisionResult.REPLAYED else RevisionResult.CONFLICT
            current == Long.MAX_VALUE -> return RevisionResult.REVISION_EXHAUSTED
        }
        connection.prepareStatement("UPDATE notification_targets SET accepted_revision=?, operation_hash=?, updated_at=? WHERE id=? AND accepted_revision=?").use { statement ->
            statement.setLong(1, requested); statement.setBytes(2, hash); statement.setObject(3, now); statement.setObject(4, id); statement.setLong(5, current)
            if (statement.executeUpdate() != 1) return RevisionResult.CONFLICT
        }
        return RevisionResult.APPLIED
    }

    private fun <T> transaction(block: (java.sql.Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try { block(connection).also { connection.commit() } } catch (error: Throwable) { connection.rollback(); throw error }
    }

    override fun close() = dataSource.close()

    companion object {
        private const val PROVIDER = "FIREBASE"

        fun open(configuration: NotificationConfiguration, random: SecureRandom = SecureRandom()): NotificationStore {
            check(configuration.enabled) { "disabled notification configuration must not create a store" }
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:h2:file:${requireNotNull(configuration.databasePath).toAbsolutePath()};AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = configuration.jdbcPoolSize
                minimumIdle = 0
                isAutoCommit = false
            }
            val dataSource = HikariDataSource(config)
            try {
                Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
                val store = NotificationStore(dataSource, configuration)
                store.verifyMetadata(random)
                return store
            } catch (error: Throwable) { dataSource.close(); throw error }
        }

        fun lookupDigest(key: ByteArray, mode: FirebaseTargetMode, registrationValue: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(listOf("vlrgg-match-notification-target-v1", PROVIDER, mode.name, registrationValue).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8))
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
                    if (!constantTimeEquals(rows.getString(2).toByteArray(), keyId.toByteArray()) || !constantTimeEquals(rows.getBytes(4), hmac(lookupKey, rows.getBytes(3)))) throw NotificationConfigurationException(ConfigurationCategory.DIGEST_KEY_MISMATCH, ConfigurationField.LOOKUP_DIGEST_KEY)
                }
            }
        }
    }
}

class TargetDigestCollisionException : IllegalStateException("target lookup collision")
class SubscriptionLimitExceededException : IllegalStateException("subscription limit exceeded")

private fun operationHash(operation: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(operation.toByteArray(StandardCharsets.UTF_8))
private fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value) }
private fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean = MessageDigest.isEqual(first, second)

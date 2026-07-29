package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationStoreTest {
    @Test fun `canonical lookup HMAC is stable`() {
        val digest = NotificationStore.lookupDigest(ByteArray(32), FirebaseTargetMode.FID, "abc")
        assertEquals("5d2e3118e8335fa0232c5cdcfa44748a0131d31c2c5188b3666a16b6e9d85ad1", digest.joinToString("") { "%02x".format(it) })
    }

    @Test fun `existing target returns every revision result without mutation on rejection`() {
        openStore().use { store ->
            val first = store.findOrRegister("registration-a", "register", 1)
            val target = requireNotNull(first.target)
            assertEquals(RevisionResult.APPLIED, first.revision)
            assertEquals(RevisionResult.APPLIED, store.findOrRegister("registration-a", "refresh", 2).revision)
            assertEquals(RevisionResult.REPLAYED, store.findOrRegister("registration-a", "refresh", 2).revision)
            assertEquals(RevisionResult.CONFLICT, store.findOrRegister("registration-a", "different", 2).revision)
            assertEquals(RevisionResult.STALE, store.findOrRegister("registration-a", "stale", 1).revision)
            assertEquals(2, requireNotNull(store.targetProjection(target)).acceptedRevision)
            assertEquals(RevisionResult.APPLIED, store.findOrRegister("registration-a", "terminal", Long.MAX_VALUE).revision)
            assertEquals(RevisionResult.REPLAYED, store.findOrRegister("registration-a", "terminal", Long.MAX_VALUE).revision)
            assertEquals(RevisionResult.CONFLICT, store.findOrRegister("registration-a", "other-terminal", Long.MAX_VALUE).revision)
            assertEquals(Long.MAX_VALUE, requireNotNull(store.targetProjection(target)).acceptedRevision)
            assertFailsWith<IllegalArgumentException> { store.findOrRegister("registration-a", "invalid", 0) }
            assertFailsWith<IllegalArgumentException> { store.findOrRegister("registration-a", "invalid", -1) }
        }
    }

    @Test fun `subscription cap counts growth only and global off is target isolated`() {
        val path = freshPath()
        openStore(path, limit = 1).use { store ->
            val first = requireNotNull(store.findOrRegister("registration-a", "register", 1).target)
            val second = requireNotNull(store.findOrRegister("registration-b", "register", 1).target)
            assertEquals(RevisionResult.APPLIED, store.mutateSubscription(first, 42, true, 2, "subscribe:42"))
            assertEquals(RevisionResult.APPLIED, store.mutateSubscription(first, 42, true, 3, "subscribe:42-repeat"))
            assertEquals(RevisionResult.APPLIED, store.mutateSubscription(second, 99, true, 2, "subscribe:99"))
            assertFailsWith<SubscriptionLimitExceededException> { store.mutateSubscription(first, 43, true, 4, "subscribe:43") }
            assertEquals(RevisionResult.APPLIED, store.disableAllSubscriptions(first, 4, "global-off"))
            assertEquals(RevisionResult.APPLIED, store.mutateSubscription(first, 43, true, 5, "subscribe:43"))
            assertEquals(1, activeCount(first, path))
            assertEquals(1, activeCount(second, path))
            assertEquals(2, subscriptionRows(first, path))
            assertEquals(1, subscriptionRows(first, path, 42))
        }
    }

    @Test fun `active digest collision fails closed using injected seam`() {
        openStore(digest = { _, _, _ -> ByteArray(32) }).use { store ->
            store.findOrRegister("registration-a", "register", 1)
            assertFailsWith<TargetDigestCollisionException> { store.findOrRegister("registration-b", "register", 1) }
        }
    }

    @Test fun `provider invalidity transaction erases only its raw value and preserves ambiguous intents`() {
        val path = freshPath()
        openStore(path).use { store ->
            val invalid = requireNotNull(store.findOrRegister("registration-a", "register", 1).target)
            val unaffected = requireNotNull(store.findOrRegister("registration-b", "register", 1).target)
            store.mutateSubscription(invalid, 42, true, 2, "subscribe:42")
            store.mutateSubscription(unaffected, 42, true, 2, "subscribe:42")
            insertIntents(invalid, listOf("PENDING", "RETRY_WAIT", "CLAIMED_NOT_STARTED", "CALL_STARTED", "UNKNOWN"), path)
            store.invalidateTarget(invalid)
            assertNull(rawValue(invalid, path))
            assertFalse(requireNotNull(store.targetProjection(invalid)).sendable)
            assertTrue(requireNotNull(store.targetProjection(unaffected)).sendable)
            assertEquals(0, activeCount(invalid, path))
            assertEquals(1, activeCount(unaffected, path))
            assertEquals(listOf("TERMINAL_FAILURE", "TERMINAL_FAILURE", "TERMINAL_FAILURE", "CALL_STARTED", "UNKNOWN"), intentStates(invalid, path))
            assertEquals(TargetResolution.TARGET_REFRESH_REQUIRED, store.findOrRegister("registration-a", "register", 3).resolution)
        }
    }

    @Test fun `reopen fails closed for persisted key and mode mismatch`() {
        val path = freshPath()
        openStore(path).close()
        val keyFailure = assertFailsWith<NotificationConfigurationException> { openStore(path, key = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") }
        assertEquals(ConfigurationCategory.DIGEST_KEY_MISMATCH, keyFailure.category)
        val modeFailure = assertFailsWith<NotificationConfigurationException> { openStore(path, mode = FirebaseTargetMode.LEGACY_TOKEN) }
        assertEquals(ConfigurationCategory.TARGET_MODE_MISMATCH, modeFailure.category)
    }

    private fun openStore(path: String = freshPath(), mode: FirebaseTargetMode = FirebaseTargetMode.FID, key: String = ZERO_KEY, limit: Int = 100, digest: TargetDigest? = null): NotificationStore {
        return digest?.let { NotificationStore.openForTesting(configuration(path, mode, key, limit), it) } ?: NotificationStore.open(configuration(path, mode, key, limit))
    }

    private fun configuration(path: String, mode: FirebaseTargetMode, key: String, limit: Int) = NotificationConfiguration.fromEnvironment(
        mapOf(
            "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
            "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1", "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to key,
            "VLRGG_NOTIFICATIONS_FIREBASE_TARGET_MODE" to mode.name, "VLRGG_NOTIFICATIONS_ACTIVE_SUBSCRIPTIONS" to limit.toString(),
        ), ServerListenerConfiguration("127.0.0.1", 8080),
    )

    private fun freshPath() = Files.createTempDirectory("vlrgg-stage1-store").resolve("notification-store").absolutePathString()
    private fun connection(path: String) = DriverManager.getConnection("jdbc:h2:file:$path;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0")
    private fun activeCount(target: PushTarget, path: String): Int = connection(path).use { connection -> connection.prepareStatement("SELECT COUNT(*) FROM notification_subscriptions WHERE target_id=? AND active=TRUE").use { statement -> statement.setObject(1, target.id); statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) } } }
    private fun subscriptionRows(target: PushTarget, path: String, matchId: Long? = null): Int = connection(path).use { connection ->
        val sql = if (matchId == null) "SELECT COUNT(*) FROM notification_subscriptions WHERE target_id=?" else "SELECT COUNT(*) FROM notification_subscriptions WHERE target_id=? AND match_id=?"
        connection.prepareStatement(sql).use { statement -> statement.setObject(1, target.id); if (matchId != null) statement.setLong(2, matchId); statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) } }
    }
    private fun rawValue(target: PushTarget, path: String): String? = connection(path).use { connection -> connection.prepareStatement("SELECT registration_value FROM notification_targets WHERE id=?").use { statement -> statement.setObject(1, target.id); statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null } } }
    private fun insertIntents(target: PushTarget, states: List<String>, path: String) = connection(path).use { connection ->
        states.forEachIndexed { index, state -> connection.prepareStatement("INSERT INTO notification_delivery_intents (id, target_id, match_id, event_type, state, application_attempt_count, created_at, updated_at) VALUES (?, ?, ?, 'START', ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").use { statement -> statement.setObject(1, UUID.randomUUID()); statement.setObject(2, target.id); statement.setLong(3, index.toLong() + 1); statement.setString(4, state); statement.executeUpdate() } }
    }
    private fun intentStates(target: PushTarget, path: String): List<String> = connection(path).use { connection -> connection.prepareStatement("SELECT state FROM notification_delivery_intents WHERE target_id=? ORDER BY match_id").use { statement -> statement.setObject(1, target.id); statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } } } }

    private companion object { const val ZERO_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" }
}

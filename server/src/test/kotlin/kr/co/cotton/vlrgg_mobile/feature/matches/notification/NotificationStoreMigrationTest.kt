package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import java.sql.DriverManager
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway

class NotificationStoreMigrationTest {
    @Test fun `V1 terminal observations backfill the V2 one-way polling latch`() {
        val path = Files.createTempDirectory("vlrgg-tracker-migration").resolve("store").absolutePathString()
        val terminalAt = Instant.parse("2026-07-29T00:00:00Z")
        migrateV1(path)
        insertV1ActiveSubscriptionAndTerminalObservation(path, matchId = 42, observedAt = terminalAt)

        store(path).use { store ->
            assertEquals(emptyList(), store.activeMatchIds())
        }

        DriverManager.getConnection(jdbcUrl(path)).use { connection ->
            connection.prepareStatement("SELECT terminal, updated_at FROM notification_match_tracking WHERE match_id=?").use { statement ->
                statement.setLong(1, 42)
                statement.executeQuery().use { rows ->
                    kotlin.test.assertTrue(rows.next())
                    kotlin.test.assertTrue(rows.getBoolean(1))
                    assertEquals(terminalAt, rows.getObject(2, OffsetDateTime::class.java).toInstant())
                }
            }
        }
    }

    @Test fun `fresh V2 database keeps an active subscription pollable before any terminal observation`() {
        val path = Files.createTempDirectory("vlrgg-tracker-fresh").resolve("store").absolutePathString()
        store(path).use { store ->
            store.reconcileSubscription("target", 42, true, 1)
            assertEquals(listOf(42L), store.activeMatchIds())
        }
    }

    @Test fun `fresh schema indexes match lookup subscriptions by match and does not duplicate target digest uniqueness`() {
        val path = Files.createTempDirectory("vlrgg-index-migration").resolve("store").absolutePathString()
        store(path).use { }

        DriverManager.getConnection(jdbcUrl(path)).use { connection ->
            val indexes = connection.prepareStatement("SELECT index_name FROM information_schema.indexes WHERE table_name=?").use { statement ->
                statement.setString(1, "NOTIFICATION_SUBSCRIPTIONS")
                statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
            assertTrue(indexes.contains("NOTIFICATION_SUBSCRIPTIONS_MATCH_ACTIVE_IDX"))
            val targetIndexes = connection.prepareStatement("SELECT index_name FROM information_schema.indexes WHERE table_name=?").use { statement ->
                statement.setString(1, "NOTIFICATION_TARGETS")
                statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
            assertFalse(targetIndexes.contains("NOTIFICATION_TARGETS_DIGEST_IDX"))
        }
    }

    private fun migrateV1(path: String) {
        Flyway.configure().dataSource(jdbcUrl(path), null, null).locations("classpath:db/migration").target("1").load().migrate()
    }

    private fun insertV1ActiveSubscriptionAndTerminalObservation(path: String, matchId: Long, observedAt: Instant) {
        DriverManager.getConnection(jdbcUrl(path)).use { connection ->
            val targetId = UUID.randomUUID()
            connection.prepareStatement("INSERT INTO notification_targets (id, provider, target_mode, lookup_digest, lookup_key_id, registration_value, sendable, invalidated_at, accepted_revision, operation_hash, created_at, updated_at) VALUES (?, 'FIREBASE', 'FID', ?, 'test', 'target', TRUE, NULL, 1, ?, ?, ?)").use { statement ->
                statement.setObject(1, targetId)
                statement.setBytes(2, ByteArray(32))
                statement.setBytes(3, ByteArray(32))
                statement.setObject(4, observedAt)
                statement.setObject(5, observedAt)
                statement.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO notification_subscriptions (id, target_id, match_id, active, created_at, updated_at) VALUES (?, ?, ?, TRUE, ?, ?)").use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, targetId)
                statement.setLong(3, matchId)
                statement.setObject(4, observedAt)
                statement.setObject(5, observedAt)
                statement.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO notification_observations (id, match_id, status, observed_at, source_result) VALUES (?, ?, 'COMPLETED', ?, 'SUCCESS')").use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setLong(2, matchId)
                statement.setObject(3, observedAt)
                statement.executeUpdate()
            }
        }
    }

    private fun store(path: String) = NotificationStore.open(NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ), ServerListenerConfiguration("127.0.0.1", 8080)))

    private fun jdbcUrl(path: String) = "jdbc:h2:file:$path;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0"
}

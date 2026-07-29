package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NotificationConfigurationTest {
    @Test fun `disabled defaults do not require storage firebase or digest factories`() {
        val configuration = NotificationConfiguration.fromEnvironment(emptyMap(), ServerListenerConfiguration.fromEnvironment(emptyMap()))

        assertFalse(configuration.enabled)
        assertNull(configuration.databasePath)
        assertNull(configuration.lookupDigestKey)
    }

    @Test fun `notification api is strictly loopback even when feature is disabled`() {
        val exception = assertFailsWith<NotificationConfigurationException> {
            NotificationConfiguration.fromEnvironment(mapOf("VLRGG_NOTIFICATIONS_API_ENABLED" to "true"), ServerListenerConfiguration("0.0.0.0", 8080))
        }
        assertEquals(ConfigurationCategory.UNSAFE_LISTENER, exception.category)
        assertEquals(ConfigurationField.LISTENER_HOST, exception.field)
    }

    @Test fun `enabled configuration rejects missing lookup key before resource work`() {
        val exception = assertFailsWith<NotificationConfigurationException> {
            NotificationConfiguration.fromEnvironment(enabledEnvironment() - "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY", ServerListenerConfiguration("127.0.0.1", 8080))
        }
        assertEquals(ConfigurationCategory.MISSING_REQUIRED, exception.category)
        assertEquals(ConfigurationField.LOOKUP_DIGEST_KEY, exception.field)
    }

    @Test fun `strict inputs reject coercion and cross field lease`() {
        val boolean = assertFailsWith<NotificationConfigurationException> {
            NotificationConfiguration.fromEnvironment(mapOf("VLRGG_NOTIFICATIONS_ENABLED" to "TRUE"), ServerListenerConfiguration("127.0.0.1", 8080))
        }
        assertEquals(ConfigurationCategory.INVALID_BOOLEAN, boolean.category)
        val lease = assertFailsWith<NotificationConfigurationException> {
            NotificationConfiguration.fromEnvironment(enabledEnvironment() + ("VLRGG_NOTIFICATIONS_CLAIM_LEASE_MILLIS" to "30000"), ServerListenerConfiguration("127.0.0.1", 8080))
        }
        assertEquals(ConfigurationCategory.INVALID_CROSS_FIELD, lease.category)
    }

    @Test fun `storage path rejects H2 setting URL query remote and control injection`() {
        listOf(
            "/tmp/store;AUTO_SERVER=TRUE", "/tmp/store?AUTO_SERVER=TRUE", "/tmp/store#fragment",
            "jdbc:h2:mem:notification", "//server/share/notification", "/tmp/store\u0000suffix",
        ).forEach { path ->
            val exception = assertFailsWith<NotificationConfigurationException> {
                NotificationConfiguration.fromEnvironment(enabledEnvironment() + ("VLRGG_NOTIFICATIONS_STORAGE_PATH" to path), ServerListenerConfiguration("127.0.0.1", 8080))
            }
            assertEquals(ConfigurationCategory.UNSAFE_STORAGE, exception.category)
            assertEquals(ConfigurationField.STORAGE_PATH, exception.field)
        }
    }

    @Test fun `enum validation deterministically precedes range validation`() {
        val exception = assertFailsWith<NotificationConfigurationException> {
            NotificationConfiguration.fromEnvironment(
                enabledEnvironment() + mapOf("VLRGG_NOTIFICATIONS_FIREBASE_TARGET_MODE" to "fid", "VLRGG_NOTIFICATIONS_REQUEST_BODY_BYTES" to "0"),
                ServerListenerConfiguration("127.0.0.1", 8080),
            )
        }
        assertEquals(ConfigurationCategory.UNSUPPORTED_TARGET_MODE, exception.category)
        assertEquals(ConfigurationField.TARGET_MODE, exception.field)
    }

    @Test fun `disabled configuration creates no storage resource`() {
        val path = Files.createTempDirectory("vlrgg-disabled").resolve("would-not-open")
        val configuration = NotificationConfiguration.fromEnvironment(mapOf("VLRGG_NOTIFICATIONS_STORAGE_PATH" to path.toString()), ServerListenerConfiguration("127.0.0.1", 8080))

        assertFalse(configuration.enabled)
        assertNull(configuration.databasePath)
        assertFalse(Files.exists(path.resolveSibling("would-not-open.mv.db")))
    }

    private fun enabledEnvironment() = mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true",
        "VLRGG_NOTIFICATIONS_STORAGE_PATH" to "/tmp/vlrgg-stage1-test",
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    )
}

package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NotificationStoreTest {
    @Test fun `revisions are ordered replay-safe and subscriptions are unique`() {
        openStore().use { store ->
            val target = requireNotNull(store.findOrRegister("registration-a", "register", 1).target)
            assertEquals(RevisionResult.APPLIED, store.mutateSubscription(target, 42, active = true, revision = 2, operation = "subscribe:42"))
            assertEquals(RevisionResult.REPLAYED, store.mutateSubscription(target, 42, active = true, revision = 2, operation = "subscribe:42"))
            assertEquals(RevisionResult.CONFLICT, store.mutateSubscription(target, 42, active = false, revision = 2, operation = "unsubscribe:42"))
            assertEquals(RevisionResult.STALE, store.mutateSubscription(target, 42, active = false, revision = 1, operation = "unsubscribe:42"))
        }
    }

    @Test fun `provider invalidity logically erases raw value and tombstone requires refresh`() {
        openStore().use { store ->
            val target = requireNotNull(store.findOrRegister("registration-a", "register", 1).target)
            store.invalidateTarget(target)
            assertNull(store.rawValueForTest(target))
            assertEquals(TargetResolution.TARGET_REFRESH_REQUIRED, store.findOrRegister("registration-a", "register", 2).resolution)
        }
    }

    @Test fun `reopen fails closed for persisted target mode mismatch`() {
        val directory = Files.createTempDirectory("vlrgg-stage1-store")
        val path = directory.resolve("notification-store").absolutePathString()
        openStore(path, FirebaseTargetMode.FID).close()
        val exception = assertFailsWith<NotificationConfigurationException> { openStore(path, FirebaseTargetMode.LEGACY_TOKEN) }
        assertEquals(ConfigurationCategory.TARGET_MODE_MISMATCH, exception.category)
    }

    private fun openStore(path: String = Files.createTempDirectory("vlrgg-stage1-store").resolve("notification-store").absolutePathString(), mode: FirebaseTargetMode = FirebaseTargetMode.FID): NotificationStore =
        NotificationStore.open(
            NotificationConfiguration.fromEnvironment(
                mapOf(
                    "VLRGG_NOTIFICATIONS_ENABLED" to "true",
                    "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
                    "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
                    "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    "VLRGG_NOTIFICATIONS_FIREBASE_TARGET_MODE" to mode.name,
                ),
                ServerListenerConfiguration("127.0.0.1", 8080),
            ),
        )
}

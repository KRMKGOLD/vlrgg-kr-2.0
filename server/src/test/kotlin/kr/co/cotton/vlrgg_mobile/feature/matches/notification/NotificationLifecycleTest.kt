package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NotificationLifecycleTest {
    @Test fun `firebase collision fails before store factory invocation`() {
        var storesOpened = 0
        val error = assertFailsWith<NotificationConfigurationException> {
            acquireNotificationRuntime(config(), NotificationRuntimeFactories(
                precheck = { throw NotificationConfigurationException(ConfigurationCategory.FIREBASE_APP_NAME_COLLISION, ConfigurationField.APP_INSTANCE_ID) },
                openStore = { storesOpened++; error("store factory must not run after collision") },
            ))
        }
        assertEquals(ConfigurationCategory.FIREBASE_APP_NAME_COLLISION, error.category)
        assertEquals(0, storesOpened)
    }

    @Test fun `startup failure rolls back firebase then store in reverse acquisition order`() {
        val events = mutableListOf<String>()
        val error = assertFailsWith<IllegalStateException> {
            acquireNotificationRuntime(config(), NotificationRuntimeFactories(
                precheck = {},
                openStore = { NotificationStore.open(it) },
                createFirebase = { RecordingFirebase(events) },
                createProvider = { error("provider creation failed") },
                closeStore = { events += "store"; it.close() },
            ))
        }
        assertEquals("provider creation failed", error.message)
        assertEquals(listOf("firebase", "store"), events)
    }

    @Test fun `normal stop joins delivery then tracking and closes owned resources once`() = runBlocking {
        val events = mutableListOf<String>()
        NotificationStore.open(config()).use { store ->
            val resources = OwnedNotificationResources(RecordingFirebase(events), store) { events += "store" }
            val scope = CoroutineScope(Dispatchers.Default)
            resources.trackTracking(scope.launch { try { awaitCancellation() } finally { events += "tracking" } })
            resources.trackDelivery(scope.launch { try { awaitCancellation() } finally { events += "delivery" } })
            resources.stopAndJoin()
            resources.stopAndJoin()
            assertEquals(listOf("delivery", "tracking", "firebase", "store"), events)
        }
    }

    @Test fun `disabled configuration acquires no notification resource`() {
        var calls = 0
        val disabled = NotificationConfiguration.fromEnvironment(emptyMap(), ServerListenerConfiguration("127.0.0.1", 8080))
        assertNull(acquireNotificationRuntime(disabled, NotificationRuntimeFactories(
            precheck = { calls++ },
            openStore = { calls++; error("disabled") },
            createFirebase = { calls++; error("disabled") },
            createProvider = { calls++; error("disabled") },
        )))
        assertEquals(0, calls)
    }

    private fun config() = NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true",
        "VLRGG_NOTIFICATIONS_STORAGE_PATH" to Files.createTempDirectory("lifecycle").resolve("store").absolutePathString(),
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ), ServerListenerConfiguration("127.0.0.1", 8080))

    private class RecordingFirebase(private val events: MutableList<String>) : OwnedFirebaseResource {
        override fun close() { events += "firebase" }
    }
}

package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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

    @Test fun `provider startup failure rolls back firebase then store in reverse acquisition order`() {
        val events = mutableListOf<String>()
        val error = assertFailsWith<IllegalStateException> {
            acquireNotificationRuntime(config(), NotificationRuntimeFactories(
                precheck = {}, openStore = { NotificationStore.open(it) },
                createFirebase = { RecordingFirebase(events) }, createProvider = { error("provider creation failed") },
                closeStore = { events += "store"; it.close() },
            ))
        }
        assertEquals("provider creation failed", error.message)
        assertEquals(listOf("firebase", "store"), events)
    }

    @Test fun `listener bind failure stops the acquired owner through the shared runner`() {
        val events = mutableListOf<String>()
        val failure = assertFailsWith<IllegalStateException> {
            runWithNotificationRuntime(config(), NotificationRuntimeFactories(
                precheck = {}, openStore = { NotificationStore.open(it) },
                createFirebase = { RecordingFirebase(events) }, createProvider = { AcceptedProvider },
                closeStore = { events += "store"; it.close() },
            )) { error("listener bind failed") }
        }
        assertEquals("listener bind failed", failure.message)
        assertEquals(listOf("firebase", "store"), events)
    }

    @Test fun `normal stop waits for both entered workers then closes in LIFO order exactly once`() = runBlocking {
        val events = mutableListOf<String>()
        NotificationStore.open(config()).use { store ->
            val resources = OwnedNotificationResources(RecordingFirebase(events), store) { events += "store" }
            val trackingEntered = CompletableDeferred<Unit>()
            val deliveryEntered = CompletableDeferred<Unit>()
            val scope = CoroutineScope(Dispatchers.Default)
            resources.startTracking(scope) { worker("tracking", trackingEntered, events) }
            resources.startDelivery(scope) { worker("delivery", deliveryEntered, events) }
            trackingEntered.await(); deliveryEntered.await()
            resources.stopAndJoin()
            resources.stopAndJoin()
            assertEquals(listOf("delivery", "tracking", "firebase", "store"), events)
        }
    }

    @Test fun `concurrent stop callers await and receive the same cleanup failure`() = runBlocking {
        NotificationStore.open(config()).use { store ->
            val failure = IllegalStateException("firebase cleanup failed")
            val resources = OwnedNotificationResources(RecordingFirebase(mutableListOf(), failure), store) { }
            val first = async(Dispatchers.Default) { runCatching { resources.stopAndJoin() }.exceptionOrNull() }
            val second = async(Dispatchers.Default) { runCatching { resources.stopBlocking() }.exceptionOrNull() }
            val firstFailure = requireNotNull(first.await())
            assertSame(firstFailure, requireNotNull(second.await()))
            assertSame(failure, firstFailure)
        }
    }

    @Test fun `close barrier rejects a racing worker before its body can execute`() = runBlocking {
        val events = mutableListOf<String>()
        NotificationStore.open(config()).use { store ->
            val resources = OwnedNotificationResources(RecordingFirebase(events), store) { events += "store" }
            val deliveryEntered = CompletableDeferred<Unit>()
            val deliveryCancelling = CompletableDeferred<Unit>()
            val allowDeliveryFinish = CompletableDeferred<Unit>()
            val postBarrierEntered = CompletableDeferred<Unit>()
            val scope = CoroutineScope(Dispatchers.Default)
            resources.startDelivery(scope) {
                deliveryEntered.complete(Unit)
                try { awaitCancellation() } finally {
                    events += "delivery"
                    deliveryCancelling.complete(Unit)
                    withContext(NonCancellable) { allowDeliveryFinish.await() }
                }
            }
            deliveryEntered.await()
            val stop = async(Dispatchers.Default) { resources.stopAndJoin() }
            deliveryCancelling.await()
            val rejected = resources.startTracking(scope) { postBarrierEntered.complete(Unit) }
            assertNull(rejected)
            allowDeliveryFinish.complete(Unit)
            stop.await()
            assertEquals(listOf("delivery", "firebase", "store"), events)
            assertFalse(postBarrierEntered.isCompleted)
        }
    }

    @Test fun `cleanup continues to store after firebase cleanup failure and reports first failure`() = runBlocking {
        val events = mutableListOf<String>()
        NotificationStore.open(config()).use { store ->
            val failure = IllegalStateException("firebase close failed")
            val resources = OwnedNotificationResources(RecordingFirebase(events, failure), store) { events += "store" }
            val thrown = try {
                resources.stopAndJoin()
                error("cleanup must fail")
            } catch (error: IllegalStateException) {
                error
            }
            assertSame(failure, thrown)
            assertEquals(listOf("firebase", "store"), events)
        }
    }

    @Test fun `disabled configuration acquires no notification resource`() {
        var calls = 0
        val disabled = NotificationConfiguration.fromEnvironment(emptyMap(), ServerListenerConfiguration("127.0.0.1", 8080))
        assertNull(acquireNotificationRuntime(disabled, NotificationRuntimeFactories(
            precheck = { calls++ }, openStore = { calls++; error("disabled") },
            createFirebase = { calls++; error("disabled") }, createProvider = { calls++; error("disabled") },
        )))
        assertEquals(0, calls)
    }

    private suspend fun worker(name: String, entered: CompletableDeferred<Unit>, events: MutableList<String>) {
        entered.complete(Unit)
        try { awaitCancellation() } finally { events += name }
    }

    private fun config() = NotificationConfiguration.fromEnvironment(mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true",
        "VLRGG_NOTIFICATIONS_STORAGE_PATH" to Files.createTempDirectory("lifecycle").resolve("store").absolutePathString(),
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1",
        "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ), ServerListenerConfiguration("127.0.0.1", 8080))

    private class RecordingFirebase(private val events: MutableList<String>, private val failure: Throwable? = null) : OwnedFirebaseResource {
        override fun close() { events += "firebase"; failure?.let { throw it } }
    }

    private object AcceptedProvider : NotificationProvider {
        override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType) = ProviderDeliveryResult.Accepted
    }
}

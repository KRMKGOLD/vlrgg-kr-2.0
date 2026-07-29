package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

/** Named-app ownership is explicit: an existing app is never borrowed, renamed, or deleted. */
internal class OwnedFirebaseApp private constructor(val app: FirebaseApp) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) app.delete() }

    companion object {
        fun precheck(configuration: NotificationConfiguration) {
            val name = requireNotNull(configuration.firebaseAppName)
            if (FirebaseApp.getApps().any { it.name == name }) {
                throw NotificationConfigurationException(ConfigurationCategory.FIREBASE_APP_NAME_COLLISION, ConfigurationField.APP_INSTANCE_ID)
            }
        }

        fun create(configuration: NotificationConfiguration): OwnedFirebaseApp {
            val name = requireNotNull(configuration.firebaseAppName)
            synchronized(FirebaseApp::class.java) {
                precheck(configuration)
                val options = FirebaseOptions.builder()
                    .setProjectId(requireNotNull(configuration.firebaseProjectId))
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build()
                return try { OwnedFirebaseApp(FirebaseApp.initializeApp(options, name)) }
                catch (error: IllegalStateException) {
                    throw NotificationConfigurationException(ConfigurationCategory.FIREBASE_APP_NAME_COLLISION, ConfigurationField.APP_INSTANCE_ID)
                }
            }
        }
    }
}

/** A resource created during notification startup. Test doubles never need Firebase globals. */
internal interface OwnedFirebaseResource : AutoCloseable

private class FirebaseAppResource(private val owned: OwnedFirebaseApp) : OwnedFirebaseResource {
    val app: FirebaseApp get() = owned.app
    override fun close() = owned.close()
}

/** Narrow startup seams keep collision and rollback tests entirely offline. */
internal data class NotificationRuntimeFactories(
    val precheck: (NotificationConfiguration) -> Unit = OwnedFirebaseApp::precheck,
    val openStore: (NotificationConfiguration) -> NotificationStore = NotificationStore::open,
    val createFirebase: (NotificationConfiguration) -> OwnedFirebaseResource = { FirebaseAppResource(OwnedFirebaseApp.create(it)) },
    val createProvider: (OwnedFirebaseResource) -> NotificationProvider = { resource ->
        FirebaseNotificationProvider((resource as FirebaseAppResource).app)
    },
    val closeStore: (NotificationStore) -> Unit = NotificationStore::close,
)

internal data class AcquiredNotificationRuntime(
    val store: NotificationStore,
    val provider: NotificationProvider,
    val resources: OwnedNotificationResources,
)

/**
 * Acquires every enabled notification resource in contract order.  The collision preflight is
 * intentionally before opening/migrating the store and before ADC is resolved by Firebase.
 */
internal fun acquireNotificationRuntime(
    configuration: NotificationConfiguration,
    factories: NotificationRuntimeFactories = NotificationRuntimeFactories(),
): AcquiredNotificationRuntime? {
    if (!configuration.enabled) return null
    factories.precheck(configuration)
    val store = factories.openStore(configuration)
    var firebase: OwnedFirebaseResource? = null
    try {
        firebase = factories.createFirebase(configuration)
        val provider = factories.createProvider(firebase)
        return AcquiredNotificationRuntime(
            store = store,
            provider = provider,
            resources = OwnedNotificationResources(firebase, store, factories.closeStore),
        )
    } catch (error: Throwable) {
        OwnedNotificationResources(firebase, store, factories.closeStore).stopBlocking()
        throw error
    }
}

/** The listener runner shares this one failure path with normal application startup. */
internal fun <T> runWithNotificationRuntime(
    configuration: NotificationConfiguration,
    factories: NotificationRuntimeFactories = NotificationRuntimeFactories(),
    runner: (AcquiredNotificationRuntime?) -> T,
): T {
    val runtime = acquireNotificationRuntime(configuration, factories)
    return try {
        runner(runtime)
    } catch (error: Throwable) {
        runtime?.resources?.stopBlocking()
        throw error
    }
}

internal class FixedDelayDeliveryPolling(private val service: NotificationDeliveryService, private val delayMillis: Long) {
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            while (currentCoroutineContext().isActive && service.runOnce()) { }
            delay(delayMillis)
        }
    }
}

/** LIFO owner used by application startup/rollback: jobs finish before app and datasource release. */
internal class OwnedNotificationResources(
    private val firebaseApp: OwnedFirebaseResource?,
    private val store: NotificationStore,
    private val closeStore: (NotificationStore) -> Unit = NotificationStore::close,
) {
    private val lock = Any()
    private val trackingJobs = mutableListOf<Job>()
    private val deliveryJobs = mutableListOf<Job>()
    private var completion: CompletableDeferred<Result<Unit>>? = null
    private var closing = false

    /**
     * The gate owns lazy creation, registration, and start as one atomic operation.  Once stop
     * enters the gate, it creates no new coroutine, so a post-close caller cannot run against a
     * released Firebase app or store.
     */
    fun startTracking(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job? = startWorker(scope, true, block)
    fun startDelivery(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job? = startWorker(scope, false, block)

    private fun startWorker(scope: CoroutineScope, isTracking: Boolean, block: suspend CoroutineScope.() -> Unit): Job? = synchronized(lock) {
        if (closing) return@synchronized null
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        if (isTracking) trackingJobs += job else deliveryJobs += job
        job.start()
        job
    }

    suspend fun stopAndJoin() {
        val (shared, owner) = synchronized(lock) {
            completion?.let { it to false } ?: CompletableDeferred<Result<Unit>>().also {
                // This is the pre-close barrier: startWorker can no longer create or start a job.
                closing = true
                completion = it
            } to true
        }
        if (owner) {
            val outcome = runCatching { cleanup() }
            shared.complete(outcome)
        }
        shared.await().getOrThrow()
    }

    private suspend fun cleanup() {
        var firstFailure: Throwable? = null
        suspend fun capture(block: suspend () -> Unit) {
            try { block() } catch (error: Throwable) { if (firstFailure == null) firstFailure = error }
        }
        val (delivery, tracking) = synchronized(lock) { deliveryJobs.toList() to trackingJobs.toList() }
        delivery.forEach { job -> capture { job.cancelAndJoin() } }
        tracking.forEach { job -> capture { job.cancelAndJoin() } }
        fun closeCapture(block: () -> Unit) {
            try { block() } catch (error: Throwable) { if (firstFailure == null) firstFailure = error }
        }
        closeCapture { firebaseApp?.close() }
        closeCapture { closeStore(store) }
        firstFailure?.let { throw it }
    }

    fun stopBlocking() = runBlocking { stopAndJoin() }
}

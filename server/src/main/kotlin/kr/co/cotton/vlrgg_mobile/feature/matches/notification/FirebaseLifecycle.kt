package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            while (isActive && service.runOnce()) { }
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
    private var tracking: Job? = null
    private var delivery: Job? = null
    private val lateJobs = mutableListOf<Pair<Job, Boolean>>()
    private var completion: CompletableDeferred<Result<Unit>>? = null
    private var closing = false

    fun trackTracking(job: Job) = track(job, true)
    fun trackDelivery(job: Job) = track(job, false)

    private fun track(job: Job, isTracking: Boolean) {
        val cancelAndJoin = synchronized(lock) {
            when {
                completion == null -> {
                    if (isTracking) tracking = job else delivery = job
                    false
                }
                !closing -> {
                    lateJobs += job to isTracking
                    false
                }
                else -> true
            }
        }
        // A registration that arrives after the cleanup barrier is rejected, rather than silently
        // becoming an unowned worker.  It never races an owned Firebase/store close.
        if (cancelAndJoin) runBlocking { job.cancelAndJoin() }
    }

    suspend fun stopAndJoin() {
        val (shared, owner) = synchronized(lock) {
            completion?.let { it to false } ?: CompletableDeferred<Result<Unit>>().also { completion = it } to true
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
        while (true) {
            val jobs = synchronized(lock) {
                val batch = buildList {
                    delivery?.let { add(it to false) }
                    tracking?.let { add(it to true) }
                    addAll(lateJobs)
                }
                delivery = null
                tracking = null
                lateJobs.clear()
                batch
            }
            jobs.filter { !it.second }.forEach { (job, _) -> capture { job.cancelAndJoin() } }
            jobs.filter { it.second }.forEach { (job, _) -> capture { job.cancelAndJoin() } }
            val hasLateJob = synchronized(lock) {
                if (lateJobs.isEmpty()) {
                    // This is the registration barrier. track() cannot offer an owned job after
                    // closing becomes true, and any offer before it was included by the loop.
                    closing = true
                    false
                } else true
            }
            if (!hasLateJob) break
        }
        fun closeCapture(block: () -> Unit) {
            try { block() } catch (error: Throwable) { if (firstFailure == null) firstFailure = error }
        }
        synchronized(lock) {
            // Hold the registration gate through the irreversible resources. A concurrent track()
            // either joined the preceding batch or is rejected only after this owner is complete.
            closeCapture { firebaseApp?.close() }
            closeCapture { closeStore(store) }
        }
        firstFailure?.let { throw it }
    }

    fun stopBlocking() = runBlocking { stopAndJoin() }
}

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
    private val tracking: Job?,
    private val delivery: Job?,
    private val firebaseApp: OwnedFirebaseApp?,
    private val store: NotificationStore,
) {
    private val stopped = AtomicBoolean(false)
    suspend fun stopAndJoin() {
        if (!stopped.compareAndSet(false, true)) return
        delivery?.cancelAndJoin()
        tracking?.cancelAndJoin()
        try { firebaseApp?.close() } finally { store.close() }
    }
}

package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.api.core.ApiFutures
import com.google.api.core.SettableApiFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FirebaseNotificationProviderTest {
    private val target = SendableDeliveryTarget(PushTarget(java.util.UUID.randomUUID()), "offline-target", FirebaseTargetMode.FID)

    @Test fun `actual async adapter maps completed sendAsync future to acceptance offline`() = runBlocking {
        val provider = FirebaseNotificationProvider { ApiFutures.immediateFuture("message-id") }
        assertEquals(ProviderDeliveryResult.Accepted, provider.send(target, NotificationEventType.START))
    }

    @Test fun `actual async adapter timeout does not cancel pending Firebase future and late completion is harmless`() = runBlocking {
        val future = SettableApiFuture.create<String>()
        val entered = CompletableDeferred<Unit>()
        val provider = FirebaseNotificationProvider { entered.complete(Unit); future }
        val timeout = try {
            withTimeout(50) { provider.send(target, NotificationEventType.START) }
            null
        } catch (error: TimeoutCancellationException) {
            error
        }
        assertNotNull(timeout)
        entered.await()
        assertFalse(future.isCancelled)
        future.set("late-message-id")
        assertFalse(future.isCancelled)
    }

    @Test fun `actual async adapter cancellation leaves pending Firebase future for late completion`() = runBlocking {
        val future = SettableApiFuture.create<String>()
        val entered = CompletableDeferred<Unit>()
        val provider = FirebaseNotificationProvider { entered.complete(Unit); future }
        val call = async { provider.send(target, NotificationEventType.END) }
        entered.await()
        call.cancelAndJoin()
        assertFalse(future.isCancelled)
        future.set("late-message-id")
        assertFalse(future.isCancelled)
    }
}

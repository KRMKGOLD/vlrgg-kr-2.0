package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

class NotificationStage11Test {
    @Test fun `secret is one-time opaque verifier material`() {
        val secret = TargetSecrets.generate()
        assertEquals(43, secret.length)
        assertTrue(TargetSecrets.matches(secret, TargetSecrets.hash(secret)))
        assertFalse(TargetSecrets.matches(TargetSecrets.generate(), TargetSecrets.hash(secret)))
    }

    @Test fun `secret verifier includes a fresh salt and fails closed for malformed values`() {
        val secret = TargetSecrets.generate()
        assertNotEquals(TargetSecrets.hash(secret), TargetSecrets.hash(secret))
        assertFalse(TargetSecrets.matches(secret, "v0:bad:bad"))
        assertFalse(TargetSecrets.matches(secret, "v1:not-base64:digest"))
    }

    @Test fun `start intent IDs are deterministic and scoped`() {
        val target = "4c9c4b4b-8278-4d14-9179-152a98a1b4d0"
        assertEquals(deterministicIntentId(target, 99), deterministicIntentId(target, 99))
        assertNotEquals(deterministicIntentId(target, 99), deterministicIntentId(target, 100))
    }

    @Test fun `canonical IDs reject noncanonical target and match inputs`() {
        assertFailsWith<IllegalArgumentException> { deterministicIntentId("4C9C4B4B-8278-4D14-9179-152A98A1B4D0", 1) }
        assertFailsWith<IllegalArgumentException> { deterministicIntentId("4c9c4b4b-8278-4d14-9179-152a98a1b4d0", 0) }
    }

    @Test fun `listener uses PORT then legacy port fallback on the public host`() {
        assertEquals(ServerListenerConfiguration("0.0.0.0", 9090), ServerListenerConfiguration.fromEnvironment(mapOf("PORT" to "9090", "VLRGG_SERVER_PORT" to "8081")))
        assertEquals(ServerListenerConfiguration("0.0.0.0", 8081), ServerListenerConfiguration.fromEnvironment(mapOf("VLRGG_SERVER_PORT" to "8081")))
        assertEquals(ServerListenerConfiguration("0.0.0.0", 8080), ServerListenerConfiguration.fromEnvironment(emptyMap()))
        assertFailsWith<IllegalArgumentException> { ServerListenerConfiguration.fromEnvironment(mapOf("PORT" to "0")) }
    }

    @Test fun `retry policy has bounded rate-limit floor and attempt cap`() {
        assertTrue(DeliveryRetryPolicy.nextDelay("intent", 1, ProviderResult.Retryable(ProviderRetryKind.RATE_LIMITED))!!.seconds >= 60)
        assertEquals(null, DeliveryRetryPolicy.nextDelay("intent", 5, ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE)))
    }

    @Test fun `retry schedule distinguishes unsafe provider hints from exhausted attempts`() {
        assertEquals(
            DeliveryRetryPolicy.Schedule.UnsafeProviderHint,
            DeliveryRetryPolicy.schedule("intent", 1, ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE, 86_400_001)),
        )
        assertEquals(
            DeliveryRetryPolicy.Schedule.RetryExhausted,
            DeliveryRetryPolicy.schedule("intent", 5, ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE)),
        )
    }

    @Test fun `retry jitter is deterministic sha based and safely bounded`() {
        val first = DeliveryRetryPolicy.nextDelay("intent", 2, ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE))!!
        val second = DeliveryRetryPolicy.nextDelay("intent", 2, ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE))!!
        assertEquals(first, second)
        assertTrue(first.toMillis() in 60_000L..65_000L)
        assertEquals(null, DeliveryRetryPolicy.nextDelay("intent", 1, ProviderResult.Retryable(ProviderRetryKind.UNAVAILABLE, 86_400_001)))
    }

    @Test fun `provider exception is ambiguous but fatal error propagates`() {
        val command = NotificationCommand("target", 1, "token", "intent")
        val exceptionProvider = object : NotificationProvider { override suspend fun send(command: NotificationCommand): ProviderResult = throw IllegalStateException("hidden") }
        assertEquals(ProviderResult.Unknown, runBlocking { invokeProvider(exceptionProvider, command) })
        val fatalProvider = object : NotificationProvider { override suspend fun send(command: NotificationCommand): ProviderResult = throw AssertionError("fatal") }
        assertFailsWith<AssertionError> { runBlocking { invokeProvider(fatalProvider, command) } }
    }

    @Test fun `provider cancellation is never converted to a retryable result`() {
        val provider = object : NotificationProvider { override suspend fun send(command: NotificationCommand): ProviderResult = throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { runBlocking { invokeProvider(provider, NotificationCommand("target", 1, "token", "intent")) } }
    }

    @Test fun `transaction failures recursively normalize wrapped domain errors`() {
        val conflict = RevisionConflictException()
        val normalized = normalizeFirestoreTransactionFailure(ExecutionException(CompletionException(ExecutionException(conflict))))
        assertTrue(normalized === conflict)
    }
}

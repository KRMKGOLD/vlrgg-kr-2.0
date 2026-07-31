package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.time.Duration
import java.time.DateTimeException
import java.time.Instant
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

/** Pure retry policy: provider adapters never expose raw HTTP/SDK failures to this boundary. */
object DeliveryRetryPolicy {
    const val MAX_ATTEMPTS = 5
    sealed interface Schedule {
        data class Delayed(val delay: Duration) : Schedule
        data object RetryExhausted : Schedule
        data object UnsafeProviderHint : Schedule
    }

    fun schedule(intentId: String, attempt: Int, result: ProviderResult.Retryable): Schedule {
        require(attempt > 0)
        if (result.hintMillis?.let { it < 0 || it > Duration.ofHours(24).toMillis() } == true) return Schedule.UnsafeProviderHint
        if (attempt >= MAX_ATTEMPTS) return Schedule.RetryExhausted
        val base = (30_000L shl (attempt - 1).coerceAtMost(6)).coerceAtMost(Duration.ofHours(1).toMillis())
        val jitter = deterministicJitter(intentId, attempt)
        val providerFloor = result.hintMillis ?: if (result.kind == ProviderRetryKind.RATE_LIMITED) 60_000L else 0L
        return Schedule.Delayed(Duration.ofMillis(maxOf((base + jitter).coerceAtMost(Duration.ofHours(1).toMillis()), providerFloor)))
    }

    fun nextDelay(intentId: String, attempt: Int, result: ProviderResult.Retryable): Duration? {
        return (schedule(intentId, attempt, result) as? Schedule.Delayed)?.delay
    }

    private fun deterministicJitter(intentId: String, attempt: Int): Long {
        val fields = listOf("vlrgg-retry-jitter-v1", intentId, attempt.toString())
        val encoded = fields.fold(ByteArray(0)) { bytes, field ->
            val value = field.toByteArray(UTF_8)
            bytes + ByteBuffer.allocate(4 + value.size).putInt(value.size).put(value).array()
        }
        return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(encoded), 0, 8).long.toULong().rem(5_001u).toLong()
    }
}

/** Executes exactly one claimed call. Any post-marker ambiguity is terminal UNKNOWN. */
class NotificationDeliveryService(
    private val store: FirestoreNotificationStore,
    private val provider: NotificationProvider,
    private val now: () -> Instant = { Instant.now() },
    private val providerTimeoutMillis: Long = 30_000,
) {
    init { require(providerTimeoutMillis > 0) }

    suspend fun runOnce(): Boolean {
        val claim = store.claimDueDeliveryOnIo() ?: return false
        val call = store.markDeliveryCallStartedOnIo(claim) ?: return true
        val result = try { withTimeout(providerTimeoutMillis) { invokeProvider(provider, call.command) } } catch (_: TimeoutCancellationException) {
            store.finalizeDeliveryOnIo(call, DeliveryState.UNKNOWN, "PROVIDER_TIMEOUT")
            return true
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                store.finalizeDeliveryOnIo(call, DeliveryState.UNKNOWN, "CALL_CANCELLED")
            }
            throw cancelled
        }
        when (result) {
            is ProviderResult.Accepted -> store.finalizeDeliveryOnIo(call, DeliveryState.ACCEPTED)
            ProviderResult.InvalidTarget -> store.finalizeDeliveryOnIo(call, DeliveryState.INVALID_TARGET, "PROVIDER_INVALID_TARGET")
            is ProviderResult.NonRetryable -> store.finalizeDeliveryOnIo(call, DeliveryState.TERMINAL_FAILURE, result.safeCode)
            ProviderResult.Unknown -> store.finalizeDeliveryOnIo(call, DeliveryState.UNKNOWN, "PROVIDER_UNKNOWN")
            is ProviderResult.Retryable -> {
                when (val schedule = DeliveryRetryPolicy.schedule(call.claim.intentId, call.claim.attempt, result)) {
                    DeliveryRetryPolicy.Schedule.RetryExhausted -> store.finalizeDeliveryOnIo(call, DeliveryState.TERMINAL_FAILURE, "RETRY_EXHAUSTED")
                    DeliveryRetryPolicy.Schedule.UnsafeProviderHint -> store.finalizeDeliveryOnIo(call, DeliveryState.TERMINAL_FAILURE, "PROVIDER_RETRY_AFTER_UNSAFE")
                    is DeliveryRetryPolicy.Schedule.Delayed -> {
                        val due = try {
                            now().plus(schedule.delay)
                        } catch (_: ArithmeticException) {
                            null
                        } catch (_: DateTimeException) {
                            null
                        }
                        if (due == null) store.finalizeDeliveryOnIo(call, DeliveryState.TERMINAL_FAILURE, "RETRY_SCHEDULE_OVERFLOW")
                        else store.finalizeDeliveryOnIo(call, DeliveryState.RETRY_WAIT, dueAt = due)
                    }
                }
            }
        }
        return true
    }
}

/** Provider ambiguity is an Exception boundary; fatal JVM Errors deliberately retain normal server semantics. */
internal suspend fun invokeProvider(provider: NotificationProvider, command: NotificationCommand): ProviderResult = try {
    provider.send(command)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ProviderResult.Unknown
}

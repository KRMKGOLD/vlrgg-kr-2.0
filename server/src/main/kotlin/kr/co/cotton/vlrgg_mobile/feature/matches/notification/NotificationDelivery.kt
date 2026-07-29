package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Provider values are internal and deliberately never cross route/OpenAPI boundaries. */
internal sealed interface ProviderDeliveryResult {
    data object Accepted : ProviderDeliveryResult
    data object InvalidTarget : ProviderDeliveryResult
    data class Retryable(val status: Int, val headers: Map<String, Any?> = emptyMap()) : ProviderDeliveryResult
    data class NonRetryable(val category: String = "PROVIDER_NONRETRYABLE") : ProviderDeliveryResult
    data class Unknown(val category: String = "PROVIDER_UNKNOWN") : ProviderDeliveryResult
}

internal interface NotificationProvider {
    suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult
}

/** Real adapter; unit tests inject NotificationProvider and never instantiate/call this transport. */
internal class FirebaseNotificationProvider(private val app: FirebaseApp) : NotificationProvider {
    override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult = try {
        val builder = Message.builder().putData("event", event.name)
        when (target.mode) {
            FirebaseTargetMode.FID -> builder.setFid(target.registrationValue)
            FirebaseTargetMode.LEGACY_TOKEN -> builder.setToken(target.registrationValue)
        }
        FirebaseMessaging.getInstance(app).send(builder.build())
        ProviderDeliveryResult.Accepted
    } catch (error: FirebaseMessagingException) {
        val code = error.messagingErrorCode?.name
        when {
            code in setOf("UNREGISTERED", "INVALID_ARGUMENT") -> ProviderDeliveryResult.InvalidTarget
            error.httpResponse?.statusCode in setOf(429, 503) -> ProviderDeliveryResult.Retryable(error.httpResponse.statusCode, error.httpResponse.headers)
            code in setOf("INTERNAL", "UNAVAILABLE") -> ProviderDeliveryResult.Retryable(error.httpResponse?.statusCode ?: 503, error.httpResponse?.headers ?: emptyMap())
            else -> ProviderDeliveryResult.NonRetryable("FIREBASE_${code ?: "ERROR"}")
        }
    } catch (_: java.io.IOException) {
        ProviderDeliveryResult.Unknown("IO_AMBIGUOUS")
    } catch (_: Exception) {
        ProviderDeliveryResult.Unknown()
    }
}

/** One process owns this guard. A process restart intentionally adds at most one full stored delay. */
internal class RetryMonotonicGuard(private val nanoTime: () -> Long = System::nanoTime) {
    private val deadlines = java.util.concurrent.ConcurrentHashMap<UUID, Long>()
    fun eligible(intent: UUID, delayMillis: Long): Boolean {
        val deadline = deadlines.computeIfAbsent(intent) { nanoTime() + delayMillis * 1_000_000L }
        return nanoTime() >= deadline
    }
}

internal class NotificationDeliveryService(
    private val store: NotificationStore,
    private val provider: NotificationProvider,
    private val configuration: NotificationConfiguration,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun runOnce(): Boolean {
        val now = Instant.now(clock)
        val claim = store.claimDueDelivery(now) ?: return false
        claim.retryDelayMillis?.let { delay ->
            if (!retryGuard.eligible(claim.intentId, delay)) {
                store.releaseDeliveryClaim(claim, now)
                return false
            }
        }
        val call = store.markDeliveryCallStarted(claim, now) ?: return true
        val result = try {
            withTimeout(configuration.deliveryTimeoutMillis) { provider.send(call.target, call.claim.event) }
        } catch (_: TimeoutCancellationException) {
            store.finalizeUnknown(call, "PROVIDER_TIMEOUT", Instant.now(clock)); return true
        } catch (error: CancellationException) {
            store.finalizeUnknown(call, "CALL_CANCELLED", Instant.now(clock)); throw error
        } catch (_: Exception) {
            ProviderDeliveryResult.Unknown("PROVIDER_AMBIGUOUS")
        }
        finalize(call, result, Instant.now(clock))
        return true
    }

    private val retryGuard = RetryMonotonicGuard()

    private fun finalize(call: DeliveryCall, result: ProviderDeliveryResult, now: Instant) = when (result) {
        ProviderDeliveryResult.Accepted -> store.finalizeAccepted(call, now)
        ProviderDeliveryResult.InvalidTarget -> store.invalidateDeliveryTarget(call, now)
        is ProviderDeliveryResult.NonRetryable -> store.finalizeTerminal(call, result.category, now)
        is ProviderDeliveryResult.Unknown -> store.finalizeUnknown(call, result.category, now)
        is ProviderDeliveryResult.Retryable -> scheduleRetry(call, result, now)
    }

    private fun scheduleRetry(call: DeliveryCall, response: ProviderDeliveryResult.Retryable, now: Instant) {
        if (call.claim.attemptCount >= configuration.maxApplicationAttempts) {
            store.finalizeTerminal(call, "RETRY_EXHAUSTED", now); return
        }
        val providerMinimum = retryAfter(response.headers, now)
            ?: if (response.status == 429) 60_000L else 0L
        if (providerMinimum > configuration.providerRetryCeilingMillis) {
            store.finalizeTerminal(call, "PROVIDER_RETRY_AFTER_UNSAFE", now); return
        }
        val exponent = (call.claim.attemptCount - 1).coerceAtMost(62)
        val base = saturatingMultiply(configuration.initialRetryMillis, 1L shl exponent).coerceAtMost(configuration.maxRetryMillis)
        val jitter = deterministicJitter(call.claim, configuration.retryJitterMillis)
        val applicationDelay = (base + jitter).coerceAtMost(configuration.maxRetryMillis)
        val delay = maxOf(applicationDelay, providerMinimum)
        val due = try { now.plusMillis(delay) } catch (_: ArithmeticException) { store.finalizeTerminal(call, "RETRY_SCHEDULE_OVERFLOW", now); return }
        store.finalizeRetry(call, now, delay, due)
    }
}

private fun retryAfter(headers: Map<String, Any?>, now: Instant): Long? {
    val values = headers.entries.filter { it.key.equals("Retry-After", ignoreCase = true) }.map { it.value }
    if (values.size != 1 || values.single() !is String) return null
    val value = (values.single() as String).trim()
    value.toLongOrNull()?.let { return if (it >= 0) Math.multiplyExact(it, 1000L) else null }
    return try { java.time.Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis().takeIf { it > 0 } } catch (_: Exception) { null }
}

private fun deterministicJitter(claim: DeliveryClaim, maximum: Long): Long {
    if (maximum == 0L) return 0
    val bytes = listOf("vlrgg-retry-jitter-v1", claim.intentId.toString(), claim.event.name, claim.attemptCount.toString()).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8)
    val value = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8).long.toULong()
    return (value % (maximum.toULong() + 1u)).toLong()
}

private fun saturatingMultiply(value: Long, multiplier: Long): Long = if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier

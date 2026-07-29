package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.api.core.ApiFuture
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

/** Internal Firebase-only error projection; it never reaches routes, storage, or observability. */
internal data class FirebaseProviderFailure(
    val messagingCode: String?,
    val status: Int?,
    val headers: Map<String, Any?>,
)

/**
 * The production constructor resolves FirebaseMessaging only here; tests inject the async future
 * boundary directly, without FirebaseApp, ADC, or any network transport.
 */
internal class FirebaseNotificationProvider(
    private val sendAsync: (Message) -> ApiFuture<String>,
    private val firebaseFailure: (Throwable) -> FirebaseProviderFailure? = ::firebaseProviderFailure,
) : NotificationProvider {
    constructor(app: FirebaseApp) : this({ message -> FirebaseMessaging.getInstance(app).sendAsync(message) })

    override suspend fun send(target: SendableDeliveryTarget, event: NotificationEventType): ProviderDeliveryResult = try {
        val builder = Message.builder().putData("event", event.name)
        when (target.mode) {
            FirebaseTargetMode.FID -> builder.setFid(target.registrationValue)
            FirebaseTargetMode.LEGACY_TOKEN -> builder.setToken(target.registrationValue)
        }
        sendAsync(builder.build()).awaitWithoutCancellingTransport()
        ProviderDeliveryResult.Accepted
    } catch (error: Exception) {
        val failure = firebaseFailure(error)
        val code = failure?.messagingCode
        when {
            code == "UNREGISTERED" -> ProviderDeliveryResult.InvalidTarget
            failure?.status in setOf(429, 503) -> ProviderDeliveryResult.Retryable(requireNotNull(failure?.status), normalizeRetryAfterHeader(requireNotNull(failure).headers))
            code in setOf("INTERNAL", "UNAVAILABLE") -> ProviderDeliveryResult.Retryable(failure?.status ?: 503, normalizeRetryAfterHeader(failure?.headers ?: emptyMap()))
            error is java.io.IOException -> ProviderDeliveryResult.Unknown("IO_AMBIGUOUS")
            failure != null -> ProviderDeliveryResult.NonRetryable("FIREBASE_${code ?: "ERROR"}")
            else -> ProviderDeliveryResult.Unknown()
        }
    }
}

private fun firebaseProviderFailure(error: Throwable): FirebaseProviderFailure? {
    if (error !is FirebaseMessagingException) return null
    val response = error.httpResponse
    return FirebaseProviderFailure(
        messagingCode = error.messagingErrorCode?.name,
        status = response?.statusCode,
        headers = response?.headers ?: emptyMap(),
    )
}

/**
 * Firebase Admin's Google HttpHeaders stores field values as Object, normally List<String>.
 * Only one case-insensitive field name and exactly one String list element are canonical.
 */
private fun normalizeRetryAfterHeader(headers: Map<String, Any?>): Map<String, Any?> {
    val candidates = headers.entries.filter { it.key.equals("Retry-After", ignoreCase = true) }
    if (candidates.size != 1) return emptyMap()
    val scalar = (candidates.single().value as? List<*>)?.singleOrNull() as? String ?: return emptyMap()
    return mapOf("Retry-After" to scalar)
}

/** Do not cancel the SDK future: a timeout is ambiguous and its late completion is CAS-suppressed. */
private suspend fun <T> ApiFuture<T>.awaitWithoutCancellingTransport(): T = suspendCancellableCoroutine { continuation ->
    addListener(Runnable {
        try { continuation.resume(get()) }
        catch (error: java.util.concurrent.ExecutionException) { continuation.resumeWithException(error.cause ?: error) }
        catch (error: Throwable) { continuation.resumeWithException(error) }
    }, java.util.concurrent.Executor { it.run() })
}

/** One process owns this guard. A process restart intentionally adds at most one full stored delay. */
internal class RetryMonotonicGuard(private val nanoTime: () -> Long = System::nanoTime) {
    private data class Schedule(val intent: UUID, val decisionAt: Instant, val dueAt: Instant)
    private val deadlines = java.util.concurrent.ConcurrentHashMap<Schedule, Long>()
    fun anchor(intent: UUID, decisionAt: Instant, dueAt: Instant, delayMillis: Long) {
        deadlines.computeIfAbsent(Schedule(intent, decisionAt, dueAt)) { nanoTime() + delayMillis * 1_000_000L }
    }
    fun eligible(intent: UUID, decisionAt: Instant, dueAt: Instant, delayMillis: Long): Boolean {
        val deadline = deadlines.computeIfAbsent(Schedule(intent, decisionAt, dueAt)) { nanoTime() + delayMillis * 1_000_000L }
        return nanoTime() >= deadline
    }
}

internal class NotificationDeliveryService(
    private val store: NotificationStore,
    private val provider: NotificationProvider,
    private val configuration: NotificationConfiguration,
    private val clock: Clock = Clock.systemUTC(),
    private val retryGuard: RetryMonotonicGuard = RetryMonotonicGuard(),
) {
    suspend fun runOnce(): Boolean {
        val now = Instant.now(clock)
        val claim = store.claimDueDelivery(now) ?: return false
        claim.retryDelayMillis?.let { delay ->
            if (!retryGuard.eligible(claim.intentId, requireNotNull(claim.retryDecisionAt), requireNotNull(claim.retryDueAt), delay)) {
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
        val parsed = retryAfter(response.headers, now)
        if (parsed == RetryAfter.OVERFLOW) { store.finalizeTerminal(call, "RETRY_SCHEDULE_OVERFLOW", now); return }
        val providerMinimum = (parsed as? RetryAfter.Value)?.millis ?: if (response.status == 429) 60_000L else 0L
        if (providerMinimum > configuration.providerRetryCeilingMillis) {
            store.finalizeTerminal(call, "PROVIDER_RETRY_AFTER_UNSAFE", now); return
        }
        val exponent = (call.claim.attemptCount - 1).coerceAtMost(62)
        val base = saturatingMultiply(configuration.initialRetryMillis, 1L shl exponent).coerceAtMost(configuration.maxRetryMillis)
        val jitter = deterministicJitter(call.claim, configuration.retryJitterMillis)
        val applicationDelay = (base + jitter).coerceAtMost(configuration.maxRetryMillis)
        val delay = maxOf(applicationDelay, providerMinimum)
        val due = try {
            now.plusMillis(delay)
        } catch (_: ArithmeticException) {
            store.finalizeTerminal(call, "RETRY_SCHEDULE_OVERFLOW", now); return
        } catch (_: java.time.DateTimeException) {
            store.finalizeTerminal(call, "RETRY_SCHEDULE_OVERFLOW", now); return
        }
        if (store.finalizeRetry(call, now, delay, due)) retryGuard.anchor(call.claim.intentId, now, due, delay)
    }
}

private sealed interface RetryAfter { data class Value(val millis: Long) : RetryAfter; data object INVALID : RetryAfter; data object OVERFLOW : RetryAfter }
private fun retryAfter(headers: Map<String, Any?>, now: Instant): RetryAfter {
    val values = headers.entries.filter { it.key.equals("Retry-After", ignoreCase = true) }.map { it.value }
    if (values.size != 1 || values.single() !is String) return RetryAfter.INVALID
    val value = (values.single() as String).trimAsciiHttpWhitespace()
    if (value.all { it in '0'..'9' } && value.isNotEmpty()) return try {
        RetryAfter.Value(Math.multiplyExact(value.toLong(), 1_000L))
    } catch (_: NumberFormatException) {
        RetryAfter.OVERFLOW
    } catch (_: ArithmeticException) {
        RetryAfter.OVERFLOW
    }
    return try {
        val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        java.time.Duration.between(now, retryAt).toMillis().takeIf { it > 0 }?.let(RetryAfter::Value) ?: RetryAfter.INVALID
    } catch (_: ArithmeticException) {
        RetryAfter.OVERFLOW
    } catch (_: java.time.DateTimeException) {
        RetryAfter.INVALID
    }
}

/** HTTP field-value OWS is ASCII SP / HTAB only; Unicode whitespace is not silently accepted. */
private fun String.trimAsciiHttpWhitespace(): String {
    var first = 0
    var last = length
    while (first < last && (this[first] == ' ' || this[first] == '\t')) first++
    while (last > first && (this[last - 1] == ' ' || this[last - 1] == '\t')) last--
    return substring(first, last)
}

private fun deterministicJitter(claim: DeliveryClaim, maximum: Long): Long {
    if (maximum == 0L) return 0
    val bytes = listOf("vlrgg-retry-jitter-v1", claim.intentId.toString(), claim.event.name, claim.attemptCount.toString()).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8)
    val value = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8).long.toULong()
    return (value % (maximum.toULong() + 1u)).toLong()
}

private fun saturatingMultiply(value: Long, multiplier: Long): Long = if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier

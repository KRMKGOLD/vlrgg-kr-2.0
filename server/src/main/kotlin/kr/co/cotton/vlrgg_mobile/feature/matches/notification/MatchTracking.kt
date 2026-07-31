package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Scheduler-owned observation seam. It has no process loop or background job. */
interface MatchObservationProvider { suspend fun observe(matchId: Long): ObservationStatus? }

data class NotificationSchedulerPolicy(
    val deadlineSeconds: Long = 500, val activeMatchLimit: Int = 100, val fanoutBatchSize: Int = 100,
    val deliveryBatchSize: Int = 500, val leaseSeconds: Long = 550, val clockSkewSeconds: Long = 5,
)

data class SchedulerResult(val leaseAcquired: Boolean, val matchesScanned: Int, val deadlineReached: Boolean)

/** One bounded invocation. Scheduler/OIDC ownership is intentionally outside this use case. */
class NotificationSchedulerUseCase(
    private val store: FirestoreNotificationStore, private val observations: MatchObservationProvider,
    private val policy: NotificationSchedulerPolicy = NotificationSchedulerPolicy(),
    private val delivery: NotificationDeliveryService? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun run(scheduleSlot: String, requestOwnerId: String): SchedulerResult {
        require(canonicalScheduleSlot(scheduleSlot))
        require(requestOwnerId.isNotBlank())
        if (!store.acquirePollLease(scheduleSlot, requestOwnerId, Duration.ofSeconds(policy.leaseSeconds))) return SchedulerResult(false, 0, false)
        val deadline = Instant.now(clock).plusSeconds(policy.deadlineSeconds)
        fun batchMayStart() = Instant.now(clock).plusSeconds(10) <= deadline
        while (batchMayStart()) {
            val progressed = store.pendingFanoutMatchIds().any { store.resumeStartFanout(it, policy.fanoutBatchSize) }
            if (!progressed) break
        }
        var scanned = 0
        for (id in store.dueActiveMatchIds(policy.activeMatchLimit)) {
            if (Instant.now(clock) >= deadline) return SchedulerResult(true, scanned, true)
            val status = try {
                observations.observe(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed observation still consumes this Match's cadence and cannot block others.
                null
            }
            store.recordObservation(id, status)
            if (status != null) scanned++
        }
        while (batchMayStart()) {
            val progressed = store.pendingFanoutMatchIds().any { store.resumeStartFanout(it, policy.fanoutBatchSize) }
            if (!progressed) break
        }
        if (delivery != null) for (unused in 0 until policy.deliveryBatchSize) {
            if (Instant.now(clock) >= deadline || !delivery.runOnce()) break
        }
        return SchedulerResult(true, scanned, Instant.now(clock) >= deadline)
    }

    private fun canonicalScheduleSlot(value: String): Boolean = runCatching {
        if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}Z$").matches(value)) return false
        Instant.parse(value.dropLast(1) + ":00Z"); true
    }.getOrDefault(false)
}

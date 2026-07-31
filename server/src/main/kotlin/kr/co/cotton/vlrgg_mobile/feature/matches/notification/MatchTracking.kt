package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Scheduler-owned observation seam. It has no process loop or background job. */
interface MatchObservationProvider { suspend fun observe(matchId: Long): ObservationStatus? }

data class NotificationSchedulerPolicy(
    val deadlineSeconds: Long = 500, val activeMatchLimit: Int = 100, val fanoutBatchSize: Int = 100,
    val deliveryBatchSize: Int = 500, val leaseSeconds: Long = 550, val fanoutReserveSeconds: Long = 10,
    val clockSkewSeconds: Long = 5,
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
        if (!store.acquirePollLeaseOnIo(scheduleSlot, requestOwnerId, Duration.ofSeconds(policy.leaseSeconds))) return SchedulerResult(false, 0, false)
        val deadline = Instant.now(clock).plusSeconds(policy.deadlineSeconds)
        fun batchMayStart() = Instant.now(clock).plusSeconds(policy.fanoutReserveSeconds) <= deadline
        drainPendingFanout(::batchMayStart)
        var scanned = 0
        for (id in store.dueActiveMatchIdsOnIo(policy.activeMatchLimit)) {
            if (Instant.now(clock) >= deadline) return SchedulerResult(true, scanned, true)
            val status = try {
                observations.observe(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed observation still consumes this Match's cadence and cannot block others.
                null
            }
            store.recordObservationOnIo(id, status)
            if (status != null) scanned++
        }
        drainPendingFanout(::batchMayStart)
        if (delivery != null) for (unused in 0 until policy.deliveryBatchSize) {
            if (Instant.now(clock) >= deadline || !delivery.runOnce()) break
        }
        return SchedulerResult(true, scanned, Instant.now(clock) >= deadline)
    }

    private suspend fun drainPendingFanout(batchMayStart: () -> Boolean) {
        // A fan-out job persists its cursor, so this bounded drain intentionally iterates until a pass makes no progress.
        while (batchMayStart()) {
            val pending = store.pendingFanoutMatchIdsOnIo()
            var progressed = false
            pending.forEach { matchId ->
                if (store.resumeStartFanoutOnIo(matchId, policy.fanoutBatchSize)) progressed = true
            }
            if (!progressed) return
        }
    }

    private fun canonicalScheduleSlot(value: String): Boolean = runCatching {
        if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}Z$").matches(value)) return false
        Instant.parse(value.dropLast(1) + ":00Z"); true
    }.getOrDefault(false)
}

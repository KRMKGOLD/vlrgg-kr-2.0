package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.feature.matches.MatchesService

/** The scraper-facing seam keeps network, parse and missing observations distinct. */
internal interface MatchObservationProvider {
    suspend fun observe(matchId: Long): MatchObservation
}

internal sealed interface MatchObservation {
    data class Success(val status: ObservationStatus) : MatchObservation
    data object NetworkFailure : MatchObservation
    data object ParsingFailure : MatchObservation
    data object Missing : MatchObservation
}

internal class MatchesServiceObservationProvider(private val matchesService: MatchesService) : MatchObservationProvider {
    override suspend fun observe(matchId: Long): MatchObservation = try {
        when (matchesService.getMatch(matchId.toString()).status) {
            MatchStatus.UPCOMING -> MatchObservation.Success(ObservationStatus.UPCOMING)
            MatchStatus.LIVE -> MatchObservation.Success(ObservationStatus.LIVE)
            MatchStatus.COMPLETED -> MatchObservation.Success(ObservationStatus.COMPLETED)
            MatchStatus.POSTPONED -> MatchObservation.Success(ObservationStatus.POSTPONED)
            MatchStatus.CANCELLED -> MatchObservation.Success(ObservationStatus.CANCELLED)
            MatchStatus.UNAVAILABLE -> MatchObservation.Missing
        }
    } catch (_: UpstreamNetworkFailure) {
        MatchObservation.NetworkFailure
    } catch (_: SourceParsingFailure) {
        MatchObservation.ParsingFailure
    }
}

/** One cycle snapshots unique active IDs and never lets one upstream failure stop another Match. */
internal class MatchTracker(
    private val store: NotificationStore,
    private val provider: MatchObservationProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logUnexpectedObservation: (Long) -> Unit = { matchId -> logger.warn("notification_tracking_failure matchId={} category=UNEXPECTED_OBSERVATION_FAILURE", matchId) },
) {
    suspend fun runCycle() {
        withContext(ioDispatcher) { store.activeMatchIds() }.forEach { matchId ->
            val result = try { provider.observe(matchId) } catch (error: CancellationException) { throw error } catch (_: Exception) {
                logUnexpectedObservation(matchId)
                null
            }
            if (result == null) return@forEach
            when (result) {
                is MatchObservation.Success -> withContext(ioDispatcher) { store.recordObservation(matchId, ObservationResult.SUCCESS, result.status, Instant.now(clock)) }
                MatchObservation.NetworkFailure -> withContext(ioDispatcher) { store.recordObservation(matchId, ObservationResult.NETWORK_FAILURE, now = Instant.now(clock)) }
                MatchObservation.ParsingFailure -> withContext(ioDispatcher) { store.recordObservation(matchId, ObservationResult.PARSING_FAILURE, now = Instant.now(clock)) }
                MatchObservation.Missing -> withContext(ioDispatcher) { store.recordObservation(matchId, ObservationResult.MISSING, now = Instant.now(clock)) }
            }
        }
    }
}

/** Owns a single tracker job: close happens only after cancellation has completed. */
internal class OwnedTrackingJob(
    private val job: Job,
    private val closeStore: () -> Unit,
) {
    private val closed = AtomicBoolean(false)

    fun stopWithoutBlockingLifecycleThread() {
        job.invokeOnCompletion { closeOnce() }
        job.cancel()
    }

    suspend fun stopAndJoin() {
        job.cancelAndJoin()
        closeOnce()
    }

    private fun closeOnce() {
        if (closed.compareAndSet(false, true)) closeStore()
    }
}

/** Fixed delay (not fixed rate): the next cycle starts only after the previous cycle completes. */
internal class FixedDelayMatchPolling(
    private val runCycle: suspend () -> Unit,
    private val delayMillis: Long,
    private val logCycleFailure: () -> Unit = { logger.warn("notification_tracking_failure category=CYCLE_FAILURE") },
) {
    constructor(tracker: MatchTracker, delayMillis: Long) : this(tracker::runCycle, delayMillis)

    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try { runCycle() } catch (error: CancellationException) { throw error } catch (_: Exception) { logCycleFailure() }
            delay(delayMillis)
        }
    }
}

private val logger = LoggerFactory.getLogger("MatchTracking")

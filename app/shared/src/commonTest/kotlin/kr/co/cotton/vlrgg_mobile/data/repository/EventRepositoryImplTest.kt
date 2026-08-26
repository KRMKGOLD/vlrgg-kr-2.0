package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventMatchesResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventNewsListResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsAvailabilityDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatsResponseDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class EventRepositoryImplTest {

    @Test
    fun getEventsReturnsMappedSuccess() = runTest {
        val repository = EventRepositoryImpl(
            FakeRemoteEventDataSource { eventListDto() },
        )

        val result = repository.getEvents()

        assertEquals(AppResult.Success(EventList(emptyList(), emptyList(), emptyList())), result)
    }

    @Test
    fun failureIsConvertedToAppFailure() = runTest {
        val repository = EventRepositoryImpl(
            FakeRemoteEventDataSource { throw IllegalStateException("events failure") },
        )

        assertSame(AppResult.Failure, repository.getEvents())
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = EventRepositoryImpl(
            FakeRemoteEventDataSource { throw cancellation },
        )

        val thrown = assertFailsWith<CancellationException> {
            repository.getEvents()
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun fourDetailBoundariesReturnMappedSuccessIndependently() = runTest {
        val repository = EventRepositoryImpl(DetailRemoteEventDataSource())

        assertEquals("100", (repository.getEventDetail("100") as AppResult.Success).data.id)
        assertEquals(emptyList(), (repository.getEventMatches("100") as AppResult.Success).data)
        assertEquals("101", (repository.getEventNews("100") as AppResult.Success).data.single().articleId)
        assertEquals(
            EventStatsAvailabilityDto.NOT_AVAILABLE.name,
            (repository.getEventStats("100") as AppResult.Success).data.availability.name,
        )
    }

    @Test
    fun detailBoundaryFailuresBecomeAppFailureWithoutAffectingOtherCalls() = runTest {
        val repository = EventRepositoryImpl(
            DetailRemoteEventDataSource(failingOperation = "news"),
        )

        assertSame(AppResult.Failure, repository.getEventNews("100"))
        assertEquals(emptyList(), (repository.getEventMatches("100") as AppResult.Success).data)
    }

    @Test
    fun cancellationFromEachDetailBoundaryIsRethrown() = runTest {
        listOf("identity", "matches", "news", "stats").forEach { operation ->
            val repository = EventRepositoryImpl(
                DetailRemoteEventDataSource(cancellingOperation = operation),
            )

            assertFailsWith<CancellationException> {
                when (operation) {
                    "identity" -> repository.getEventDetail("100")
                    "matches" -> repository.getEventMatches("100")
                    "news" -> repository.getEventNews("100")
                    "stats" -> repository.getEventStats("100")
                }
            }
        }
    }

    private fun eventListDto() = EventListResponseDto(
        ongoing = emptyList(),
        upcoming = emptyList(),
        completedOrPaused = emptyList(),
    )
}

private class DetailRemoteEventDataSource(
    private val failingOperation: String? = null,
    private val cancellingOperation: String? = null,
) : RemoteEventDataSource {
    override suspend fun getEvents() = error("Event list is not used")

    override suspend fun getEventDetail(eventId: String): EventDetailResponseDto {
        before("identity")
        return EventDetailResponseDto(
            id = eventId,
            name = "Masters Seoul",
            status = null,
            dateLabel = null,
            location = null,
            series = null,
            description = null,
            imageUrl = null,
        )
    }

    override suspend fun getEventMatches(eventId: String): EventMatchesResponseDto {
        before("matches")
        return EventMatchesResponseDto(emptyList())
    }

    override suspend fun getEventNews(eventId: String): EventNewsListResponseDto {
        before("news")
        return EventNewsListResponseDto(
            listOf(EventNewsDto("101/masters-seoul", "Title", null, "2026-08-25")),
        )
    }

    override suspend fun getEventStats(eventId: String): EventStatsResponseDto {
        before("stats")
        return EventStatsResponseDto(EventStatsAvailabilityDto.NOT_AVAILABLE, emptyList())
    }

    private fun before(operation: String) {
        if (operation == cancellingOperation) throw CancellationException("cancelled $operation")
        if (operation == failingOperation) throw IllegalStateException("failed $operation")
    }
}

private class FakeRemoteEventDataSource(
    private val handler: suspend () -> EventListResponseDto,
) : RemoteEventDataSource {
    override suspend fun getEvents(): EventListResponseDto = handler()

    override suspend fun getEventDetail(eventId: String) = error("Event detail is not used")

    override suspend fun getEventMatches(eventId: String) = error("Event matches are not used")

    override suspend fun getEventNews(eventId: String) = error("Event news is not used")

    override suspend fun getEventStats(eventId: String) = error("Event stats are not used")
}

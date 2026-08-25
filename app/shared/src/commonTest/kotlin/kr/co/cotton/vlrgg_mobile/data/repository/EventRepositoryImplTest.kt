package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventListResponseDto
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

    private fun eventListDto() = EventListResponseDto(
        ongoing = emptyList(),
        upcoming = emptyList(),
        completedOrPaused = emptyList(),
    )
}

private class FakeRemoteEventDataSource(
    private val handler: suspend () -> EventListResponseDto,
) : RemoteEventDataSource {
    override suspend fun getEvents(): EventListResponseDto = handler()
}

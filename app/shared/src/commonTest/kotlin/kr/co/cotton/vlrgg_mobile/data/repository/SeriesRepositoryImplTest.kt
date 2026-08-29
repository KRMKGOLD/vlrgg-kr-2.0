package kr.co.cotton.vlrgg_mobile.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSeriesDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventStatusDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.events.EventSummaryDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.series.SeriesDetailResponseDto
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SeriesRepositoryImplTest {

    @Test
    fun successMapsTheServerContractWithoutChangingEventOrder() = runTest {
        val remote = FakeRemoteSeriesDataSource { response() }
        val result = SeriesRepositoryImpl(remote).getSeriesDetail("2")

        assertEquals("2", remote.requestedSeriesId)
        val series = (result as AppResult.Success).data
        assertEquals(listOf("u2", "u1"), series.upcomingEvents.map { it.id })
        assertEquals(listOf("c1"), series.completedEvents.map { it.id })
    }

    @Test
    fun nonCancellationFailureIsTheSafeAppFailure() = runTest {
        val result = SeriesRepositoryImpl(FakeRemoteSeriesDataSource { error("network") }).getSeriesDetail("2")

        assertSame(AppResult.Failure, result)
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = SeriesRepositoryImpl(FakeRemoteSeriesDataSource { throw cancellation })

        val thrown = assertFailsWith<CancellationException> {
            repository.getSeriesDetail("2")
        }

        assertSame(cancellation, thrown)
    }

    private fun response() = SeriesDetailResponseDto(
        id = "2",
        name = "Champions Tour",
        description = null,
        upcomingEvents = listOf(event("u2"), event("u1")),
        completedEvents = listOf(event("c1")),
    )

    private fun event(id: String) = EventSummaryDto(id, id, EventStatusDto.UPCOMING, null, null, null)
}

private class FakeRemoteSeriesDataSource(
    private val response: suspend () -> SeriesDetailResponseDto,
) : RemoteSeriesDataSource {
    var requestedSeriesId: String? = null

    override suspend fun getSeriesDetail(seriesId: String): SeriesDetailResponseDto {
        requestedSeriesId = seriesId
        return response()
    }
}

package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.SeriesRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    @Test
    fun initialRequestLoadsOnceAndContentKeepsEmptySectionsAsContent() = runViewModelTest {
        val series = detail(upcomingEvents = emptyList(), completedEvents = listOf(event("completed")))
        val repository = FakeSeriesRepository(listOf(AppResult.Success(series)))
        val viewModel = SeriesDetailViewModel(repository, SERIES_ID)

        assertEquals(SeriesDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf(SERIES_ID), repository.requestedSeriesIds)
        assertEquals(
            SeriesDetailUiState(SeriesDetailContentState.Content(series)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun successWithBothEmptySectionsRemainsContentForUiDerivation() = runViewModelTest {
        val series = detail(upcomingEvents = emptyList(), completedEvents = emptyList())
        val viewModel = SeriesDetailViewModel(
            FakeSeriesRepository(listOf(AppResult.Success(series))),
            SERIES_ID,
        )

        advanceUntilIdle()

        assertEquals(
            SeriesDetailUiState(SeriesDetailContentState.Content(series)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryOnlyRunsFromErrorAndUsesSameSeriesId() = runViewModelTest {
        val repository = FakeSeriesRepository(listOf(AppResult.Failure, AppResult.Success(detail())))
        val viewModel = SeriesDetailViewModel(repository, SERIES_ID)

        advanceUntilIdle()
        assertEquals(
            SeriesDetailUiState(SeriesDetailContentState.Error),
            viewModel.uiState.value,
        )

        viewModel.retry()
        viewModel.retry()
        assertEquals(SeriesDetailUiState(), viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf(SERIES_ID, SERIES_ID), repository.requestedSeriesIds)
        assertEquals(
            SeriesDetailUiState(SeriesDetailContentState.Content(detail())),
            viewModel.uiState.value,
        )
    }

    private fun detail(
        upcomingEvents: List<EventSummary> = listOf(event("upcoming")),
        completedEvents: List<EventSummary> = emptyList(),
    ) = SeriesDetail(SERIES_ID, "Champions Tour", null, upcomingEvents, completedEvents)

    private fun event(id: String) = EventSummary(id, "Event $id", EventStatus.UPCOMING)

    private fun runViewModelTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeSeriesRepository(
        private val responses: List<AppResult<SeriesDetail>>,
    ) : SeriesRepository {
        val requestedSeriesIds = mutableListOf<String>()

        override suspend fun getSeriesDetail(seriesId: String): AppResult<SeriesDetail> {
            requestedSeriesIds += seriesId
            return responses[requestedSeriesIds.lastIndex]
        }
    }

    private companion object {
        const val SERIES_ID = "2"
    }
}

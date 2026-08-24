package kr.co.cotton.vlrgg_mobile.ui.feature.events

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {

    @Test
    fun initialSuccessKeepsEachStatusGroupIncludingEmptyGroups() = runViewModelTest {
        val ongoing = event(id = "ongoing", status = EventStatus.ONGOING)
        val completed = event(id = "completed", status = EventStatus.COMPLETED)
        val eventList = EventList(
            ongoing = listOf(ongoing),
            upcoming = emptyList(),
            completedOrPaused = listOf(completed),
        )
        val repository = FakeEventRepository(results = listOf(AppResult.Success(eventList)))

        val viewModel = EventsViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.requestCount)
        assertEquals(
            EventsUiState(EventsContentState.Content(eventList)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initialEmptyListExposesWholeListEmpty() = runViewModelTest {
        val repository = FakeEventRepository(results = listOf(AppResult.Success(emptyEventList())))

        val viewModel = EventsViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            EventsUiState(EventsContentState.Empty),
            viewModel.uiState.value,
        )
    }

    @Test
    fun initialFailureExposesErrorAndRetryRequestsAgain() = runViewModelTest {
        val eventList = EventList(ongoing = listOf(event()), upcoming = emptyList(), completedOrPaused = emptyList())
        val repository = FakeEventRepository(
            results = listOf(AppResult.Failure, AppResult.Success(eventList)),
        )
        val viewModel = EventsViewModel(repository)
        advanceUntilIdle()

        assertEquals(EventsUiState(EventsContentState.Error), viewModel.uiState.value)

        viewModel.retry()
        assertEquals(EventsUiState(), viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(2, repository.requestCount)
        assertEquals(EventsUiState(EventsContentState.Content(eventList)), viewModel.uiState.value)
    }

    @Test
    fun retryOutsideErrorDoesNotRequestAgain() = runViewModelTest {
        val repository = FakeEventRepository(
            results = listOf(AppResult.Success(EventList(listOf(event()), emptyList(), emptyList()))),
        )
        val viewModel = EventsViewModel(repository)
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, repository.requestCount)
    }

    @Test
    fun refreshReplacesContentAndStopsRefreshing() = runViewModelTest {
        val initial = EventList(listOf(event(id = "initial")), emptyList(), emptyList())
        val refreshed = EventList(emptyList(), listOf(event(id = "upcoming", status = EventStatus.UPCOMING)), emptyList())
        val repository = FakeEventRepository(
            results = listOf(AppResult.Success(initial), AppResult.Success(refreshed)),
        )
        val viewModel = EventsViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()

        assertEquals(
            EventsUiState(
                contentState = EventsContentState.Loading,
                isRefreshing = true,
            ),
            viewModel.uiState.value,
        )
        advanceUntilIdle()

        assertEquals(2, repository.requestCount)
        assertEquals(EventsUiState(EventsContentState.Content(refreshed)), viewModel.uiState.value)
    }

    @Test
    fun concurrentRefreshRequestsRepositoryOnlyOnce() = runViewModelTest {
        val initial = EventList(listOf(event(id = "initial")), emptyList(), emptyList())
        val pendingRefresh = CompletableDeferred<AppResult<EventList>>()
        val repository = FakeEventRepository { callIndex ->
            when (callIndex) {
                0 -> AppResult.Success(initial)
                1 -> pendingRefresh.await()
                else -> error("Unexpected event list request")
            }
        }
        val viewModel = EventsViewModel(repository)
        advanceUntilIdle()

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(2, repository.requestCount)
        assertTrue(viewModel.uiState.value.isRefreshing)

        pendingRefresh.complete(AppResult.Success(emptyEventList()))
        advanceUntilIdle()
        assertEquals(EventsUiState(EventsContentState.Empty), viewModel.uiState.value)
    }

    private fun emptyEventList() = EventList(
        ongoing = emptyList(),
        upcoming = emptyList(),
        completedOrPaused = emptyList(),
    )

    private fun event(
        id: String = "event-id",
        status: EventStatus = EventStatus.ONGOING,
    ) = EventSummary(
        id = id,
        name = "Event $id",
        status = status,
        dateLabel = null,
        regionCode = null,
        imageUrl = null,
    )

    private fun runViewModelTest(
        testBody: suspend TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeEventRepository(
        private val resultProvider: suspend (callIndex: Int) -> AppResult<EventList>,
    ) : EventRepository {

        constructor(results: List<AppResult<EventList>>) : this(
            resultProvider = { callIndex ->
                check(callIndex in results.indices) { "No result prepared for event request" }
                results[callIndex]
            },
        )

        var requestCount = 0
            private set

        override suspend fun getEvents(): AppResult<EventList> {
            val callIndex = requestCount++
            return resultProvider(callIndex)
        }
    }
}

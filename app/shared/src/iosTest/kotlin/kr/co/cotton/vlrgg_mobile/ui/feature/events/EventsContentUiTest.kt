package kr.co.cotton.vlrgg_mobile.ui.feature.events

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventList
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EventsContentUiTest {

    @Test
    fun loadingShowsSectionSkeletonAndSearchInvokesCallback() = runComposeUiTest {
        var searchClicks = 0
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(),
                onSearch = { searchClicks += 1 },
            )
        }

        val loadingNode = onNodeWithTag(EVENTS_LOADING_TAG).assertIsDisplayed().fetchSemanticsNode()
        assertEquals("이벤트를 불러오는 중", loadingNode.config[SemanticsProperties.StateDescription])
        val ongoingTop = onNodeWithText("Ongoing").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val upcomingTop = onNodeWithText("Upcoming").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val completedTop = onNodeWithText("Completed / Paused")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(ongoingTop < upcomingTop)
        assertTrue(upcomingTop < completedTop)
        onNodeWithContentDescription("검색").performClick()
        assertEquals(1, searchClicks)
    }

    @Test
    fun wholeEmptyShowsCenteredEmptyMessage() = runComposeUiTest {
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(contentState = EventsContentState.Empty),
            )
        }

        onNodeWithText("표시할 이벤트가 없어요.").assertIsDisplayed()
        onNodeWithText("현재 이벤트가 없어요.").assertDoesNotExist()
    }

    @Test
    fun initialErrorShowsGuidanceAndRetryInvokesCallback() = runComposeUiTest {
        var retries = 0
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(contentState = EventsContentState.Error),
                onRetry = { retries += 1 },
            )
        }

        onNodeWithText("이벤트를 불러오지 못했습니다.").assertIsDisplayed()
        onNodeWithText("네트워크 상태를 확인하고 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithTag(EVENTS_INITIAL_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun contentShowsOrderedSectionsPartialEmptyStatusesAndOnlyUpstreamMetadata() = runComposeUiTest {
        setContent {
            EventsContentFixture(uiState = populatedState)
        }

        val ongoingTop = onNodeWithText("Ongoing").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val upcomingTop = onNodeWithText("Upcoming").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val completedTop = onNodeWithText("Completed / Paused")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(ongoingTop < upcomingTop)
        assertTrue(upcomingTop < completedTop)
        onAllNodesWithText("현재 이벤트가 없어요.").assertCountEquals(1)

        onNodeWithText(longEventName).assertIsDisplayed()
        onNodeWithText("AUG 20 - SEP 4 · KR").assertIsDisplayed()
        onNodeWithText("AUG 18").assertIsDisplayed()
        onNodeWithText("PACIFIC").assertIsDisplayed()
        onNodeWithText("날짜 미정").assertDoesNotExist()
        onNodeWithText("지역 미정").assertDoesNotExist()

        onNodeWithText("진행 중").assertIsDisplayed()
        onNodeWithText("종료").assertIsDisplayed()
        onNodeWithText("중단").assertIsDisplayed()
    }

    @Test
    fun eventRowClickEmitsStableEventId() = runComposeUiTest {
        var clickedEventId: String? = null
        setContent {
            EventsContentFixture(
                uiState = populatedState,
                onEventClick = { clickedEventId = it },
            )
        }

        onNodeWithTag(eventRowTag(ongoingEvent.id)).performClick()
        assertEquals(ongoingEvent.id, clickedEventId)
    }

    @Test
    fun eventWithoutOptionalMetadataShowsNoSynthesizedValues() = runComposeUiTest {
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(
                    contentState = EventsContentState.Content(
                        EventList(
                            ongoing = emptyList(),
                            upcoming = listOf(upcomingEventWithoutMetadata),
                            completedOrPaused = emptyList(),
                        ),
                    ),
                ),
            )
        }

        onNodeWithText(upcomingEventWithoutMetadata.name).assertIsDisplayed()
        onNodeWithText("예정").assertIsDisplayed()
        onNodeWithText("날짜 미정").assertDoesNotExist()
        onNodeWithText("지역 미정").assertDoesNotExist()
    }

    @Test
    fun activeRefreshShowsSkeletonAndRefreshProgressState() = runComposeUiTest {
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(
                    contentState = EventsContentState.Loading,
                    isRefreshing = true,
                ),
            )
        }

        onNodeWithTag(EVENTS_LOADING_TAG).assertExists()
        onNodeWithTag(EVENTS_REFRESHING_TAG).assertExists()
        onNodeWithText(longEventName).assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun EventsContentFixture(
        uiState: EventsUiState,
        onSearch: () -> Unit = {},
        onEventClick: (String) -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        VlrTheme {
            EventsContent(
                uiState = uiState,
                listState = rememberLazyListState(),
                onSearch = onSearch,
                onEventClick = onEventClick,
                onRefresh = onRefresh,
                onRetry = onRetry,
            )
        }
    }

    private companion object {
        const val longEventName =
            "Valorant Champions Tour 2026 Pacific Last Chance Qualifier Presented by Example"

        val ongoingEvent = EventSummary(
            id = "event-ongoing-101",
            name = longEventName,
            status = EventStatus.ONGOING,
            dateLabel = "AUG 20 - SEP 4",
            regionCode = "KR",
            imageUrl = "https://example.com/event.png",
        )
        val completedEvent = EventSummary(
            id = "event-completed-202",
            name = "Masters Seoul",
            status = EventStatus.COMPLETED,
            dateLabel = "AUG 18",
        )
        val pausedEvent = EventSummary(
            id = "event-paused-303",
            name = "Pacific Open",
            status = EventStatus.PAUSED,
            regionCode = "PACIFIC",
        )
        val upcomingEventWithoutMetadata = EventSummary(
            id = "event-upcoming-404",
            name = "Game Changers East Asia",
            status = EventStatus.UPCOMING,
        )
        val populatedState = EventsUiState(
            contentState = EventsContentState.Content(
                EventList(
                    ongoing = listOf(ongoingEvent),
                    upcoming = emptyList(),
                    completedOrPaused = listOf(completedEvent, pausedEvent),
                ),
            ),
        )
    }
}

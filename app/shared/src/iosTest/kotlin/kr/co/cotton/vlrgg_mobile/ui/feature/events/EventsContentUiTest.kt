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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventMonthDay
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EventsContentUiTest {

    @Test
    fun loadingShowsRowSkeletonWithProgressAndSearchInvokesCallback() = runComposeUiTest {
        var searchClicks = 0
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(),
                onSearch = { searchClicks += 1 },
            )
        }

        // `.pen` 40-2: row skeletons only, without group headers or a spinner.
        val loadingNode = onNodeWithTag(EVENTS_LOADING_TAG).assertIsDisplayed().fetchSemanticsNode()
        assertEquals("이벤트를 불러오는 중", loadingNode.config[SemanticsProperties.StateDescription])
        assertEquals(ProgressBarRangeInfo.Indeterminate, loadingNode.config[SemanticsProperties.ProgressBarRangeInfo])
        listOf("진행 중", "예정", "종료 · 중단").forEach { onNodeWithText(it).assertDoesNotExist() }
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

        val ongoingTop = onNodeWithText("진행 중").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val upcomingTop = onNodeWithText("예정").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val completedTop = onNodeWithText("종료 · 중단")
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
        onNodeWithText("날짜 미정").assertDoesNotExist()
        onNodeWithText("지역 미정").assertDoesNotExist()

        // Group headers carry the status; only a paused event, which shares the
        // `종료 · 중단` group with finished ones, spells it out in its meta line.
        onNodeWithText("중단 · PACIFIC").assertIsDisplayed()
        onAllNodesWithText("진행 중", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("종료").assertDoesNotExist()
        val ongoingRowTop = onNodeWithTag(eventRowTag(ongoingEvent.id)).fetchSemanticsNode().boundsInRoot.top
        val completedRowTop = onNodeWithTag(eventRowTag(completedEvent.id)).fetchSemanticsNode().boundsInRoot.top
        val pausedRowTop = onNodeWithTag(eventRowTag(pausedEvent.id)).fetchSemanticsNode().boundsInRoot.top
        assertTrue(ongoingRowTop in ongoingTop..upcomingTop)
        assertTrue(completedRowTop > completedTop && pausedRowTop > completedTop)
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
        // The `예정` group header is the status; the row adds no status or meta text.
        onAllNodesWithText("예정", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("날짜 미정").assertDoesNotExist()
        onNodeWithText("지역 미정").assertDoesNotExist()
    }

    @Test
    fun activeRefreshKeepsEventsAndShowsRefreshProgressState() = runComposeUiTest {
        setContent {
            EventsContentFixture(
                uiState = EventsUiState(
                    contentState = populatedState.contentState,
                    isRefreshing = true,
                ),
            )
        }

        onNodeWithTag(EVENTS_REFRESHING_TAG).assertExists()
        onNodeWithText(longEventName).assertIsDisplayed()
    }

    @Test
    fun groupTitlesAreKoreanHeadingsWithVisibleCountsAndAnEmptyGroupKeepsItsTitleWithoutCount() = runComposeUiTest {
        setContent { EventsContentFixture(uiState = populatedState) }

        listOf("진행 중", "예정", "종료 · 중단").forEach { title ->
            onNode(hasText(title) and isHeading(), useUnmergedTree = true).assertExists()
        }
        listOf("Ongoing", "Upcoming", "Completed / Paused").forEach { onNodeWithText(it).assertDoesNotExist() }
        // `.pen` 40-1·40-5: counts follow the titles; the empty `예정` group has none.
        onNodeWithText("1", useUnmergedTree = true).assertExists()
        onNodeWithText("2", useUnmergedTree = true).assertExists()
        onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(0)
        val upcomingHeader = onNodeWithText("예정").fetchSemanticsNode().boundsInRoot
        val emptyMessage = onNodeWithText("현재 이벤트가 없어요.").fetchSemanticsNode().boundsInRoot
        assertTrue(emptyMessage.top >= upcomingHeader.bottom)
    }

    @Test
    fun groupsAreWhiteSectionsSeparatedByAnEightDpCanvasGap() =
        runSkikoComposeUiTest(size = Size(360f, 1200f)) {
            setContent { EventsContentFixture(uiState = populatedState) }

            val ongoingRow = onNodeWithTag(eventRowTag(ongoingEvent.id)).fetchSemanticsNode().boundsInRoot
            val upcomingHeader = onNodeWithText("예정").fetchSemanticsNode().boundsInRoot
            // The header text sits under the 20dp top padding of the section header.
            assertEquals(ongoingRow.bottom + 8f + 20f, upcomingHeader.top, 1f)
        }

    @Test
    fun structuredPeriodIsKoreanAndReadAsARangeWhileOtherwiseTheSourceLabelStays() = runComposeUiTest {
        val events = EventList(
            ongoing = listOf(ongoingEvent.copy(startDate = EventMonthDay(9, 12), endDate = EventMonthDay(10, 4))),
            upcoming = listOf(
                upcomingEventWithoutMetadata.copy(dateLabel = "Nov 1—TBD", startDate = EventMonthDay(11, 1)),
                upcomingEventWithoutMetadata.copy(
                    id = "same-month",
                    dateLabel = "Nov 20—23",
                    startDate = EventMonthDay(11, 20),
                    endDate = EventMonthDay(11, 23),
                ),
            ),
            completedOrPaused = listOf(
                completedEvent.copy(dateLabel = "Dec 17", startDate = EventMonthDay(12, 17), endDate = EventMonthDay(12, 17)),
                pausedEvent.copy(dateLabel = "Season"),
            ),
        )
        setContent { EventsContentFixture(uiState = EventsUiState(contentState = EventsContentState.Content(events))) }

        onNodeWithText("9월 12일 ~ 10월 4일 · KR", useUnmergedTree = true).assertExists()
        onNodeWithText("11월 1일 ~ 미정", useUnmergedTree = true).assertExists()
        onNodeWithText("11월 20일 ~ 23일", useUnmergedTree = true).assertExists()
        onNodeWithText("12월 17일", useUnmergedTree = true).assertExists()
        onNodeWithText("중단 · Season · PACIFIC", useUnmergedTree = true).assertExists()
        onNodeWithText("AUG 20 - SEP 4", substring = true, useUnmergedTree = true).assertDoesNotExist()
        // TalkBack and VoiceOver read the range with words instead of the tilde.
        onNodeWithTag(eventRowTag(ongoingEvent.id))
            .assert(hasContentDescriptionExactly("9월 12일부터 10월 4일까지 · KR"))
        onNodeWithTag(eventRowTag(upcomingEventWithoutMetadata.id))
            .assert(hasContentDescriptionExactly("11월 1일부터, 종료일 미정"))
    }

    @Test
    fun longNamesAndPeriodsWrapWithoutClippingOnNarrowScreensAndDoubleFonts() {
        val longPeriodEvent = ongoingEvent.copy(
            regionCode = "INTERNATIONAL",
            startDate = EventMonthDay(12, 28),
            endDate = EventMonthDay(1, 14),
        )
        val meta = "12월 28일 ~ 1월 14일 · INTERNATIONAL"
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 2400f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            EventsContentFixture(
                                uiState = EventsUiState(
                                    contentState = EventsContentState.Content(
                                        EventList(listOf(longPeriodEvent), emptyList(), listOf(completedEvent)),
                                    ),
                                ),
                            )
                        }
                    }

                    val label = "width=$width fontScale=$scale"
                    val row = onNodeWithTag(eventRowTag(ongoingEvent.id)).fetchSemanticsNode().boundsInRoot
                    assertTrue(row.left >= 0f && row.right <= width, "$label row leaves the screen")
                    listOf(longEventName, meta).forEach { text ->
                        assertTextFits(text, eventRowTag(ongoingEvent.id), row, label)
                    }
                    listOf("진행 중", "종료 · 중단").forEach { title ->
                        val header = onNode(hasText(title) and isHeading(), useUnmergedTree = true)
                        val layout = header.fetchSemanticsNode().textLayout()
                        assertEquals(title.length, layout.getLineEnd(layout.lineCount - 1), "$label '$title' is truncated")
                    }
                }
            }
        }
    }

    @Test
    fun rtlPlacesTheLogoOnTheRightAndTheChevronOnTheLeft() =
        runSkikoComposeUiTest(size = Size(360f, 1200f)) {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EventsContentFixture(uiState = populatedState)
                }
            }

            val row = onNodeWithTag(eventRowTag(ongoingEvent.id)).fetchSemanticsNode().boundsInRoot
            val title = onNode(
                hasText(longEventName) and hasAnyAncestor(hasTestTag(eventRowTag(ongoingEvent.id))),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            // The 40dp logo slot leads on the right with a 16dp inset; the 48dp chevron slot trails on the left.
            assertEquals(row.right - 16f - 40f - 12f, title.right, 1f)
            assertTrue(title.left >= row.left + 4f + 48f - 1f, "title overlaps the chevron slot: $title / $row")
            val header = onNodeWithText("진행 중").fetchSemanticsNode().boundsInRoot
            assertEquals(row.right - 16f, header.right, 1f)
        }

    private fun ComposeUiTest.assertTextFits(text: String, rowTag: String, row: Rect, label: String) {
        val node = onNode(hasText(text) and hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true)
        val layout = node.fetchSemanticsNode().textLayout()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
        assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
        assertTrue(bounds.right <= row.right + 0.5f && bounds.bottom <= row.bottom + 0.5f, "$label '$text' leaves its row")
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
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

package kr.co.cotton.vlrgg_mobile.ui.feature.series.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventStatus
import kr.co.cotton.vlrgg_mobile.domain.model.events.EventSummary
import kr.co.cotton.vlrgg_mobile.domain.model.series.SeriesDetail
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals

@OptIn(DelicateCoilApi::class, ExperimentalTestApi::class)
class SeriesDetailContentUiTest {

    @Test
    fun loadingKeepsBackAndFullSkeletonWithoutSampleData() = runComposeUiTest {
        var backCount = 0
        setContent { Fixture(onBack = { backCount += 1 }) }

        onNodeWithContentDescription("뒤로 가기").assertIsDisplayed().performClick()
        onNodeWithTag(SERIES_DETAIL_LOADING_TAG).assertExists()
        // `.pen` 42-2: hero and row skeletons without section titles, announced as progress.
        val progress = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNode()
        assertEquals("시리즈 정보를 불러오는 중", progress.config[SemanticsProperties.StateDescription])
        onNodeWithText("예정 대회").assertDoesNotExist()
        onNodeWithText("완료 대회").assertDoesNotExist()
        onNodeWithText("Champions Tour").assertDoesNotExist()
        onNodeWithText("Masters Toronto").assertDoesNotExist()
        assertEquals(1, backCount)
    }

    @Test
    fun populatedOrdersSectionsPreservesMetadataAndNavigatesBothRowsByStableId() = runComposeUiTest {
        val clickedIds = mutableListOf<String>()
        setContent { Fixture(content(populatedSeries), onEventClick = clickedIds::add) }

        val identityTop = onNodeWithTag(SERIES_DETAIL_IDENTITY_TAG).fetchSemanticsNode().boundsInRoot.top
        val upcomingTop = onNodeWithTag(SERIES_DETAIL_UPCOMING_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        val completedTop = onNodeWithTag(SERIES_DETAIL_COMPLETED_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue(identityTop < upcomingTop)
        assertTrue(upcomingTop < completedTop)

        onNodeWithText("Champions Tour").assertExists()
        onNodeWithText("공식 글로벌 발로란트 챔피언십 시리즈 설명").assertExists()
        onNodeWithText("Sep 12 · INT").assertExists()
        onNodeWithText("정보 없음").assertExists()
        // The ongoing event keeps its `진행 중` chip among upcoming events; the completed section title
        // already says `종료` for its rows.
        onNode(
            hasText("진행 중") and hasAnyAncestor(hasTestTag(seriesEventRowTag(UPCOMING_ID))),
            useUnmergedTree = true,
        ).assertExists()
        val upcomingRowTop = onNodeWithTag(seriesEventRowTag(UPCOMING_ID)).fetchSemanticsNode().boundsInRoot.top
        val completedRowTop = onNodeWithTag(seriesEventRowTag(COMPLETED_ID)).fetchSemanticsNode().boundsInRoot.top
        assertTrue(upcomingRowTop in upcomingTop..completedTop)
        assertTrue(completedRowTop > completedTop)
        onNodeWithText("종료됨").assertDoesNotExist()
        onNodeWithTag(seriesEventRowTag(UPCOMING_ID)).performClick()
        onNodeWithTag(seriesEventRowTag(COMPLETED_ID)).performClick()
        assertEquals(listOf(UPCOMING_ID, COMPLETED_ID), clickedIds)
    }

    @Test
    fun onlyOngoingEventsCarryTheInProgressChipAmongUpcomingEvents() = runComposeUiTest {
        val scheduledId = "scheduled"
        val scheduled = EventSummary(
            id = scheduledId,
            name = "Masters London",
            status = EventStatus.UPCOMING,
            dateLabel = "Jun 7",
            regionCode = "EU",
        )
        setContent {
            Fixture(
                content(
                    populatedSeries.copy(upcomingEvents = populatedSeries.upcomingEvents + scheduled),
                ),
            )
        }

        onAllNodesWithText("진행 중", useUnmergedTree = true).assertCountEquals(1)
        onNode(
            hasText("진행 중") and hasAnyAncestor(hasTestTag(seriesEventRowTag(UPCOMING_ID))),
            useUnmergedTree = true,
        ).assertExists()
        onNodeWithTag(seriesEventRowTag(scheduledId)).assertExists()
        onNodeWithText("Jun 7 · EU", useUnmergedTree = true).assertExists()
    }

    @Test
    fun eachSingleEmptySectionKeepsTheOtherSuccessfulSection() = runComposeUiTest {
        val state = mutableStateOf(
            content(populatedSeries.copy(upcomingEvents = emptyList())),
        )
        setContent { Fixture(state.value) }

        onNodeWithText("예정된 대회가 없습니다.").assertExists()
        onNodeWithTag(seriesEventRowTag(COMPLETED_ID)).assertExists()
        onNodeWithTag(SERIES_DETAIL_EMPTY_TAG).assertDoesNotExist()

        state.value = content(populatedSeries.copy(completedEvents = emptyList()))
        onNodeWithTag(seriesEventRowTag(UPCOMING_ID)).assertExists()
        onNodeWithText("종료된 대회가 없습니다.").assertExists()
        onNodeWithTag(SERIES_DETAIL_EMPTY_TAG).assertDoesNotExist()
    }

    @Test
    fun overallEmptyAndErrorAreDistinctAndErrorExposesOnlySafeRetryAndBack() = runComposeUiTest {
        val state = mutableStateOf(
            content(populatedSeries.copy(upcomingEvents = emptyList(), completedEvents = emptyList())),
        )
        var retries = 0
        var backs = 0
        setContent {
            Fixture(
                uiState = state.value,
                onBack = { backs += 1 },
                onRetry = { retries += 1 },
            )
        }

        onNodeWithTag(SERIES_DETAIL_EMPTY_TAG).assertExists()
        onNodeWithTag(SERIES_DETAIL_UPCOMING_SECTION_TAG).assertDoesNotExist()
        onNodeWithTag(SERIES_DETAIL_ERROR_TAG).assertDoesNotExist()

        state.value = SeriesDetailUiState(SeriesDetailContentState.Error)
        onNodeWithTag(SERIES_DETAIL_ERROR_TAG).assertExists()
        onNodeWithText("시리즈 정보를 불러오지 못했습니다").assertExists()
        onNodeWithText("HTTP", substring = true).assertDoesNotExist()
        onNodeWithText("selector", substring = true, ignoreCase = true).assertDoesNotExist()
        onNodeWithContentDescription("재시도").performClick()
        onNodeWithContentDescription("뒤로 가기").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun providedImageIsUsedAndPrimaryTargetsRemainAccessibleAtCompactWidth() {
        var imageRequestCount = 0
        var requestedImageData: Any? = null
        var imageLoader: ImageLoader? = null
        val fakeEngine = FakeImageLoaderEngine.Builder()
            .default(
                Interceptor { chain ->
                    imageRequestCount += 1
                    requestedImageData = chain.request.data
                    ErrorResult(null, chain.request, IllegalStateException("event image fixture failure"))
                },
            )
            .build()

        SingletonImageLoader.setUnsafe(
            SingletonImageLoader.Factory { context ->
                ImageLoader.Builder(context)
                    .components { add(fakeEngine) }
                    .build()
                    .also { imageLoader = it }
            },
        )
        try {
            runComposeUiTest {
                var density = 1f
                setContent {
                    density = LocalDensity.current.density
                    Fixture(content(populatedSeries))
                }

                assertTrue(imageRequestCount > 0)
                assertEquals(IMAGE_URL, requestedImageData)
                // The failed image falls back to the `trophy` slot instead of an empty image.
                onNodeWithTag(seriesEventImageTag(UPCOMING_ID), useUnmergedTree = true).assertDoesNotExist()

                val minimumTargetPx = 48f * density
                listOf(
                    onNodeWithContentDescription("뒤로 가기"),
                    onNodeWithContentDescription("이벤트 상세: Masters Toronto Official International Championship"),
                    onNodeWithContentDescription("이벤트 상세: VCT 2026 시즌 종료 대회 공식 명칭"),
                ).forEach { interaction ->
                    val bounds = interaction.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.width >= minimumTargetPx, "Target width was ${bounds.width}px")
                    assertTrue(bounds.height >= minimumTargetPx, "Target height was ${bounds.height}px")
                }

                onNodeWithText("favorite", substring = true, ignoreCase = true).assertDoesNotExist()
                onNodeWithText("notification", substring = true, ignoreCase = true).assertDoesNotExist()
                onNodeWithText("Standings", substring = true, ignoreCase = true).assertDoesNotExist()
                onNodeWithText("filter", substring = true, ignoreCase = true).assertDoesNotExist()
                onNodeWithText("더보기").assertDoesNotExist()
                onNodeWithText("placeholder", substring = true, ignoreCase = true).assertDoesNotExist()
            }
        } finally {
            try {
                SingletonImageLoader.reset()
            } finally {
                imageLoader?.shutdown()
            }
        }
    }

    @Test
    fun sectionsUseKoreanHeadingTitlesWithVisibleCounts() = runComposeUiTest {
        setContent { Fixture(content(populatedSeries.copy(completedEvents = emptyList()))) }

        onNode(hasText("예정 대회") and isHeading(), useUnmergedTree = true).assertExists()
        onNode(hasText("완료 대회") and isHeading(), useUnmergedTree = true).assertExists()
        onNodeWithText("Upcoming Events").assertDoesNotExist()
        onNodeWithText("Completed Events").assertDoesNotExist()
        // `.pen` 42-5: the empty section keeps its title without a count.
        onNode(hasText("1") and hasAnyAncestor(hasTestTag(SERIES_DETAIL_UPCOMING_SECTION_TAG)), useUnmergedTree = true)
            .assertExists()
        onNode(hasText("0") and hasAnyAncestor(hasTestTag(SERIES_DETAIL_COMPLETED_SECTION_TAG)), useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithText("종료된 대회가 없습니다.").assertExists()
    }

    @Test
    fun sectionsAreWhiteBlocksEightDpBelowTheHeroAndApart() =
        runSkikoComposeUiTest(size = Size(360f, 1200f)) {
            setContent { Fixture(content(populatedSeries)) }

            val hero = onNodeWithTag(SERIES_DETAIL_IDENTITY_TAG).fetchSemanticsNode().boundsInRoot
            val upcoming = onNodeWithTag(SERIES_DETAIL_UPCOMING_SECTION_TAG).fetchSemanticsNode().boundsInRoot
            val upcomingRow = onNodeWithTag(seriesEventRowTag(UPCOMING_ID)).fetchSemanticsNode().boundsInRoot
            val completed = onNodeWithTag(SERIES_DETAIL_COMPLETED_SECTION_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(hero.bottom + 8f, upcoming.top, 1f)
            assertEquals(upcomingRow.bottom + 8f, completed.top, 1f)
        }

    @Test
    fun structuredPeriodIsKoreanAndReadAsARange() = runComposeUiTest {
        val upcoming = populatedSeries.upcomingEvents.single()
            .copy(startDate = EventMonthDay(9, 12), endDate = EventMonthDay(10, 4))
        val completed = populatedSeries.completedEvents.single()
            .copy(dateLabel = "Nov 1—TBD", startDate = EventMonthDay(11, 1))
        setContent {
            Fixture(content(populatedSeries.copy(upcomingEvents = listOf(upcoming), completedEvents = listOf(completed))))
        }

        onNodeWithText("9월 12일 ~ 10월 4일 · INT", useUnmergedTree = true).assertExists()
        onNodeWithText("11월 1일 ~ 미정", useUnmergedTree = true).assertExists()
        onNodeWithText("Sep 12", substring = true, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(seriesEventRowTag(UPCOMING_ID)).assert(
            hasContentDescription("9월 12일부터 10월 4일까지 · INT"),
        )
        onNodeWithTag(seriesEventRowTag(COMPLETED_ID)).assert(hasContentDescription("11월 1일부터, 종료일 미정"))
    }

    @Test
    fun overallEmptyPanelFillsTheRestOfTheScreenUnderTheHero() =
        runSkikoComposeUiTest(size = Size(360f, 780f)) {
            setContent { Fixture(content(populatedSeries.copy(upcomingEvents = emptyList(), completedEvents = emptyList()))) }

            val hero = onNodeWithTag(SERIES_DETAIL_IDENTITY_TAG).fetchSemanticsNode().boundsInRoot
            val panel = onNodeWithTag(SERIES_DETAIL_EMPTY_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(hero.bottom, panel.top, 1f)
            assertEquals(780f, panel.bottom, 1f)
            // `.pen` 42-3: the message is centered in that area, not pinned under the hero.
            val title = onNodeWithText("표시할 대회가 없습니다.").fetchSemanticsNode().boundsInRoot
            assertTrue(title.top > panel.top + panel.height / 3, "panel content is not centered: $title / $panel")
        }

    @Test
    fun longNamesPeriodsAndTitlesWrapWithoutClippingOnNarrowScreensAndDoubleFonts() {
        val upcoming = populatedSeries.upcomingEvents.single()
            .copy(startDate = EventMonthDay(12, 28), endDate = EventMonthDay(1, 14))
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 2400f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            Fixture(content(populatedSeries.copy(upcomingEvents = listOf(upcoming))))
                        }
                    }

                    val label = "width=$width fontScale=$scale"
                    listOf(
                        upcoming.name,
                        "12월 28일 ~ 1월 14일 · INT",
                        populatedSeries.name,
                        populatedSeries.description!!,
                        "예정 대회",
                        "완료 대회",
                    ).forEach { text ->
                        val node = onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
                        val layout = node.textLayout()
                        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
                        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
                        assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
                        assertTrue(node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
                    }
                    // The `진행 중` chip stays whole next to the wrapped period.
                    val chip = onNode(hasText("진행 중"), useUnmergedTree = true).fetchSemanticsNode()
                    val chipLayout = chip.textLayout()
                    assertEquals(1, chipLayout.lineCount, "$label chip wraps")
                }
            }
        }
    }

    @Test
    fun rtlMirrorsTheHeroRowsAndSectionTitles() =
        runSkikoComposeUiTest(size = Size(360f, 1200f)) {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Fixture(content(populatedSeries))
                }
            }

            val name = onNode(hasText(populatedSeries.name) and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f - 72f - 16f, name.right, 1f)
            val row = onNodeWithTag(seriesEventRowTag(COMPLETED_ID)).fetchSemanticsNode().boundsInRoot
            val title = onNode(
                hasText("VCT 2026 시즌 종료 대회 공식 명칭") and hasAnyAncestor(hasTestTag(seriesEventRowTag(COMPLETED_ID))),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertEquals(row.right - 16f - 40f - 12f, title.right, 1f)
            val section = onNode(hasText("완료 대회") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(360f - 16f, section.right, 1f)
        }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Composable
    private fun Fixture(
        uiState: SeriesDetailUiState = SeriesDetailUiState(),
        onBack: () -> Unit = {},
        onEventClick: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        VlrTheme {
            SeriesDetailContent(
                uiState = uiState,
                listState = rememberLazyListState(),
                onBack = onBack,
                onEventClick = onEventClick,
                onRetry = onRetry,
            )
        }
    }

    private fun content(series: SeriesDetail) = SeriesDetailUiState(
        contentState = SeriesDetailContentState.Content(series),
    )

    private companion object {
        const val UPCOMING_ID = "upcoming"
        const val COMPLETED_ID = "completed"
        const val IMAGE_URL = "https://example.invalid/masters.png"

        val populatedSeries = SeriesDetail(
            id = "2",
            name = "Champions Tour",
            description = "공식 글로벌 발로란트 챔피언십 시리즈 설명",
            upcomingEvents = listOf(
                EventSummary(
                    id = UPCOMING_ID,
                    name = "Masters Toronto Official International Championship",
                    status = EventStatus.ONGOING,
                    dateLabel = "Sep 12",
                    regionCode = "INT",
                    imageUrl = IMAGE_URL,
                ),
            ),
            completedEvents = listOf(
                EventSummary(
                    id = COMPLETED_ID,
                    name = "VCT 2026 시즌 종료 대회 공식 명칭",
                    status = EventStatus.COMPLETED,
                    dateLabel = null,
                    regionCode = null,
                    imageUrl = null,
                ),
            ),
        )
    }
}

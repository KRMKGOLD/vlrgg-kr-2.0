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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(DelicateCoilApi::class, ExperimentalTestApi::class)
class SeriesDetailContentUiTest {

    @Test
    fun loadingKeepsBackAndFullSkeletonWithoutSampleData() = runComposeUiTest {
        var backCount = 0
        setContent { Fixture(onBack = { backCount += 1 }) }

        onNodeWithContentDescription("뒤로 가기").assertIsDisplayed().performClick()
        onNodeWithTag(SERIES_DETAIL_LOADING_TAG).assertExists()
        onNodeWithText("Upcoming Events").assertExists()
        onNodeWithText("Completed Events").assertExists()
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
        onNodeWithText("진행 중").assertExists()
        onNodeWithText("종료됨").assertExists()
        onNodeWithTag(seriesEventRowTag(UPCOMING_ID)).performClick()
        onNodeWithTag(seriesEventRowTag(COMPLETED_ID)).performClick()
        assertEquals(listOf(UPCOMING_ID, COMPLETED_ID), clickedIds)
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

                onNodeWithTag(seriesEventImageTag(UPCOMING_ID), useUnmergedTree = true).assertExists()
                assertTrue(imageRequestCount > 0)
                assertEquals(IMAGE_URL, requestedImageData)

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

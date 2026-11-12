package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(DelicateCoilApi::class, ExperimentalTestApi::class)
class NewsDetailImageFailureUiTest {

    @Test
    fun imageFailureKeepsArticleContentAndCaptionVisibleWithoutAnArticleLevelError() {
        var imageRequestCount = 0
        var requestedImageData: Any? = null
        var imageLoader: ImageLoader? = null
        val fakeEngine = FakeImageLoaderEngine.Builder()
            .default(
                Interceptor { chain ->
                    imageRequestCount += 1
                    requestedImageData = chain.request.data
                    ErrorResult(
                        image = null,
                        request = chain.request,
                        throwable = IllegalStateException("Image fixture failure"),
                    )
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
                setContent {
                    VlrTheme {
                        NewsDetailContent(
                            uiState = NewsDetailUiState(
                                NewsDetailContentState.Content(articleWithImage()),
                            ),
                            onBack = {},
                            onTeamClick = {},
                            onPlayerClick = {},
                            onRetry = {},
                        )
                    }
                }

                val beforeImageBounds = onNodeWithText(BEFORE_IMAGE_TEXT)
                    .assertExists()
                    .fetchSemanticsNode()
                    .boundsInRoot
                val imageBounds = onNodeWithContentDescription(IMAGE_CAPTION)
                    .assertExists()
                    .fetchSemanticsNode()
                    .boundsInRoot
                val captionBounds = onNodeWithText(IMAGE_CAPTION)
                    .assertExists()
                    .fetchSemanticsNode()
                    .boundsInRoot
                val afterImageBounds = onNodeWithText(AFTER_IMAGE_LIST_ITEM)
                    .assertExists()
                    .fetchSemanticsNode()
                    .boundsInRoot

                // 실패하면 이미지 자리에 32dp `image` 아이콘을 두고, 아이콘은 낭독하지 않는다.
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag(NEWS_DETAIL_IMAGE_PLACEHOLDER_TAG, useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                val placeholder = onNodeWithTag(NEWS_DETAIL_IMAGE_PLACEHOLDER_TAG, useUnmergedTree = true)
                    .assertIsDisplayed()
                    .assertWidthIsEqualTo(32.dp)
                    .assertHeightIsEqualTo(32.dp)
                assertTrue(SemanticsProperties.ContentDescription !in placeholder.fetchSemanticsNode().config)
                assertEquals(184.dp.value, (imageBounds.bottom - imageBounds.top) / density.density, 0.5f)

                assertTrue(beforeImageBounds.top < imageBounds.top)
                assertTrue(imageBounds.top < captionBounds.top)
                assertTrue(captionBounds.top < afterImageBounds.top)
                onNodeWithText("재시도").assertDoesNotExist()
                onNodeWithText("기사 내용을 불러올 수 없습니다.").assertDoesNotExist()
                onNodeWithText("Partial", substring = true).assertDoesNotExist()
                onNodeWithText("일부 콘텐츠", substring = true).assertDoesNotExist()
            }
            assertTrue(imageRequestCount > 0, "AsyncImage did not make the expected image request")
            assertEquals(IMAGE_URL, requestedImageData)
        } finally {
            try {
                SingletonImageLoader.reset()
            } finally {
                imageLoader?.shutdown()
            }
        }
    }

    private fun articleWithImage() = NewsArticle(
        articleId = "image-failure-article",
        slug = "image-failure-article",
        title = "이미지 실패 회귀 기사",
        author = "VLR.GG",
        publishedAt = "2026-08-19",
        blocks = listOf(
            NewsArticleBlock.Paragraph(
                content = listOf(NewsArticleInline.Text(BEFORE_IMAGE_TEXT)),
            ),
            NewsArticleBlock.Image(
                imageUrl = IMAGE_URL,
                caption = IMAGE_CAPTION,
            ),
            NewsArticleBlock.ListBlock(
                ordered = false,
                items = listOf(listOf(NewsArticleInline.Text(AFTER_IMAGE_LIST_ITEM))),
            ),
        ),
        publishedAtUtc = null,
    )

    private companion object {
        const val BEFORE_IMAGE_TEXT = "이미지 앞 본문"
        const val IMAGE_URL = "https://example.test/news-detail-failure.png"
        const val IMAGE_CAPTION = "실패해도 유지되는 캡션"
        const val AFTER_IMAGE_LIST_ITEM = "이미지 뒤 목록 항목"
    }
}

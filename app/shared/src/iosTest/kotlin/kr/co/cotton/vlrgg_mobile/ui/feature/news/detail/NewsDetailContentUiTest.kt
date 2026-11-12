package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrLightColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@OptIn(ExperimentalTestApi::class)
class NewsDetailContentUiTest {

    @Test
    fun articleShowsLightHeaderTitleKoreanBylineAndArticleTypography() = runComposeUiTest {
        setDetail(NewsDetailContentState.Content(article))

        onNode(isHeading() and SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("뉴스"))))
            .assertIsDisplayed()
        onNodeWithText(TITLE).assertIsDisplayed()
        onNode(isHeading() and SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(TITLE))))
            .assertExists()
        val title = onNodeWithText(TITLE).textStyle()
        assertEquals(24.sp, title.fontSize)
        assertEquals(FontWeight.Black, title.fontWeight)

        val expectedDate = KoreanDateTimeFormat.dateTime(PUBLISHED_AT, TimeZone.currentSystemDefault())
        val byline = onNodeWithText("jenopelle · $expectedDate")
        byline.assertIsDisplayed()
        assertEquals(13.sp, byline.textStyle().fontSize)
        assertEquals(VlrLightColors.muted, byline.textStyle().color)
        onNodeWithText("작성자", substring = true).assertDoesNotExist()

        val paragraph = onNodeWithText(PARAGRAPH).textStyle()
        assertEquals(16.sp, paragraph.fontSize)
        assertEquals(27.sp, paragraph.lineHeight)
        assertEquals(VlrLightColors.ink, paragraph.color)

        assertTrue(onNodeWithText(TITLE).top() < byline.top())
        assertTrue(byline.top() < onNodeWithText(PARAGRAPH).top())
    }

    @Test
    fun bylineFallsBackToSourceTextWithoutUtcInstant() = runComposeUiTest {
        setDetail(NewsDetailContentState.Content(article.copy(publishedAtUtc = null)))

        onNodeWithText("jenopelle · 16 hours ago").assertIsDisplayed()
    }

    @Test
    fun routableLinksAreAccentUnderlinedLinksThatNavigate() = runComposeUiTest {
        val teams = mutableListOf<String>()
        val players = mutableListOf<String>()
        setDetail(
            NewsDetailContentState.Content(article),
            onTeamClick = { teams += it },
            onPlayerClick = { players += it },
        )

        val linked = onNodeWithText(LINKED_TEXT)
        val links = linked.fetchSemanticsNode().config[SemanticsProperties.Text].single().getLinkAnnotations(0, LINKED_TEXT.length)
        assertEquals(listOf("Team Liquid", "purp0"), links.map { LINKED_TEXT.substring(it.start, it.end) })
        links.forEach { range ->
            val style = (range.item as LinkAnnotation.Clickable).styles?.style
            assertEquals(VlrLightColors.accent, style?.color)
            assertEquals(TextDecoration.Underline, style?.textDecoration)
            assertEquals(FontWeight.SemiBold, style?.fontWeight)
        }
        // 링크마다 보조 기술이 링크로 다루는 별도 노드가 있다(Android ClickableSpan, iOS link trait).
        assertEquals(2, linked.fetchSemanticsNode().children.count { SemanticsProperties.LinkTestMarker in it.config })

        linked.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "Team:474" }
        linked.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "Player:9554" }
        assertEquals(listOf("474"), teams)
        assertEquals(listOf("9554"), players)

        // 이동할 수 없는 링크는 일반 글자로 남는다.
        val plain = onNodeWithText(UNSUPPORTED_LINK_TEXT)
        assertTrue(
            plain.fetchSemanticsNode().config[SemanticsProperties.Text].single()
                .getLinkAnnotations(0, UNSUPPORTED_LINK_TEXT.length).isEmpty(),
        )
    }

    @Test
    fun listKeepsOrderWithMutedMarkers() = runComposeUiTest {
        setDetail(NewsDetailContentState.Content(article))

        val first = onNodeWithText(FIRST_ITEM)
        val second = onNodeWithText(SECOND_ITEM)
        assertTrue(first.top() < second.top())
        assertEquals(16.sp, first.textStyle().fontSize)
        val markers = onAllNodesWithText("•").fetchSemanticsNodes()
        assertEquals(2, markers.size)
        assertEquals(VlrLightColors.muted, onAllNodesWithText("•")[0].textStyle().color)
    }

    @Test
    fun missingOptionalImageKeepsTheRestOfTheArticleInOrder() = runComposeUiTest {
        setDetail(
            NewsDetailContentState.Content(
                article.copy(blocks = article.blocks.filterNot { it is NewsArticleBlock.Image }),
            ),
        )

        onNodeWithTag(NEWS_DETAIL_IMAGE_PLACEHOLDER_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(CAPTION).assertDoesNotExist()
        assertTrue(onNodeWithText(LINKED_TEXT).top() < onNodeWithText(FIRST_ITEM).top())
    }

    @Test
    fun loadingShowsLightHeaderAndSilentArticleSkeleton() = runComposeUiTest {
        setDetail(NewsDetailContentState.Loading)

        onNodeWithText("뉴스").assertIsDisplayed()
        onNodeWithTag(NEWS_DETAIL_SKELETON_TAG).assertIsDisplayed()
        assertTrue(onNodeWithTag(NEWS_DETAIL_SKELETON_TAG).fetchSemanticsNode().children.isEmpty())
        onNodeWithText("재시도").assertDoesNotExist()
    }

    @Test
    fun emptyArticleShowsDescriptionStatePanelWithoutRetry() = runComposeUiTest {
        setDetail(NewsDetailContentState.Empty(article.copy(blocks = emptyList())))

        onNodeWithText("기사 내용을 불러올 수 없습니다.").assertIsDisplayed()
        onNodeWithText("본문 내용이 비어 있거나 유효하지 않습니다.").assertIsDisplayed()
        onNodeWithText("재시도").assertDoesNotExist()
        onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun errorShowsStatePanelRetry() = runComposeUiTest {
        var retries = 0
        setDetail(NewsDetailContentState.Error, onRetry = { retries += 1 })

        onNodeWithText("기사 내용을 불러올 수 없습니다.").assertIsDisplayed()
        onNodeWithText("재시도").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun largeFontKeepsTitleBylineAndBodyStackedWithoutOverlap() = runComposeUiTest {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                VlrTheme {
                    NewsDetailContent(
                        uiState = NewsDetailUiState(NewsDetailContentState.Content(article.copy(publishedAtUtc = null))),
                        onBack = {},
                        onTeamClick = {},
                        onPlayerClick = {},
                        onRetry = {},
                        modifier = Modifier.size(360.dp, 780.dp),
                    )
                }
            }
        }

        val title = onNodeWithText(TITLE).assertIsDisplayed()
        val byline = onNodeWithText("jenopelle · 16 hours ago").assertIsDisplayed()
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.bottom <= byline.top())
        assertTrue(byline.fetchSemanticsNode().boundsInRoot.bottom <= onNodeWithText(PARAGRAPH).top())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setDetail(
        state: NewsDetailContentState,
        onTeamClick: (String) -> Unit = {},
        onPlayerClick: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        setContent {
            VlrTheme {
                NewsDetailContent(
                    uiState = NewsDetailUiState(state),
                    onBack = {},
                    onTeamClick = onTeamClick,
                    onPlayerClick = onPlayerClick,
                    onRetry = onRetry,
                    modifier = Modifier.size(360.dp, 1_200.dp),
                )
            }
        }
    }

    private fun SemanticsNodeInteraction.top(): Float = fetchSemanticsNode().boundsInRoot.top

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style
    }

    private companion object {
        const val TITLE = "purp0 searching for new team following Team Liquid benching"
        const val PARAGRAPH = "Sample paragraph. The article body is shown as published on the source."
        const val LINKED_TEXT = "Linked in text: Team Liquid, purp0"
        const val UNSUPPORTED_LINK_TEXT = "See VCT Champions for the bracket."
        const val CAPTION = "Image caption from the source article"
        const val FIRST_ITEM = "First list item from the source"
        const val SECOND_ITEM = "Second list item from the source"
        val PUBLISHED_AT = Instant.parse("2026-09-19T02:05:00Z")

        val article = NewsArticle(
            articleId = "101",
            slug = "purp0-free-agent",
            title = TITLE,
            author = "jenopelle",
            publishedAt = "16 hours ago",
            publishedAtUtc = PUBLISHED_AT,
            blocks = listOf(
                NewsArticleBlock.Paragraph(listOf(NewsArticleInline.Text(PARAGRAPH))),
                NewsArticleBlock.Paragraph(
                    listOf(
                        NewsArticleInline.Text("Linked in text: "),
                        NewsArticleInline.Link("Team Liquid", NewsLinkKind.TEAM, "474/team-liquid"),
                        NewsArticleInline.Text(", "),
                        NewsArticleInline.Link("purp0", NewsLinkKind.PLAYER, "9554/purp0"),
                    ),
                ),
                NewsArticleBlock.Paragraph(
                    listOf(
                        NewsArticleInline.Text("See "),
                        NewsArticleInline.Link("VCT Champions", NewsLinkKind.EVENT, "2283/champions"),
                        NewsArticleInline.Text(" for the bracket."),
                    ),
                ),
                NewsArticleBlock.Image(imageUrl = "https://example.test/figure.png", caption = CAPTION),
                NewsArticleBlock.ListBlock(
                    ordered = false,
                    items = listOf(
                        listOf(NewsArticleInline.Text(FIRST_ITEM)),
                        listOf(NewsArticleInline.Text(SECOND_ITEM)),
                    ),
                ),
            ),
        )
    }
}

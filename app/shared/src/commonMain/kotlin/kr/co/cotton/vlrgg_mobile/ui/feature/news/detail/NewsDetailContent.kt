package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kr.co.cotton.vlrgg_mobile.ui.component.DetailHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.newsByline
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.withKoreanWordBreak
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_description
import vlrggmobile.app.shared.generated.resources.ic_image

internal const val NEWS_DETAIL_SKELETON_TAG = "news-detail-skeleton"
internal const val NEWS_DETAIL_IMAGE_PLACEHOLDER_TAG = "news-detail-image-placeholder"

@Composable
fun NewsDetailContent(
    uiState: NewsDetailUiState,
    onBack: () -> Unit,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { DetailHeader(onBack = onBack, title = "뉴스") },
    ) { contentPadding ->
        when (val contentState = uiState.contentState) {
            NewsDetailContentState.Loading -> NewsDetailSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is NewsDetailContentState.Content -> NewsArticleContent(
                article = contentState.article,
                onTeamClick = onTeamClick,
                onPlayerClick = onPlayerClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is NewsDetailContentState.Empty -> NewsArticleEmpty(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            NewsDetailContentState.Error -> if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                Box(Modifier.fillMaxSize().padding(contentPadding))
            } else {
                NewsDetailError(
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }
        }
    }
}

/** 기사 본문 영역: 위 20·아래 32·좌우 16dp, 요소 사이 16dp(`.pen` 11-1). */
private val ArticleContentPadding = PaddingValues(
    start = VlrDimensions.Space4,
    top = VlrDimensions.Space5,
    end = VlrDimensions.Space4,
    bottom = VlrDimensions.Space8,
)
private val ArticleImageHeight = 184.dp

@Composable
private fun NewsArticleContent(
    article: NewsArticle,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    LazyColumn(
        modifier = modifier,
        contentPadding = ArticleContentPadding,
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        item(key = "title") {
            Text(
                text = article.title,
                modifier = Modifier.semantics { heading() },
                style = VlrTheme.typography.title.withKoreanWordBreak(),
                color = VlrTheme.colors.ink,
            )
        }
        newsByline(article.author, article.publishedLabel(timeZone))?.let { byline ->
            item(key = "byline") {
                Text(
                    text = byline,
                    style = VlrTheme.typography.meta,
                    color = VlrTheme.colors.muted,
                )
            }
        }
        item(key = "rule") {
            HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
        }
        itemsIndexed(
            items = article.blocks,
            key = { index, _ -> "block-$index" },
        ) { _, block ->
            NewsArticleBlockContent(
                block = block,
                onTeamClick = onTeamClick,
                onPlayerClick = onPlayerClick,
            )
        }
    }
}

/** `publishedAtUtc`가 있으면 [timeZone] 기준으로 포맷하고, 없을 때만 source text를 쓴다. */
internal fun NewsArticle.publishedLabel(timeZone: TimeZone): String =
    publishedAtUtc?.let { publishedAt -> KoreanDateTimeFormat.dateTime(publishedAt, timeZone) } ?: publishedAt

@Composable
private fun NewsArticleBlockContent(
    block: NewsArticleBlock,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is NewsArticleBlock.Paragraph -> InlineArticleText(
            content = block.content,
            onTeamClick = onTeamClick,
            onPlayerClick = onPlayerClick,
            style = VlrTheme.typography.article,
            modifier = modifier,
        )

        is NewsArticleBlock.Image -> NewsArticleFigure(block = block, modifier = modifier)

        is NewsArticleBlock.ListBlock -> Column(
            modifier = modifier.padding(start = VlrDimensions.Space1),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = newsDetailListMarker(ordered = block.ordered, index = index),
                        style = VlrTheme.typography.article.copy(lineHeight = ArticleListLineHeight),
                        color = VlrTheme.colors.muted,
                    )
                    InlineArticleText(
                        content = item,
                        onTeamClick = onTeamClick,
                        onPlayerClick = onPlayerClick,
                        style = VlrTheme.typography.article.copy(lineHeight = ArticleListLineHeight),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val ArticleListLineHeight = 1.6.em

/**
 * 본문 이미지와 캡션. 로딩 중에는 `surface-sunken` 자리만, 실패하면 `image` 아이콘을 그린다.
 * 이미지는 캡션(없으면 `기사 이미지`)으로 읽히고, 실패 아이콘은 장식이라 읽지 않는다.
 */
@Composable
private fun NewsArticleFigure(
    block: NewsArticleBlock.Image,
    modifier: Modifier = Modifier,
) {
    var imageFailed by remember(block.imageUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(VlrDimensions.CornerRadius)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ArticleImageHeight)
                .clip(shape)
                .background(VlrTheme.colors.surfaceSunken, shape),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = block.imageUrl,
                contentDescription = block.caption ?: "기사 이미지",
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                onSuccess = { imageFailed = false },
                modifier = Modifier.fillMaxSize(),
            )
            if (imageFailed) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_image),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(NEWS_DETAIL_IMAGE_PLACEHOLDER_TAG),
                    tint = VlrTheme.colors.disabled,
                )
            }
        }
        block.caption?.let { caption ->
            Text(
                text = caption,
                style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal).withKoreanWordBreak(),
                color = VlrTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun InlineArticleText(
    content: List<NewsArticleInline>,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildNewsDetailAnnotatedText(
            content = content,
            onTeamClick = onTeamClick,
            onPlayerClick = onPlayerClick,
            linkColor = VlrTheme.colors.accent,
        ),
        style = style.withKoreanWordBreak(),
        color = VlrTheme.colors.ink,
        modifier = modifier,
    )
}

/** 본문 block이 없을 때 콘텐츠 영역 전체의 State panel(`.pen` 11-3). */
@Composable
private fun NewsArticleEmpty(
    modifier: Modifier = Modifier,
) {
    StatePanel(
        title = "기사 내용을 불러올 수 없습니다.",
        body = "본문 내용이 비어 있거나 유효하지 않습니다.",
        icon = Res.drawable.ic_description,
        modifier = modifier,
    )
}

@Composable
private fun NewsDetailError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanel(
        title = "기사 내용을 불러올 수 없습니다.",
        actionLabel = "재시도",
        onAction = onRetry,
        modifier = modifier,
    )
}

/** 제목·작성자·구분선·문단·이미지 자리(`.pen` 11-2). 밝은 헤더는 Scaffold가 그대로 둔다. */
@Composable
private fun NewsDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(NEWS_DETAIL_SKELETON_TAG)
            .clearAndSetSemantics {}
            .verticalScroll(rememberScrollState())
            .padding(ArticleContentPadding),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(24.dp))
        SkeletonBlock(Modifier.size(width = 220.dp, height = 24.dp))
        SkeletonBlock(Modifier.size(width = 180.dp, height = 12.dp))
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.line)
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(14.dp)) }
        SkeletonBlock(Modifier.size(width = 240.dp, height = 14.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(ArticleImageHeight))
        repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(14.dp)) }
        SkeletonBlock(Modifier.size(width = 200.dp, height = 14.dp))
    }
}

internal enum class NewsDetailLinkTarget {
    Team,
    Player,
}

internal fun NewsArticleInline.Link.routableTarget(): NewsDetailLinkTarget? = when (kind) {
    NewsLinkKind.TEAM -> NewsDetailLinkTarget.Team
    NewsLinkKind.PLAYER -> NewsDetailLinkTarget.Player
    NewsLinkKind.EVENT,
    NewsLinkKind.MATCH,
    NewsLinkKind.INTERNAL_UNSUPPORTED,
    NewsLinkKind.EXTERNAL,
    -> null
}

internal fun NewsArticleInline.Link.navigationIdOrNull(): String? = reference
    ?.trim()
    ?.trim('/')
    ?.substringBefore('/')
    ?.takeIf(String::isNotBlank)

internal fun newsDetailListMarker(
    ordered: Boolean,
    index: Int,
): String = if (ordered) "${index + 1}." else "•"

@Composable
private fun buildNewsDetailAnnotatedText(
    content: List<NewsArticleInline>,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    content.forEach { inline ->
        when (inline) {
            is NewsArticleInline.Text -> append(inline.text)
            is NewsArticleInline.Link -> {
                val target = inline.routableTarget()
                val navigationId = inline.navigationIdOrNull()
                if (target == null || navigationId == null) {
                    append(inline.label)
                } else {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "${target.name}:$navigationId",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = LinkInteractionListener {
                                when (target) {
                                    NewsDetailLinkTarget.Team -> onTeamClick(navigationId)
                                    NewsDetailLinkTarget.Player -> onPlayerClick(navigationId)
                                }
                            },
                        ),
                    ) {
                        append(inline.label)
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsDetailLoadingPreview() {
    NewsDetailPreview(NewsDetailUiState())
}

@Preview(
    name = "Content",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsDetailContentPreview() {
    NewsDetailPreview(
        NewsDetailUiState(NewsDetailContentState.Content(previewArticle)),
    )
}

@Preview(
    name = "Empty",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsDetailEmptyPreview() {
    NewsDetailPreview(
        NewsDetailUiState(NewsDetailContentState.Empty(previewArticle.copy(blocks = emptyList()))),
    )
}

@Preview(
    name = "Error",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun NewsDetailErrorPreview() {
    NewsDetailPreview(
        NewsDetailUiState(NewsDetailContentState.Error),
    )
}

@Composable
private fun NewsDetailPreview(uiState: NewsDetailUiState) {
    VlrTheme {
        NewsDetailContent(
            uiState = uiState,
            onBack = {},
            onTeamClick = {},
            onPlayerClick = {},
            onRetry = {},
        )
    }
}

private val previewArticle = NewsArticle(
    articleId = "101",
    slug = "purp0-free-agent",
    title = "purp0 searching for new team following Team Liquid benching",
    author = "jenopelle",
    publishedAt = "16 hours ago",
    publishedAtUtc = Instant.parse("2026-09-19T02:05:00Z"),
    blocks = listOf(
        NewsArticleBlock.Paragraph(
            content = listOf(
                NewsArticleInline.Text(
                    "Sample paragraph. The article body is shown as published on the source, " +
                        "keeping the original language and the original paragraph order.",
                ),
            ),
        ),
        NewsArticleBlock.Paragraph(
            content = listOf(
                NewsArticleInline.Text("Linked in text: "),
                NewsArticleInline.Link("Team Liquid", NewsLinkKind.TEAM, "474/team-liquid"),
                NewsArticleInline.Text(", "),
                NewsArticleInline.Link("purp0", NewsLinkKind.PLAYER, "9554/purp0"),
            ),
        ),
        // 미리보기는 네트워크 이미지를 불러오지 않으므로 실패 placeholder가 보인다.
        NewsArticleBlock.Image(
            imageUrl = "https://example.com/vlrgg-v3/news-figure.png",
            caption = "Image caption from the source article",
        ),
        NewsArticleBlock.ListBlock(
            ordered = false,
            items = listOf(
                listOf(NewsArticleInline.Text("First list item from the source")),
                listOf(NewsArticleInline.Text("Second list item from the source")),
            ),
        ),
    ),
)

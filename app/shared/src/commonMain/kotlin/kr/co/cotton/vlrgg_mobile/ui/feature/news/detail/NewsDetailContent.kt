package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleBlock
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_error
import vlrggmobile.app.shared.generated.resources.ic_news

@Composable
fun NewsDetailContent(
    uiState: NewsDetailUiState,
    onBack: () -> Unit,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { NewsDetailTopBar(onBack = onBack) },
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
                article = contentState.article,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            NewsDetailContentState.Error -> NewsDetailError(
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }
}

@Composable
private fun NewsDetailTopBar(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(VlrTheme.colors.surface),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = VlrDimensions.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlrIconButton(
                contentDescription = "뒤로 가기",
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_arrow_back),
                        contentDescription = null,
                    )
                },
            )
        }
        HorizontalDivider(
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.outline,
        )
    }
}

@Composable
private fun NewsArticleContent(
    article: NewsArticle,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        item(key = "header") {
            NewsArticleHeader(article = article)
        }
        itemsIndexed(
            items = article.blocks,
            key = { index, _ -> "block-$index" },
        ) { index, block ->
            NewsArticleBlockContent(
                block = block,
                onTeamClick = onTeamClick,
                onPlayerClick = onPlayerClick,
                modifier = Modifier.padding(
                    start = VlrDimensions.Space4,
                    end = VlrDimensions.Space4,
                    top = if (index == 0) VlrDimensions.Space6 else VlrDimensions.Space3,
                ),
            )
        }
        item(key = "bottom-space") {
            Spacer(Modifier.height(VlrDimensions.Space6))
        }
    }
}

@Composable
private fun NewsArticleHeader(
    article: NewsArticle,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier.padding(
            start = VlrDimensions.Space4,
            top = VlrDimensions.Space6,
            end = VlrDimensions.Space4,
        ),
    ) {
        Text(
            text = article.title,
            style = VlrTheme.typography.display,
            color = VlrTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(VlrDimensions.Space3))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "작성자: ${article.author}",
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.textSecondary,
            )
            Text(
                text = " · ",
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.outline,
            )
            Text(
                text = article.publishedAt,
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.textSecondary,
            )
        }
        if (showDivider) {
            Spacer(Modifier.height(VlrDimensions.Space3))
            HorizontalDivider(
                thickness = VlrDimensions.OutlineWidth,
                color = VlrTheme.colors.outline,
            )
        } else {
            Spacer(Modifier.height(VlrDimensions.Space6))
        }
    }
}

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
            modifier = modifier,
        )

        is NewsArticleBlock.Image -> Column(modifier = modifier) {
            AsyncImage(
                model = block.imageUrl,
                contentDescription = block.caption ?: "기사 이미지",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(192.dp)
                    .clip(RoundedCornerShape(VlrDimensions.DefaultCornerRadius))
                    .border(
                        width = VlrDimensions.OutlineWidth,
                        color = VlrTheme.colors.outline,
                        shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius),
                    ),
            )
            block.caption?.let { caption ->
                Text(
                    text = caption,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = VlrDimensions.Space2),
                )
            }
        }

        is NewsArticleBlock.ListBlock -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        ) {
            block.items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = newsDetailListMarker(ordered = block.ordered, index = index),
                        style = VlrTheme.typography.body,
                        color = VlrTheme.colors.actionPrimary,
                        modifier = Modifier.width(VlrDimensions.Space6),
                    )
                    InlineArticleText(
                        content = item,
                        onTeamClick = onTeamClick,
                        onPlayerClick = onPlayerClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineArticleText(
    content: List<NewsArticleInline>,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildNewsDetailAnnotatedText(
            content = content,
            onTeamClick = onTeamClick,
            onPlayerClick = onPlayerClick,
            linkColor = VlrTheme.colors.actionPrimary,
        ),
        style = VlrTheme.typography.body,
        color = VlrTheme.colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
private fun NewsArticleEmpty(
    article: NewsArticle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        NewsArticleHeader(article = article, showDivider = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = VlrDimensions.Space4)
                .padding(bottom = VlrDimensions.Space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_news),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = VlrTheme.colors.outline,
            )
            Spacer(Modifier.height(VlrDimensions.Space3))
            Text(
                text = "기사 내용을 불러올 수 없습니다.",
                style = VlrTheme.typography.sectionTitle,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(VlrDimensions.Space1))
            Text(
                text = "본문 내용이 비어 있거나 유효하지 않습니다.",
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NewsDetailError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = VlrDimensions.Space4),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space6),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = VlrTheme.colors.outline,
                )
                Text(
                    text = "기사 내용을 불러올 수 없습니다.",
                    style = VlrTheme.typography.body,
                    color = VlrTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            VlrButton(
                text = "재시도",
                onClick = onRetry,
                modifier = Modifier.widthIn(min = 120.dp),
            )
        }
    }
}

@Composable
private fun NewsDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    val skeletonShape = RoundedCornerShape(VlrDimensions.Space1)

    LazyColumn(
        modifier = modifier.clearAndSetSemantics {},
    ) {
        item(key = "hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(202.dp)
                    .background(VlrTheme.colors.skeleton),
            )
        }
        item(key = "article-skeleton") {
            Column(
                modifier = Modifier.padding(
                    horizontal = VlrDimensions.Space4,
                    vertical = VlrDimensions.Space6,
                ),
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(VlrDimensions.Space4))
                            .background(VlrTheme.colors.skeleton),
                    )
                    Spacer(Modifier.width(VlrDimensions.Space3))
                    Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1)) {
                        SkeletonLine(widthFraction = 0.32f, height = 16.dp, shape = skeletonShape)
                        SkeletonLine(widthFraction = 0.20f, height = 12.dp, shape = skeletonShape)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                    SkeletonLine(widthFraction = 1f, height = 32.dp, shape = skeletonShape)
                    SkeletonLine(widthFraction = 0.75f, height = 32.dp, shape = skeletonShape)
                    SkeletonLine(widthFraction = 0.5f, height = 32.dp, shape = skeletonShape)
                }
                HorizontalDivider(
                    thickness = VlrDimensions.OutlineWidth,
                    color = VlrTheme.colors.outline,
                    modifier = Modifier.padding(vertical = VlrDimensions.Space3),
                )
                Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                    repeat(5) { index ->
                        SkeletonLine(
                            widthFraction = when (index) {
                                2 -> 0.9f
                                3 -> 0.95f
                                4 -> 0.7f
                                else -> 1f
                            },
                            height = 16.dp,
                            shape = skeletonShape,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = VlrDimensions.OutlineWidth,
                            color = VlrTheme.colors.outline,
                            shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius),
                        )
                        .padding(VlrDimensions.Space4),
                    verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
                ) {
                    SkeletonLine(widthFraction = 0.33f, height = 20.dp, shape = skeletonShape)
                    SkeletonLine(widthFraction = 1f, height = 16.dp, shape = skeletonShape)
                    SkeletonLine(widthFraction = 0.8f, height = 16.dp, shape = skeletonShape)
                }
                Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                    repeat(4) { index ->
                        SkeletonLine(
                            widthFraction = if (index == 1) 0.85f else if (index == 3) 0.6f else 1f,
                            height = 16.dp,
                            shape = skeletonShape,
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
                ) {
                    repeat(3) { index ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(VlrDimensions.Space1)
                                    .clip(RoundedCornerShape(VlrDimensions.Space1))
                                    .background(VlrTheme.colors.skeleton),
                            )
                            Spacer(Modifier.width(VlrDimensions.Space2))
                            SkeletonLine(
                                widthFraction = if (index == 1) 0.85f else 0.75f,
                                height = 16.dp,
                                shape = skeletonShape,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .background(VlrTheme.colors.skeleton),
    )
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
    slug = "champions-run",
    title = "Sentinels, TenZ의 맹활약에 힘입어 마스터스 결승 진출 확정",
    author = "이스포츠 에디터",
    publishedAt = "3시간 전",
    blocks = listOf(
        NewsArticleBlock.Paragraph(
            content = listOf(
                NewsArticleInline.Text("북미의 강호 "),
                NewsArticleInline.Link("Sentinels", NewsLinkKind.TEAM, "2/sentinels"),
                NewsArticleInline.Text("가 다시 한번 세계 무대 정상에 도전한다."),
            ),
        ),
        NewsArticleBlock.Image(
            imageUrl = "https://www.gstatic.com/labs-code/stitch/stitch-placeholder-300x300.svg",
            caption = "중요한 라운드 후 팀원들과 환호하는 모습",
        ),
        NewsArticleBlock.ListBlock(
            ordered = false,
            items = listOf(
                listOf(NewsArticleInline.Text("퍼스트 블러드 성공률: Sentinels 68% 우위")),
                listOf(NewsArticleInline.Text("에코 라운드 승률: 평균 대비 높은 수치 기록")),
            ),
        ),
    ),
)

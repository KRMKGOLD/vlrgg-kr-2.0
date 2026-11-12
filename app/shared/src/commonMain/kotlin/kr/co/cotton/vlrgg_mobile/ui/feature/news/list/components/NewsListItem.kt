package kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.component.NewsRow
import kr.co.cotton.vlrgg_mobile.ui.component.newsByline

/** News list and event news row. [headline] marks the first article of the news list. */
@Composable
fun NewsListItem(
    news: NewsSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    headline: Boolean = false,
) {
    NewsRow(
        title = news.title,
        byline = newsByline(news.author, news.publishedAt),
        onClick = onClick,
        modifier = modifier,
        headline = headline,
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewNewsListItem() {
    VlrTheme {
        NewsListItem(
            news = NewsSummary(
                articleId = "12345",
                slug = "sample-news-article",
                title = "Preview News Title",
                author = "Editor",
                publishedAt = "2023-10-27"
            ),
            onClick = {},
        )
    }
}

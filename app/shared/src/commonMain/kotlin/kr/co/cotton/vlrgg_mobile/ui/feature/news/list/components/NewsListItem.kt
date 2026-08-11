package kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions.Space1
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions.Space3
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions.Space4
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

@Composable
fun NewsListItem(
    news: NewsSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = Space3, horizontal = Space4)
            .fillMaxWidth(),
    ) {
        val desc = buildString {
            append(news.author)
            append(" · ")
            append(news.publishedAt)
        }

        Text(
            text = news.title,
            style = VlrTheme.typography.bodyStrong
        )
        Spacer(Modifier.height(Space1))
        Text(
            text = desc,
            style = VlrTheme.typography.label,
            color = VlrTheme.colors.textSecondary
        )
    }
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

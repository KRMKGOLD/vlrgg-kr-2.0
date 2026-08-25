package kr.co.cotton.vlrgg_mobile.ui.feature.news.list.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsSummary
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NewsListItemUiTest {

    @Test
    fun whitespaceAuthorShowsPublishedAtWithoutSeparator() = runComposeUiTest {
        setContent {
            VlrTheme {
                NewsListItem(
                    news = NewsSummary(
                        articleId = "101",
                        slug = "masters-seoul",
                        title = "Masters begins",
                        author = "   ",
                        publishedAt = "2026-08-25",
                    ),
                    onClick = {},
                )
            }
        }

        onNodeWithText("2026-08-25").assertIsDisplayed()
        onNodeWithText("·", substring = true).assertDoesNotExist()
    }
}

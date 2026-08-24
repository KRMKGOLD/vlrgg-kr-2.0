package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search

internal const val ROOT_TOP_BAR_TAG = "root-top-bar"
internal const val ROOT_TOP_BAR_CONTENT_TAG = "root-top-bar-content"
internal const val ROOT_TOP_BAR_TITLE_TAG = "root-top-bar-title"
internal const val ROOT_TOP_BAR_SEARCH_TAG = "root-top-bar-search"

internal val RootTopBarContentHeight = 56.dp

/** Shared top bar for bottom-navigation roots. */
@Composable
fun RootTopBar(
    title: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .testTag(ROOT_TOP_BAR_TAG),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RootTopBarContentHeight)
                .padding(horizontal = VlrDimensions.Space4)
                .testTag(ROOT_TOP_BAR_CONTENT_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ROOT_TOP_BAR_TITLE_TAG),
                style = VlrTheme.typography.pageTitle,
                color = VlrTheme.colors.textPrimary,
            )
            VlrIconButton(
                contentDescription = "검색",
                onClick = onSearch,
                modifier = Modifier.testTag(ROOT_TOP_BAR_SEARCH_TAG),
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_search),
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

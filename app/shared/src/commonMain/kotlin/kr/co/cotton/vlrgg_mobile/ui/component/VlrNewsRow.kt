package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

/**
 * Text-only news row: title 15/600 (line height 1.45) and a 12 `muted` byline (`DESIGN.md` 8.2).
 * [headline] is the larger first-article variant of the news list.
 */
@Composable
fun NewsRow(
    title: String,
    byline: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    headline: Boolean = false,
    contentDescription: String? = null,
    bylineTestTag: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .background(VlrTheme.colors.surface)
            .rowDivider(VlrTheme.colors.line)
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = if (headline) VlrDimensions.Space5 else 14.dp,
                bottom = if (headline) 18.dp else 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = if (headline) {
                VlrTheme.typography.headline
            } else {
                VlrTheme.typography.body.copy(lineHeight = 1.45.em)
            },
            color = VlrTheme.colors.ink,
        )
        byline?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                modifier = bylineTestTag?.let(Modifier::testTag) ?: Modifier,
                style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                color = VlrTheme.colors.muted,
            )
        }
    }
}

/** `작성자 · 게시일`; either part may be missing. */
fun newsByline(author: String?, published: String?): String? =
    listOfNotNull(author?.takeIf(String::isNotBlank), published?.takeIf(String::isNotBlank))
        .joinToString(" · ")
        .takeIf(String::isNotEmpty)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NewsRowPreview() {
    VlrTheme {
        Column {
            NewsRow(
                title = "KIWOOM DRX advance to the Pacific playoffs",
                byline = "jenopelle · September 19, 2026",
                onClick = {},
                headline = true,
            )
            NewsRow(
                title = "purp0 searching for new team following Team Liquid benching",
                byline = "jenopelle · September 19, 2026",
                onClick = {},
            )
        }
    }
}

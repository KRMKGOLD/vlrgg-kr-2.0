package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_search
import vlrggmobile.app.shared.generated.resources.ic_star

internal const val RootHeaderSearchLabel = "검색"
internal const val DetailHeaderBackLabel = "뒤로 가기"

/** Root screen header: `title` text and a 48dp search action (`DESIGN.md` 8.1). */
@Composable
fun RootHeader(
    title: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(VlrDimensions.HeaderHeight)
            .background(VlrTheme.colors.surface)
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = VlrTheme.typography.title.copy(letterSpacing = (-0.5).sp),
            color = VlrTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        VlrIconButton(
            contentDescription = RootHeaderSearchLabel,
            onClick = onSearch,
            icon = { Icon(vectorResource(Res.drawable.ic_search), contentDescription = null) },
        )
    }
}

enum class DetailHeaderVariant {
    /** `surface` header with an optional title and a bottom `line` (news detail, search, licenses). */
    Light,

    /** `panel` header joined with the dark hero band; it never shows a title. */
    Dark,
}

/**
 * Pushed-screen header: Back, optional title (light only), and feature actions (`DESIGN.md` 8.1).
 * [actions] receives the icon color of the variant.
 */
@Composable
fun DetailHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    variant: DetailHeaderVariant = DetailHeaderVariant.Light,
    actions: @Composable RowScope.(iconColor: Color) -> Unit = {},
) {
    val colors = VlrTheme.colors
    val container = if (variant == DetailHeaderVariant.Dark) colors.panel else colors.surface
    val iconColor = if (variant == DetailHeaderVariant.Dark) colors.onPanel else colors.ink

    Column(modifier = modifier.fillMaxWidth().background(container)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(VlrDimensions.HeaderHeight)
                .padding(horizontal = VlrDimensions.Space1),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlrIconButton(
                contentDescription = DetailHeaderBackLabel,
                onClick = onBack,
                tint = iconColor,
                icon = { Icon(vectorResource(Res.drawable.ic_arrow_back), contentDescription = null) },
            )
            val visibleTitle = title?.takeIf { variant == DetailHeaderVariant.Light }
            if (visibleTitle != null) {
                Text(
                    text = visibleTitle,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = VlrTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            actions(iconColor)
        }
        if (variant == DetailHeaderVariant.Light) {
            HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = colors.line)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun HeaderPreview() {
    VlrTheme {
        Column {
            RootHeader(title = "경기", onSearch = {})
            DetailHeader(onBack = {}, title = "뉴스")
            DetailHeader(
                onBack = {},
                variant = DetailHeaderVariant.Dark,
                actions = { iconColor ->
                    VlrIconButton(
                        contentDescription = "즐겨찾기 추가",
                        onClick = {},
                        tint = iconColor,
                        icon = { Icon(vectorResource(Res.drawable.ic_star), contentDescription = null) },
                    )
                },
            )
        }
    }
}

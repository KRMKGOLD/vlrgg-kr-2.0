package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_star
import vlrggmobile.app.shared.generated.resources.ic_star_fill

/**
 * Top of the dark detail band (`DESIGN.md` 2·3): the status bar area and the dark [DetailHeader]
 * on `panel`, with light status bar icons while it is shown. [content] is optional fixed band
 * content under the header; scrolling heroes ([EntityHero], the match scoreboard) follow it in
 * the screen's list on the same `panel` color, so the band reads as one block.
 */
@Composable
fun DetailTopBand(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.(iconColor: Color) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    LightStatusBarIconsEffect()
    Column(modifier = modifier.fillMaxWidth().background(VlrTheme.colors.panel)) {
        DetailHeader(
            onBack = onBack,
            variant = DetailHeaderVariant.Dark,
            windowInsets = windowInsets,
            actions = actions,
        )
        content()
    }
}

/**
 * Team, player, event and series hero on `panel` (`DESIGN.md` 8.3): a 72dp [LogoSlot], the
 * `display` name in `on-panel` and 13 `on-panel-muted` meta lines. [facts] holds the optional
 * row under the identity, such as the event status chip.
 */
@Composable
fun EntityHero(
    name: String,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    meta: List<String> = emptyList(),
    facts: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.panel)
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space2,
                bottom = VlrDimensions.Space6,
            ),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            logo()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            ) {
                Text(
                    text = name,
                    modifier = Modifier.semantics { heading() },
                    style = VlrTheme.typography.display.copy(lineHeight = 1.2.em),
                    color = VlrTheme.colors.onPanel,
                )
                meta.filter(String::isNotBlank).forEach {
                    Text(text = it, style = VlrTheme.typography.meta, color = VlrTheme.colors.onPanelMuted)
                }
            }
        }
        facts?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
                verticalAlignment = Alignment.CenterVertically,
                content = it,
            )
        }
    }
}

/** Favorite star of the dark header: `on-panel` outline, `on-panel-accent` FILL when on. */
@Composable
fun FavoriteStarIcon(isFavorite: Boolean) {
    Icon(
        imageVector = vectorResource(if (isFavorite) Res.drawable.ic_star_fill else Res.drawable.ic_star),
        contentDescription = null,
    )
}

/** Icon tint for the favorite star on the dark header. */
@Composable
fun favoriteStarTint(isFavorite: Boolean, iconColor: Color): Color =
    if (isFavorite) VlrTheme.colors.onPanelAccent else iconColor

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DetailBandPreview() {
    VlrTheme {
        Column {
            DetailTopBand(
                onBack = {},
                actions = { iconColor ->
                    VlrIconButton(
                        contentDescription = "즐겨찾기 해제",
                        onClick = {},
                        tint = favoriteStarTint(isFavorite = true, iconColor = iconColor),
                        icon = { FavoriteStarIcon(isFavorite = true) },
                    )
                },
            )
            EntityHero(
                name = "KIWOOM DRX",
                meta = listOf("DRX · 대한민국"),
                logo = {
                    LogoSlot(
                        imageUrl = null,
                        kind = LogoSlotKind.Team,
                        placeholderText = "DRX",
                        size = LogoSlotDefaults.HeroSize,
                        surface = LogoSlotSurface.Dark,
                    )
                },
            )
            SkeletonHero(Modifier.padding(top = 8.dp))
        }
    }
}

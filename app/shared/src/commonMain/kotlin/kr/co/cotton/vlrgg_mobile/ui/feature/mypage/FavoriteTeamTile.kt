package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_star_fill

/**
 * Favorite tile (`DESIGN.md` 8.2, `.pen` `PLXGm`): the whole tile opens the team detail and the
 * 48dp star is a separate removal target. Text wraps instead of truncating, so the tile grows
 * with the font scale; [modifier] lands on the tile target.
 */
@Composable
internal fun FavoriteTeamTile(
    favorite: FavoriteTeam,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDetail = remember(favorite.id, onClick) { { onClick(favorite.id) } }
    val removeFavorite = remember(favorite.id, onRemove) { { onRemove(favorite.id) } }
    val shape = RoundedCornerShape(VlrDimensions.CornerRadius)

    Column(
        modifier = modifier
            .clip(shape)
            .background(VlrTheme.colors.surface)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.line, shape)
            .semantics { contentDescription = "팀 상세: ${favorite.name}" }
            .clickable(role = Role.Button, onClick = openDetail)
            .padding(start = 14.dp, top = VlrDimensions.Space3, end = VlrDimensions.Space1, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoSlot(
                imageUrl = favorite.imageUrl,
                kind = LogoSlotKind.Team,
                placeholderText = favorite.tag?.takeIf(String::isNotBlank) ?: logoInitials(favorite.name),
                size = LogoSlotDefaults.TileSize,
                imageTestTag = myPageTeamImageTag(favorite.id),
                placeholderTestTag = myPageTeamImagePlaceholderTag(favorite.id),
            )
            VlrIconButton(
                contentDescription = "${favorite.name} 즐겨찾기 해제",
                onClick = removeFavorite,
                tint = VlrTheme.colors.accent,
                icon = { Icon(vectorResource(Res.drawable.ic_star_fill), contentDescription = null) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = favorite.name,
                style = VlrTheme.typography.body.copy(fontWeight = FontWeight.ExtraBold),
                color = VlrTheme.colors.ink,
            )
            favoriteTeamMeta(favorite)?.let {
                Text(
                    text = it,
                    style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = VlrTheme.colors.muted,
                )
            }
        }
    }
}

private fun favoriteTeamMeta(favorite: FavoriteTeam): String? =
    listOfNotNull(favorite.tag, favorite.country).joinToString(" · ").ifBlank { null }

/** Loading placeholder in the place of a [FavoriteTeamTile] (`.pen` 30-2 `Tile skeleton`). */
@Composable
internal fun FavoriteTeamTileSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(VlrDimensions.CornerRadius)
    Column(
        modifier = modifier
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.line, shape)
            .padding(14.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        SkeletonBlock(Modifier.size(LogoSlotDefaults.TileSize))
        SkeletonBlock(Modifier.size(width = 100.dp, height = 14.dp))
        SkeletonBlock(Modifier.size(width = 72.dp, height = 10.dp))
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FavoriteTeamTilePreview() {
    VlrTheme {
        Row(
            modifier = Modifier.padding(VlrDimensions.Space4),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        ) {
            FavoriteTeamTile(
                favorite = FavoriteTeam(id = "1", name = "KIWOOM DRX", tag = "DRX", country = "대한민국"),
                onClick = {},
                onRemove = {},
                modifier = Modifier.weight(1f),
            )
            FavoriteTeamTileSkeleton(Modifier.weight(1f))
        }
    }
}

package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerCurrentTeam
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

internal fun playerCurrentTeamCardTag(teamId: String) = playerTeamRowTag(teamId)
internal fun playerTeamLogoTag(teamId: String) = "player-team-logo-$teamId"
internal fun playerTeamLogoPlaceholderTag(teamId: String) = "player-team-logo-placeholder-$teamId"

@Composable
internal fun PlayerCurrentTeamCard(
    team: PlayerCurrentTeam,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(playerCurrentTeamCardTag(team.id))
            .clip(shape)
            .background(VlrTheme.colors.surfaceSubtle, shape)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .semantics { contentDescription = "팀 상세: ${team.name}" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamLogoFrame(team)
        Spacer(Modifier.width(VlrDimensions.Space3))
        Text(
            text = team.name,
            modifier = Modifier.weight(1f),
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(VlrDimensions.Space2))
        Text(
            text = "›",
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TeamLogoFrame(team: PlayerCurrentTeam) {
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(VlrTheme.colors.surface)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        val imageUrl = team.imageUrl?.takeIf(String::isNotBlank)
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp).testTag(playerTeamLogoTag(team.id)),
            )
        } else {
            Text(
                text = team.name.stableTeamPlaceholder(),
                modifier = Modifier.testTag(playerTeamLogoPlaceholderTag(team.id)),
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textBrand,
            )
        }
    }
}

private fun String.stableTeamPlaceholder(): String = trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerCurrentTeam
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRow
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials

internal fun playerCurrentTeamCardTag(teamId: String) = playerTeamRowTag(teamId)
internal fun playerTeamLogoTag(teamId: String) = "player-team-logo-$teamId"
internal fun playerTeamLogoPlaceholderTag(teamId: String) = "player-team-logo-placeholder-$teamId"

@Composable
internal fun PlayerCurrentTeamCard(
    team: PlayerCurrentTeam,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IdentityRow(
        title = team.name,
        leading = {
            LogoSlot(
                imageUrl = team.imageUrl,
                kind = LogoSlotKind.Team,
                placeholderText = logoInitials(team.name),
                imageTestTag = playerTeamLogoTag(team.id),
                placeholderTestTag = playerTeamLogoPlaceholderTag(team.id),
            )
        },
        onClick = onClick,
        contentDescription = "팀 상세: ${team.name}",
        modifier = modifier.testTag(playerCurrentTeamCardTag(team.id)),
    )
}

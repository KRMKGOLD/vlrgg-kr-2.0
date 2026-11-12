package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.math.roundToInt

/** Result column of a [TeamMatchRow]; only player matches carry one. */
enum class TeamMatchOutcome(val label: String) {
    Win("승리"),
    Loss("패배"),
    Unknown("결과 미정"),
}

/** Right-hand status of a [TeamMatchRow]. */
sealed interface TeamMatchRowStatus {
    val text: String

    /** Saira 20 score text. [isMissing] draws it `muted` (`—`). */
    data class Score(override val text: String, val isMissing: Boolean = false) : TeamMatchRowStatus

    /** System 13/700 `muted` label, such as the remaining time of an upcoming match. */
    data class Label(override val text: String) : TeamMatchRowStatus
}

/**
 * Team and player detail match row (`DESIGN.md` 8.2): optional 60dp result column, wrapping
 * title with event context, and status with date on the right. The whole row opens the match.
 */
@Composable
fun TeamMatchRow(
    title: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    context: String? = null,
    status: TeamMatchRowStatus? = null,
    date: String? = null,
    outcome: TeamMatchOutcome? = null,
    statusTestTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .background(VlrTheme.colors.surface)
            .rowDivider(VlrTheme.colors.line)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        outcome?.let {
            Text(
                text = it.label,
                modifier = Modifier.width(60.dp),
                style = VlrTheme.typography.meta.copy(fontWeight = FontWeight.ExtraBold),
                color = if (it == TeamMatchOutcome.Win) VlrTheme.colors.accent else VlrTheme.colors.muted,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = VlrTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = VlrTheme.colors.ink,
            )
            context?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = VlrTheme.colors.muted,
                )
            }
        }
        if (status != null || date != null) {
            Column(
                modifier = Modifier.capWidth(TeamMatchRowTrailingMaxFraction),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                status?.let { TeamMatchStatusText(it, statusTestTag) }
                date?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                        color = VlrTheme.colors.muted,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * Share of the width left beside the result column that the status and date column may take. A
 * long schedule (`2025년 12월 24일 오후 12:00` at a large font) wraps inside it instead of being
 * measured first at full width and leaving the match title no room.
 */
internal const val TeamMatchRowTrailingMaxFraction = 0.45f

/** Measures with at most [fraction] of the incoming max width, so a [Row] sibling keeps the rest. */
private fun Modifier.capWidth(fraction: Float): Modifier = layout { measurable, constraints ->
    val maxWidth = if (constraints.hasBoundedWidth) {
        (constraints.maxWidth * fraction).roundToInt().coerceAtLeast(constraints.minWidth)
    } else {
        constraints.maxWidth
    }
    val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

@Composable
private fun TeamMatchStatusText(status: TeamMatchRowStatus, testTag: String?) {
    val tagModifier = testTag?.let(Modifier::testTag) ?: Modifier
    when (status) {
        is TeamMatchRowStatus.Score -> Text(
            text = status.text,
            modifier = tagModifier,
            style = VlrTheme.typography.scoreM,
            color = if (status.isMissing) VlrTheme.colors.muted else VlrTheme.colors.ink,
        )

        is TeamMatchRowStatus.Label -> Text(
            text = status.text,
            modifier = tagModifier,
            style = VlrTheme.typography.meta.copy(fontWeight = FontWeight.Bold),
            color = VlrTheme.colors.muted,
            textAlign = TextAlign.End,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TeamMatchRowPreview() {
    VlrTheme {
        Column {
            TeamMatchRow(
                title = "vs Paper Rex",
                onClick = {},
                contentDescription = "경기 상세: KIWOOM DRX 대 Paper Rex",
                context = "VCT Pacific 2026 · Playoffs–Upper Final",
                status = TeamMatchRowStatus.Label("1일 6시간 후"),
                date = "9월 24일 오후 6:00",
            )
            TeamMatchRow(
                title = "Nongshim RedForce vs BBL Esports",
                onClick = {},
                contentDescription = "경기 상세: Nongshim RedForce 2, BBL Esports 0",
                context = "Esports World Cup 2026 · Playoffs",
                status = TeamMatchRowStatus.Score("2 : 0"),
                date = "7월 12일",
                outcome = TeamMatchOutcome.Win,
            )
        }
    }
}

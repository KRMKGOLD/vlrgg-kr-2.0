package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

/** Time column state of a [MatchRow]. Labels arrive formatted; the row only lays them out. */
sealed interface MatchRowStatus {
    /** Upcoming: start time and the optional remaining time. Scores are never shown. */
    data class Scheduled(val time: String, val remaining: String?) : MatchRowStatus

    /** `live` dot and `LIVE`. */
    data object Live : MatchRowStatus

    /** Start time and `종료`; the winner is emphasized. */
    data class Finished(val time: String?) : MatchRowStatus

    /** Postponed, cancelled or unavailable: time and a status label. */
    data class Other(val time: String?, val label: String, val isWarning: Boolean = false) : MatchRowStatus
}

data class MatchRowTeam(
    val name: String,
    val score: Int?,
)

data class MatchRowModel(
    val home: MatchRowTeam,
    val away: MatchRowTeam,
    val status: MatchRowStatus,
    val context: String? = null,
    val stateDescription: String? = null,
    val scoreTestTag: String? = null,
)

internal const val MatchRowFinishedLabel = "종료"
internal const val MatchRowLiveLabel = "LIVE"

fun matchRowHomeScoreTag(scoreTestTag: String): String = "$scoreTestTag-home"
fun matchRowAwayScoreTag(scoreTestTag: String): String = "$scoreTestTag-away"

/** Scores are shown for live and terminal matches; a finished match without a score shows `—`. */
internal fun MatchRowModel.showsScores(): Boolean = when (status) {
    is MatchRowStatus.Scheduled -> false
    MatchRowStatus.Live, is MatchRowStatus.Other -> home.score != null || away.score != null
    is MatchRowStatus.Finished -> true
}

/** Winner side of a finished match with two different scores; null otherwise. */
internal fun MatchRowModel.winner(): MatchRowTeam? {
    if (status !is MatchRowStatus.Finished) return null
    val homeScore = home.score ?: return null
    val awayScore = away.score ?: return null
    return when {
        homeScore > awayScore -> home
        awayScore > homeScore -> away
        else -> null
    }
}

/** `경기 상세: KIWOOM DRX 2, Paper Rex 1` with both scores, otherwise `경기 상세: A 대 B`. */
internal fun MatchRowModel.accessibilityLabel(): String {
    val homeScore = home.score
    val awayScore = away.score
    return if (showsScores() && homeScore != null && awayScore != null) {
        "경기 상세: ${home.name} $homeScore, ${away.name} $awayScore"
    } else {
        "경기 상세: ${home.name} 대 ${away.name}"
    }
}

/**
 * Match list row (`DESIGN.md` 8.2): 72dp time column, 1dp vertical line, teams with `score-m`
 * scores and a 12 `muted` event context. Team names wrap instead of truncating.
 */
@Composable
fun MatchRow(
    model: MatchRowModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val winner = model.winner()
    val showsScores = model.showsScores()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .background(VlrTheme.colors.surface)
            .rowDivider(VlrTheme.colors.line)
            .semantics {
                contentDescription = model.accessibilityLabel()
                model.stateDescription?.let { stateDescription = it }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .height(IntrinsicSize.Min)
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space3),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        MatchRowTimeColumn(model.status)
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.line,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MatchRowTeamLine(
                team = model.home,
                showScore = showsScores,
                isLoser = winner != null && winner != model.home,
                scoreTestTag = model.scoreTestTag?.let(::matchRowHomeScoreTag),
            )
            MatchRowTeamLine(
                team = model.away,
                showScore = showsScores,
                isLoser = winner != null && winner != model.away,
                scoreTestTag = model.scoreTestTag?.let(::matchRowAwayScoreTag),
            )
            model.context?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = VlrTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun MatchRowTimeColumn(status: MatchRowStatus) {
    val primary = VlrTheme.typography.meta.copy(fontWeight = FontWeight.Bold)
    val secondary = VlrTheme.typography.label
    Column(
        modifier = Modifier.width(72.dp).padding(top = 1.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (status) {
            is MatchRowStatus.Scheduled -> {
                Text(status.time, style = primary, color = VlrTheme.colors.ink)
                status.remaining?.let { Text(it, style = secondary, color = VlrTheme.colors.muted) }
            }

            MatchRowStatus.Live -> Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(VlrTheme.colors.live, CircleShape))
                Text(
                    text = MatchRowLiveLabel,
                    style = VlrTheme.typography.tag.copy(fontSize = 14.sp),
                    color = VlrTheme.colors.accent,
                )
            }

            is MatchRowStatus.Finished -> {
                status.time?.let { Text(it, style = primary, color = VlrTheme.colors.ink) }
                Text(MatchRowFinishedLabel, style = secondary, color = VlrTheme.colors.muted)
            }

            is MatchRowStatus.Other -> {
                status.time?.let { Text(it, style = primary, color = VlrTheme.colors.ink) }
                Text(
                    text = status.label,
                    style = secondary,
                    color = if (status.isWarning) VlrTheme.colors.warn else VlrTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun MatchRowTeamLine(
    team: MatchRowTeam,
    showScore: Boolean,
    isLoser: Boolean,
    scoreTestTag: String?,
) {
    val color: Color = if (isLoser) VlrTheme.colors.muted else VlrTheme.colors.ink
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = team.name,
            modifier = Modifier.weight(1f),
            style = VlrTheme.typography.body.copy(
                fontWeight = if (isLoser) FontWeight.Medium else FontWeight.Bold,
            ),
            color = color,
        )
        if (showScore) {
            Text(
                text = team.score?.toString() ?: "—",
                modifier = scoreTestTag?.let(Modifier::testTag) ?: Modifier,
                style = VlrTheme.typography.scoreM,
                color = if (team.score == null) VlrTheme.colors.muted else color,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MatchRowPreview() {
    VlrTheme {
        Column {
            MatchRow(
                model = MatchRowModel(
                    home = MatchRowTeam("KIWOOM DRX", 1),
                    away = MatchRowTeam("Paper Rex", 0),
                    status = MatchRowStatus.Live,
                    context = "VCT Pacific 2026 · Playoffs–Upper Final",
                ),
                onClick = {},
            )
            MatchRow(
                model = MatchRowModel(
                    home = MatchRowTeam("T1", null),
                    away = MatchRowTeam("Gen.G", null),
                    status = MatchRowStatus.Scheduled("오후 8:00", "1시간 32분 후"),
                    context = "VCT Pacific 2026 · Playoffs–Lower Round 2",
                ),
                onClick = {},
            )
            MatchRow(
                model = MatchRowModel(
                    home = MatchRowTeam("Havoc & Yonder GC", 1),
                    away = MatchRowTeam("Nova Esports GC", 3),
                    status = MatchRowStatus.Finished("오후 9:00"),
                    context = "Game Changers 2026: China · Grand Final",
                ),
                onClick = {},
            )
        }
    }
}

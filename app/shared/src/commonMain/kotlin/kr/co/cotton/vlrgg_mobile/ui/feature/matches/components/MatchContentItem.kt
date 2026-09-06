package kr.co.cotton.vlrgg_mobile.ui.feature.matches.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme

/** UI-only input shared by match-list cards and match-detail Head to Head rows. */
internal data class MatchContentItemModel(
    val homeTeamName: String,
    val awayTeamName: String,
    val scoreLabel: String,
    val scoreStyle: MatchContentItemScoreStyle,
    val scoreTestTag: String? = null,
    val leadingContent: MatchContentItemLeading? = null,
    val eventName: String? = null,
    val eventSeries: String? = null,
    val stateDescription: String? = null,
)

/** Typography stays caller-owned because list markers and H2H scores have different hierarchy. */
internal enum class MatchContentItemScoreStyle {
    LABEL,
    BODY_STRONG,
    DISPLAY,
}

internal sealed interface MatchContentItemLeading {
    data class Time(
        val timeLabel: String,
        val relativeTimeLabel: String?,
    ) : MatchContentItemLeading

    data class Status(
        val status: StatusChipStatus,
        val label: String,
        val relativeTimeLabel: String?,
    ) : MatchContentItemLeading
}

@Composable
internal fun MatchContentItem(
    item: MatchContentItemModel,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VlrDimensions.DefaultCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(testTag)
            .clip(shape)
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
            .semantics {
                contentDescription = "경기 상세: ${item.homeTeamName} 대 ${item.awayTeamName}"
                item.stateDescription?.let { description -> stateDescription = description }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(VlrDimensions.Space3),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        if (item.leadingContent != null || item.eventName != null || item.eventSeries != null) {
            MatchContentItemMetadata(item)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamName(item.homeTeamName, Modifier.weight(1f))
            Text(
                text = item.scoreLabel,
                modifier = item.scoreTestTag?.let { scoreTestTag -> Modifier.testTag(scoreTestTag) } ?: Modifier,
                style = when (item.scoreStyle) {
                    MatchContentItemScoreStyle.LABEL -> VlrTheme.typography.label
                    MatchContentItemScoreStyle.BODY_STRONG -> VlrTheme.typography.bodyStrong
                    MatchContentItemScoreStyle.DISPLAY -> VlrTheme.typography.display
                },
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TeamName(
                name = item.awayTeamName,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun MatchContentItemMetadata(item: MatchContentItemModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1)) {
            when (val leading = item.leadingContent) {
                is MatchContentItemLeading.Time -> {
                    Text(leading.timeLabel, style = VlrTheme.typography.label, color = VlrTheme.colors.textSecondary)
                    leading.relativeTimeLabel?.let { relativeTimeLabel ->
                        Text(relativeTimeLabel, style = VlrTheme.typography.labelSmall, color = VlrTheme.colors.textSecondary)
                    }
                }

                is MatchContentItemLeading.Status -> {
                    StatusChip(status = leading.status, label = leading.label)
                    leading.relativeTimeLabel?.let { relativeTimeLabel ->
                        Text(relativeTimeLabel, style = VlrTheme.typography.labelSmall, color = VlrTheme.colors.textSecondary)
                    }
                }

                null -> Unit
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        ) {
            item.eventName?.let { eventName ->
                Text(
                    text = eventName,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.eventSeries?.let { eventSeries ->
                Text(
                    text = eventSeries,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TeamName(
    name: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = name,
        modifier = modifier,
        style = VlrTheme.typography.bodyStrong,
        color = VlrTheme.colors.textPrimary,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

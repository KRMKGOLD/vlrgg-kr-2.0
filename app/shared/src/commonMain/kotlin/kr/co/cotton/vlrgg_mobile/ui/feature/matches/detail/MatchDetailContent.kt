package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back

internal const val MATCH_DETAIL_LOADING_TAG = "match-detail-loading"
internal const val MATCH_DETAIL_HERO_TAG = "match-detail-hero"
internal const val MATCH_DETAIL_EVENT_TAG = "match-detail-event"
internal const val MATCH_DETAIL_MAPS_SECTION_TAG = "match-detail-maps"
internal const val MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG = "match-detail-head-to-head"

internal fun matchDetailTeamTag(side: String): String = "match-detail-team-$side"
internal fun matchDetailTeamImageTag(side: String): String = "match-detail-team-image-$side"
internal fun matchDetailTeamImagePlaceholderTag(side: String): String = "match-detail-team-image-placeholder-$side"
internal fun matchDetailMapTag(name: String): String = "match-detail-map-$name"
internal fun matchDetailHeadToHeadTag(matchId: String): String = "match-detail-head-to-head-$matchId"

@Composable
fun MatchDetailContent(
    uiState: MatchDetailUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { MatchDetailTopBar(onBack) },
    ) { contentPadding ->
        when (val state = uiState.contentState) {
            MatchDetailContentState.Loading -> MatchDetailLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is MatchDetailContentState.Content -> MatchDetailBody(
                match = state.match,
                listState = listState,
                onEventClick = onEventClick,
                onTeamClick = onTeamClick,
                onMatchClick = onMatchClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            MatchDetailContentState.Error -> MatchDetailError(
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }
}

@Composable
private fun MatchDetailTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(VlrTheme.colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = VlrDimensions.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlrIconButton(
                contentDescription = "뒤로 가기",
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_arrow_back),
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

@Composable
private fun MatchDetailLoading(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.testTag(MATCH_DETAIL_LOADING_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = VlrDimensions.Space4,
            vertical = VlrDimensions.Space6,
        ),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        item(key = "loading-hero") {
            Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3)) {
                SkeletonBlock(88.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonCircle()
                    SkeletonBlock(28.dp, Modifier.width(72.dp))
                    SkeletonCircle()
                }
                SkeletonBlock(20.dp, Modifier.fillMaxWidth(0.7f))
            }
        }
        item(key = "loading-maps") { LoadingSection("Maps", rowCount = 2) }
        item(key = "loading-head-to-head") { LoadingSection("Head to Head", rowCount = 3) }
    }
}

@Composable
private fun LoadingSection(title: String, rowCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
        Text(
            text = title,
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textPrimary,
        )
        repeat(rowCount) { SkeletonBlock(VlrDimensions.MinimumTouchTarget) }
    }
}

@Composable
private fun SkeletonCircle() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(VlrTheme.colors.skeleton),
    )
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(VlrDimensions.DefaultCornerRadius))
            .background(VlrTheme.colors.skeleton),
    )
}

@Composable
private fun MatchDetailBody(
    match: MatchDetail,
    listState: LazyListState,
    onEventClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = VlrDimensions.Space6),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space8),
    ) {
        item(key = "hero") {
            MatchHero(
                match = match,
                onEventClick = onEventClick,
                onTeamClick = onTeamClick,
            )
        }
        item(key = "maps") {
            MapsSection(match.maps, match.status)
        }
        item(key = "head-to-head") {
            HeadToHeadSection(match.headToHead, match.status, onMatchClick)
        }
    }
}

@Composable
private fun MatchHero(
    match: MatchDetail,
    onEventClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_DETAIL_HERO_TAG)
            .padding(horizontal = VlrDimensions.Space4),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        StatusChip(
            status = match.status.toStatusChipStatus(),
            label = match.status.displayLabel(),
        )
        EventIdentity(match.event, onEventClick)
        Text(
            text = match.timeLabel,
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        listOfNotNull(match.relativeTimeLabel, match.scheduledAt, match.seriesFormat)
            .filter(String::isNotBlank)
            .takeIf { it.isNotEmpty() }
            ?.let { metadata ->
                Text(
                    text = metadata.joinToString(" · "),
                    style = VlrTheme.typography.label,
                    color = VlrTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        match.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(
                text = description,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamIdentity(
                team = match.homeTeam,
                side = "home",
                onTeamClick = onTeamClick,
                modifier = Modifier.weight(1f),
            )
            MatchScore(match.homeScore, match.awayScore)
            TeamIdentity(
                team = match.awayTeam,
                side = "away",
                onTeamClick = onTeamClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (match.status.isLimitedInformation()) {
            Text(
                text = "이 경기는 제한된 정보만 제공됩니다",
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        VlrDimensions.OutlineWidth,
                        VlrTheme.colors.outline,
                        androidx.compose.foundation.shape.RoundedCornerShape(VlrDimensions.DefaultCornerRadius),
                    )
                    .padding(VlrDimensions.Space3),
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun EventIdentity(event: MatchEvent, onEventClick: (String) -> Unit) {
    val clickModifier = event.id?.let { eventId ->
        Modifier
            .semantics { contentDescription = "이벤트 상세: ${event.name}" }
            .clickable(role = Role.Button) { onEventClick(eventId) }
    } ?: Modifier
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(MATCH_DETAIL_EVENT_TAG)
            .then(clickModifier),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = event.name,
            style = VlrTheme.typography.pageTitle,
            color = VlrTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        event.series?.takeIf(String::isNotBlank)?.let { series ->
            Text(
                text = series,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TeamIdentity(
    team: MatchTeam,
    side: String,
    onTeamClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrl = team.imageUrl?.takeIf(String::isNotBlank)
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val clickModifier = team.id?.let { teamId ->
        Modifier
            .semantics { contentDescription = "팀 상세: ${team.name}" }
            .clickable(role = Role.Button) { onTeamClick(teamId) }
    } ?: Modifier
    Column(
        modifier = modifier
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(matchDetailTeamTag(side))
            .then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(VlrTheme.colors.surfaceSubtle)
                .border(
                    VlrDimensions.OutlineWidth,
                    VlrTheme.colors.outline,
                    androidx.compose.foundation.shape.CircleShape,
                ),
        ) {
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(matchDetailTeamImageTag(side)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(matchDetailTeamImagePlaceholderTag(side)),
                )
            }
        }
        Text(
            text = team.name,
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MatchScore(homeScore: Int?, awayScore: Int?) {
    val score = if (homeScore == null && awayScore == null) {
        "VS"
    } else {
        "${homeScore.scoreText()} - ${awayScore.scoreText()}"
    }
    Text(
        text = score,
        modifier = Modifier.width(72.dp),
        style = VlrTheme.typography.display,
        color = VlrTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MapsSection(maps: List<MatchMap>, status: MatchStatus) {
    DetailSection(
        title = "Maps",
        tag = MATCH_DETAIL_MAPS_SECTION_TAG,
    ) {
        if (maps.isEmpty()) {
            SectionEmpty(
                message = if (status.isLimitedInformation()) {
                    "이 경기의 맵 정보는 제공되지 않습니다"
                } else {
                    "맵 정보가 없습니다"
                },
            )
        } else {
            maps.forEach { map -> MatchMapRow(map) }
        }
    }
}

@Composable
private fun HeadToHeadSection(
    matches: List<RelatedMatch>,
    status: MatchStatus,
    onMatchClick: (String) -> Unit,
) {
    DetailSection(
        title = "Head to Head",
        tag = MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG,
    ) {
        if (matches.isEmpty()) {
            SectionEmpty(
                message = if (status.isLimitedInformation()) {
                    "이 경기의 상대 전적은 제공되지 않습니다"
                } else {
                    "상대 전적이 없습니다"
                },
            )
        } else {
            matches.forEach { match -> RelatedMatchRow(match, onMatchClick) }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    tag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .padding(horizontal = VlrDimensions.Space4),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        Text(
            text = title,
            style = VlrTheme.typography.sectionTitle,
            color = VlrTheme.colors.textPrimary,
        )
        content()
    }
}

@Composable
private fun MatchMapRow(map: MatchMap) {
    FlatOutlinedRow(modifier = Modifier.testTag(matchDetailMapTag(map.name))) {
        Text(
            text = map.name,
            modifier = Modifier.weight(1f),
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${map.homeScore.scoreText()} - ${map.awayScore.scoreText()}",
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun RelatedMatchRow(match: RelatedMatch, onMatchClick: (String) -> Unit) {
    FlatOutlinedRow(
        modifier = Modifier
            .testTag(matchDetailHeadToHeadTag(match.id))
            .semantics { contentDescription = "경기 상세: ${match.homeTeamName} 대 ${match.awayTeamName}" }
            .clickable(role = Role.Button) { onMatchClick(match.id) },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.homeTeamName,
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = match.awayTeamName,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${match.homeScore.scoreText()} - ${match.awayScore.scoreText()}",
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun FlatOutlinedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .border(
                VlrDimensions.OutlineWidth,
                VlrTheme.colors.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(VlrDimensions.DefaultCornerRadius),
            )
            .padding(horizontal = VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        content = content,
    )
}

@Composable
private fun SectionEmpty(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .border(
                VlrDimensions.OutlineWidth,
                VlrTheme.colors.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(VlrDimensions.DefaultCornerRadius),
            )
            .padding(horizontal = VlrDimensions.Space3),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = message,
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun MatchDetailError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = VlrDimensions.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "경기 정보를 불러오지 못했습니다",
            style = VlrTheme.typography.pageTitle,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VlrDimensions.Space2))
        Text(
            text = "잠시 후 다시 시도해 주세요.",
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VlrDimensions.Space6))
        VlrButton(
            text = "재시도",
            onClick = onRetry,
            variant = VlrButtonVariant.Primary,
        )
    }
}

private fun Int?.scoreText(): String = this?.toString() ?: "—"

private fun MatchStatus.toStatusChipStatus(): StatusChipStatus = when (this) {
    MatchStatus.UPCOMING -> StatusChipStatus.Upcoming
    MatchStatus.LIVE -> StatusChipStatus.Live
    MatchStatus.COMPLETED -> StatusChipStatus.Completed
    MatchStatus.POSTPONED -> StatusChipStatus.Postponed
    MatchStatus.CANCELLED -> StatusChipStatus.Cancelled
    MatchStatus.UNAVAILABLE -> StatusChipStatus.Unavailable
}

private fun MatchStatus.displayLabel(): String = when (this) {
    MatchStatus.UPCOMING -> "예정"
    MatchStatus.LIVE -> "LIVE"
    MatchStatus.COMPLETED -> "완료됨"
    MatchStatus.POSTPONED -> "연기됨"
    MatchStatus.CANCELLED -> "취소됨"
    MatchStatus.UNAVAILABLE -> "정보 없음"
}

private fun MatchStatus.isLimitedInformation(): Boolean =
    this == MatchStatus.CANCELLED || this == MatchStatus.UNAVAILABLE

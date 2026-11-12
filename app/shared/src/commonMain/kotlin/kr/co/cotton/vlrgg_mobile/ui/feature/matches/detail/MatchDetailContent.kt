package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kr.co.cotton.vlrgg_mobile.ui.component.DetailTopBand
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotSurface
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials
import kr.co.cotton.vlrgg_mobile.ui.component.rememberBusyRetryCooldown
import kr.co.cotton.vlrgg_mobile.ui.component.rowDivider
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_chevron_right
import vlrggmobile.app.shared.generated.resources.ic_info

internal const val MATCH_DETAIL_LOADING_TAG = "match-detail-loading"
internal const val MATCH_DETAIL_HERO_TAG = "match-detail-hero"
internal const val MATCH_DETAIL_EVENT_TAG = "match-detail-event"
internal const val MATCH_DETAIL_SCORE_TAG = "match-detail-score"
internal const val MATCH_DETAIL_DESCRIPTION_TAG = "match-detail-description"
internal const val MATCH_DETAIL_MAPS_SECTION_TAG = "match-detail-maps"
internal const val MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG = "match-detail-head-to-head"

internal fun matchDetailTeamTag(side: String): String = "match-detail-team-$side"
internal fun matchDetailTeamImageTag(side: String): String = "match-detail-team-image-$side"
internal fun matchDetailTeamImagePlaceholderTag(side: String): String = "match-detail-team-image-placeholder-$side"
internal fun matchDetailScoreTag(side: String): String = "$MATCH_DETAIL_SCORE_TAG-$side"
internal fun matchDetailMapTag(name: String): String = "match-detail-map-$name"
internal fun matchDetailMapWinBarTag(name: String, side: String): String = "match-detail-map-$name-win-$side"
internal fun matchDetailHeadToHeadTag(matchId: String): String = "match-detail-head-to-head-$matchId"

internal const val MatchDetailVsLabel = "VS"
internal const val MatchDetailLimitedNotice = "이 경기는 제한된 정보만 제공됩니다"

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
    val isBusyCooldownActive = rememberBusyRetryCooldown(uiState.busyRetry)
    Scaffold(
        modifier = modifier,
        // 로딩·본문은 섹션 사이와 아래 여백이 `canvas`이고, 오류 State panel만 흰 바탕이다(`.pen` 21-1·21-6·21-7).
        containerColor = if (uiState.contentState == MatchDetailContentState.Error) {
            VlrTheme.colors.surface
        } else {
            VlrTheme.colors.canvas
        },
        topBar = { DetailTopBand(onBack = onBack) },
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

            MatchDetailContentState.Error -> if (uiState.busyRetry?.isDialogVisible == true || isBusyCooldownActive) {
                Box(Modifier.fillMaxSize().padding(contentPadding))
            } else {
                MatchDetailError(
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }
        }
    }
}

/** `.pen` 21-6: the dark scoreboard skeleton under the fixed band, then text-row skeletons on `canvas`. */
@Composable
private fun MatchDetailLoading(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .background(VlrTheme.colors.canvas)
            .testTag(MATCH_DETAIL_LOADING_TAG),
    ) {
        item(key = "loading-hero") { ScoreboardSkeleton() }
        item(key = "loading-rows") {
            Column(Modifier.fillMaxWidth().background(VlrTheme.colors.surface)) {
                repeat(4) { SkeletonRow() }
            }
        }
    }
}

@Composable
private fun ScoreboardSkeleton() {
    val block = VlrTheme.colors.panel2
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.panel)
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space4, top = VlrDimensions.Space2, bottom = VlrDimensions.Space6)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SkeletonBlock(Modifier.size(width = 160.dp, height = 12.dp), color = block)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(Modifier.size(ScoreboardLogoSize), color = block)
            SkeletonBlock(Modifier.size(width = 72.dp, height = 40.dp), color = block)
            SkeletonBlock(Modifier.size(ScoreboardLogoSize), color = block)
        }
        SkeletonBlock(Modifier.size(width = 200.dp, height = 12.dp), color = block)
    }
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
        modifier = modifier.background(VlrTheme.colors.canvas),
        contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
    ) {
        item(key = "hero") {
            Scoreboard(
                match = match,
                onEventClick = onEventClick,
                onTeamClick = onTeamClick,
            )
        }
        if (match.status.isLimitedInformation()) {
            sectionItem("limited") { LimitedNotice() }
        }
        match.description?.takeIf(String::isNotBlank)?.let { description ->
            sectionItem("description") { DescriptionSection(description) }
        }
        sectionItem("maps") { MapsSection(match) }
        sectionItem("head-to-head") { HeadToHeadSection(match.headToHead, match.status, onMatchClick) }
    }
}

/** White sections after the hero are separated by 8dp `canvas` (`DESIGN.md` 3). */
private fun LazyListScope.sectionItem(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.fillMaxWidth().padding(top = VlrDimensions.Space2).background(VlrTheme.colors.surface)) {
            content()
        }
    }
}

/**
 * Scoreboard hero (`DESIGN.md` 8.3, `.pen` `C / Scoreboard`): event link, symmetric 100dp team
 * columns with the score or `VS` and the status chip between them, then the schedule.
 */
@Composable
private fun Scoreboard(
    match: MatchDetail,
    onEventClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val winner = match.scoreboardWinner()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.panel)
            .testTag(MATCH_DETAIL_HERO_TAG)
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space4, bottom = VlrDimensions.Space5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EventLink(match.event, onEventClick)
        Spacer(Modifier.height(6.dp))
        // 위 정렬: 한쪽 팀 이름이 두 줄이어도 두 로고와 점수 높이가 어긋나지 않는다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ScoreboardTeam(
                team = match.homeTeam,
                side = HomeSide,
                isLoser = winner == MatchSide.Away,
                onTeamClick = onTeamClick,
            )
            ScoreboardResult(match, winner, Modifier.weight(1f))
            ScoreboardTeam(
                team = match.awayTeam,
                side = AwaySide,
                isLoser = winner == MatchSide.Home,
                onTeamClick = onTeamClick,
            )
        }
        Spacer(Modifier.height(VlrDimensions.Space4))
        match.scheduleLines(timeZone).forEach { line ->
            Text(
                text = line,
                modifier = Modifier.fillMaxWidth(),
                style = VlrTheme.typography.meta,
                color = VlrTheme.colors.onPanelMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 13/600 `on-panel-muted` event context; the chevron only appears when the event can open. */
@Composable
private fun EventLink(event: MatchEvent, onEventClick: (String) -> Unit) {
    val clickModifier = event.id?.let { eventId ->
        Modifier
            .semantics { contentDescription = "이벤트 상세: ${event.name}" }
            .clickable(role = Role.Button) { onEventClick(eventId) }
    } ?: Modifier
    Row(
        modifier = Modifier
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(MATCH_DETAIL_EVENT_TAG)
            .then(clickModifier),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space1, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(event.name, event.series?.takeIf(String::isNotBlank)).joinToString(" · "),
            modifier = Modifier.weight(1f, fill = false),
            style = VlrTheme.typography.meta.copy(fontWeight = FontWeight.SemiBold),
            color = VlrTheme.colors.onPanelMuted,
            textAlign = TextAlign.Center,
        )
        if (event.id != null) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = VlrTheme.colors.onPanelMuted,
            )
        }
    }
}

@Composable
private fun ScoreboardTeam(
    team: MatchTeam,
    side: String,
    isLoser: Boolean,
    onTeamClick: (String) -> Unit,
) {
    val clickModifier = team.id?.let { teamId ->
        Modifier
            .semantics { contentDescription = "팀 상세: ${team.name}" }
            .clickable(role = Role.Button) { onTeamClick(teamId) }
    } ?: Modifier
    Column(
        modifier = Modifier
            .width(ScoreboardTeamWidth)
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .testTag(matchDetailTeamTag(side))
            .then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        LogoSlot(
            imageUrl = team.imageUrl,
            kind = LogoSlotKind.Team,
            placeholderText = logoInitials(team.name),
            size = ScoreboardLogoSize,
            surface = LogoSlotSurface.Dark,
            imageTestTag = matchDetailTeamImageTag(side),
            placeholderTestTag = matchDetailTeamImagePlaceholderTag(side),
        )
        Text(
            text = team.name,
            style = VlrTheme.typography.bodySm.copy(fontWeight = FontWeight.Bold),
            color = if (isLoser) VlrTheme.colors.onPanelMuted else VlrTheme.colors.onPanel,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScoreboardResult(match: MatchDetail, winner: MatchSide?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (match.showsScoreboardScore()) {
            Row(
                modifier = Modifier
                    .testTag(MATCH_DETAIL_SCORE_TAG)
                    .semantics(mergeDescendants = true) { contentDescription = match.scoreAccessibilityLabel() },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreboardScore(match.homeScore, isLoser = winner == MatchSide.Away, MatchSide.Home)
                Text(
                    text = ":",
                    style = VlrTheme.typography.scoreXl.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                    color = VlrTheme.colors.onPanelMuted,
                )
                ScoreboardScore(match.awayScore, isLoser = winner == MatchSide.Home, MatchSide.Away)
            }
        } else {
            Text(
                text = MatchDetailVsLabel,
                modifier = Modifier.testTag(MATCH_DETAIL_SCORE_TAG),
                style = VlrTheme.typography.scoreXl.copy(fontSize = 36.sp),
                color = VlrTheme.colors.onPanelMuted,
            )
        }
        StatusChip(
            status = match.status.toStatusChipStatus(),
            label = match.status.displayLabel(),
        )
    }
}

@Composable
private fun ScoreboardScore(score: Int?, isLoser: Boolean, side: MatchSide) {
    Text(
        text = score.scoreText(),
        modifier = Modifier.testTag(matchDetailScoreTag(side.tag)),
        style = VlrTheme.typography.scoreXl.copy(lineHeight = 1.em),
        color = if (isLoser || score == null) VlrTheme.colors.onPanelMuted else VlrTheme.colors.onPanel,
    )
}

@Composable
private fun LimitedNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VlrDimensions.Space4, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_info),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = VlrTheme.colors.muted,
        )
        Text(
            text = MatchDetailLimitedNotice,
            modifier = Modifier.weight(1f),
            style = VlrTheme.typography.meta,
            color = VlrTheme.colors.ink2,
        )
    }
}

/** Upstream description shown as is, under a `경기 설명` label (`.pen` 21-1). */
@Composable
private fun DescriptionSection(description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MATCH_DETAIL_DESCRIPTION_TAG)
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space4, top = VlrDimensions.Space4, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "경기 설명",
            style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Bold),
            color = VlrTheme.colors.muted,
        )
        Text(
            text = description,
            style = VlrTheme.typography.meta.copy(lineHeight = 1.6.em),
            color = VlrTheme.colors.ink2,
        )
    }
}

@Composable
private fun MapsSection(match: MatchDetail) {
    val maps = match.maps
    Column(Modifier.fillMaxWidth().testTag(MATCH_DETAIL_MAPS_SECTION_TAG)) {
        SectionHeader(title = "맵", count = maps.size.takeIf { it > 0 }?.toString())
        if (maps.isEmpty()) {
            SectionMessage(
                message = if (match.status.isLimitedInformation()) {
                    "이 경기의 맵 정보는 제공되지 않습니다"
                } else {
                    "맵 정보가 없습니다"
                },
            )
        } else {
            MapLegend(match.homeTeam.name, match.awayTeam.name)
            maps.forEachIndexed { index, map ->
                MapRow(
                    number = index + 1,
                    map = map,
                    homeTeamName = match.homeTeam.name,
                    awayTeamName = match.awayTeam.name,
                    winner = map.winner(match.status),
                )
            }
        }
    }
}

/** Which side each map score column belongs to. The list response has no team tag, so it names the teams. */
@Composable
private fun MapLegend(homeTeamName: String, awayTeamName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rowDivider(VlrTheme.colors.line)
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space4, bottom = 6.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        val style = VlrTheme.typography.meta.copy(fontWeight = FontWeight.Bold)
        Text(homeTeamName, modifier = Modifier.weight(1f), style = style, color = VlrTheme.colors.muted)
        Text(
            awayTeamName,
            modifier = Modifier.weight(1f),
            style = style,
            color = VlrTheme.colors.muted,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Map row (`DESIGN.md` 8.3): 56dp score columns around `MAP n` and the map name. The winner's
 * score is `ink` with a 16×2dp `accent` bar under it; a map still in play has no winner.
 */
@Composable
private fun MapRow(
    number: Int,
    map: MatchMap,
    homeTeamName: String,
    awayTeamName: String,
    winner: MatchSide?,
) {
    val mapLabel = "MAP $number"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rowDivider(VlrTheme.colors.line)
            .testTag(matchDetailMapTag(map.name))
            .semantics(mergeDescendants = true) {
                contentDescription = "$mapLabel ${map.name}, $homeTeamName ${map.homeScore.scoreText()}, " +
                    "$awayTeamName ${map.awayScore.scoreText()}"
            }
            .padding(horizontal = VlrDimensions.Space4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MapScore(map.homeScore, winner, MatchSide.Home, map.name, Alignment.Start)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = mapLabel,
                style = VlrTheme.typography.tag.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = VlrTheme.colors.muted,
            )
            Text(
                text = map.name,
                style = VlrTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = VlrTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
        }
        MapScore(map.awayScore, winner, MatchSide.Away, map.name, Alignment.End)
    }
}

@Composable
private fun MapScore(
    score: Int?,
    winner: MatchSide?,
    side: MatchSide,
    mapName: String,
    alignment: Alignment.Horizontal,
) {
    val isLoser = winner != null && winner != side
    Column(
        modifier = Modifier.width(MapScoreColumnWidth),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = score.scoreText(),
            style = VlrTheme.typography.scoreL,
            color = if (isLoser || score == null) VlrTheme.colors.muted else VlrTheme.colors.ink,
        )
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 2.dp)
                .then(
                    if (winner == side) {
                        Modifier
                            .background(VlrTheme.colors.accent)
                            .testTag(matchDetailMapWinBarTag(mapName, side.tag))
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun HeadToHeadSection(
    matches: List<RelatedMatch>,
    status: MatchStatus,
    onMatchClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag(MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG)) {
        SectionHeader(title = "상대 전적", count = matches.size.takeIf { it > 0 }?.toString())
        if (matches.isEmpty()) {
            SectionMessage(
                message = if (status.isLimitedInformation()) {
                    "이 경기의 상대 전적은 제공되지 않습니다"
                } else {
                    "상대 전적이 없습니다"
                },
            )
        } else {
            matches.forEach { match -> HeadToHeadRow(match, onMatchClick) }
        }
    }
}

/**
 * Head to head row (`DESIGN.md` 8.3): team A right-aligned · Saira 18 score · team B, and the
 * whole row opens that match. The winner is `ink` 700 and the loser `muted` 500.
 */
@Composable
private fun HeadToHeadRow(match: RelatedMatch, onMatchClick: (String) -> Unit) {
    val winner = winnerOf(match.homeScore, match.awayScore)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .rowDivider(VlrTheme.colors.line)
            .testTag(matchDetailHeadToHeadTag(match.id))
            .semantics { contentDescription = match.accessibilityLabel() }
            .clickable(role = Role.Button) { onMatchClick(match.id) }
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space1, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadToHeadTeam(match.homeTeamName, isLoser = winner == MatchSide.Away, Modifier.weight(1f), TextAlign.End)
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeadToHeadScore(match.homeScore, isLoser = winner == MatchSide.Away)
            Text(
                text = ":",
                style = VlrTheme.typography.tag.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = VlrTheme.colors.muted,
            )
            HeadToHeadScore(match.awayScore, isLoser = winner == MatchSide.Home)
        }
        HeadToHeadTeam(match.awayTeamName, isLoser = winner == MatchSide.Home, Modifier.weight(1f), TextAlign.Start)
        Box(Modifier.size(VlrDimensions.MinimumTouchTarget), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = VlrTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun HeadToHeadTeam(name: String, isLoser: Boolean, modifier: Modifier, textAlign: TextAlign) {
    Text(
        text = name,
        modifier = modifier,
        style = VlrTheme.typography.bodySm.copy(fontWeight = if (isLoser) FontWeight.Medium else FontWeight.Bold),
        color = if (isLoser) VlrTheme.colors.muted else VlrTheme.colors.ink,
        textAlign = textAlign,
    )
}

@Composable
private fun HeadToHeadScore(score: Int?, isLoser: Boolean) {
    Text(
        text = score.scoreText(),
        style = VlrTheme.typography.scoreM.copy(fontSize = 18.sp, lineHeight = 28.sp),
        color = if (isLoser || score == null) VlrTheme.colors.muted else VlrTheme.colors.ink,
    )
}

@Composable
private fun MatchDetailError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StatePanel(
        title = "경기 정보를 불러오지 못했습니다",
        body = "잠시 후 다시 시도해 주세요.",
        actionLabel = "재시도",
        onAction = onRetry,
        modifier = modifier,
    )
}

internal enum class MatchSide(val tag: String) {
    Home(HomeSide),
    Away(AwaySide),
}

private const val HomeSide = "home"
private const val AwaySide = "away"
private val ScoreboardTeamWidth = 100.dp
private val ScoreboardLogoSize = 56.dp
private val MapScoreColumnWidth = 56.dp

/**
 * `scheduledAtUtc`가 있으면 [timeZone] 기준으로 포맷하고, 없을 때만 source label을 쓴다. ISO 원문은 표시하지 않는다.
 * 구서버의 `scheduledAt`은 형식만 정상이고 값이 틀려서 사용하지 않는다.
 */
internal fun MatchDetail.scheduleLabel(timeZone: TimeZone): String =
    scheduledAtUtc?.let { scheduledAtUtc -> KoreanDateTimeFormat.dateTimeWithWeekday(scheduledAtUtc, timeZone) } ?: timeLabel

/** 남은 시간은 예정 경기에만 표시한다. */
internal fun MatchDetail.remainingTimeLabel(): String? =
    relativeTimeLabel?.takeIf { status == MatchStatus.UPCOMING }?.let(KoreanDateTimeFormat::remainingTime)

/**
 * Hero 일정 줄. 남은 시간이 있는 예정 경기는 `일정`과 `남은 시간 · Bo3` 두 줄이고, 그 외는 `일정 · Bo3` 한 줄이다.
 */
internal fun MatchDetail.scheduleLines(timeZone: TimeZone): List<String> {
    val format = seriesFormat?.takeIf(String::isNotBlank)
    val schedule = scheduleLabel(timeZone).takeIf(String::isNotBlank)
    val remaining = remainingTimeLabel()
    return if (remaining != null) {
        listOfNotNull(schedule, listOfNotNull(remaining, format).joinToString(" · "))
    } else {
        listOfNotNull(listOfNotNull(schedule, format).joinToString(" · ").takeIf(String::isNotEmpty))
    }
}

/** LIVE·종료 경기만 점수를 둔다. 예정·연기·취소·정보 없음과 점수가 없는 경기는 `VS`다. */
internal fun MatchDetail.showsScoreboardScore(): Boolean =
    (status == MatchStatus.LIVE || status == MatchStatus.COMPLETED) && (homeScore != null || awayScore != null)

/** 승자는 두 점수가 다른 종료 경기에서만 정한다. */
internal fun MatchDetail.scoreboardWinner(): MatchSide? =
    if (status == MatchStatus.COMPLETED) winnerOf(homeScore, awayScore) else null

/** `KIWOOM DRX 2, Paper Rex 1`. 결측 점수는 `—`로 읽는다. */
internal fun MatchDetail.scoreAccessibilityLabel(): String =
    "${homeTeam.name} ${homeScore.scoreText()}, ${awayTeam.name} ${awayScore.scoreText()}"

/**
 * 맵 승자. 종료 경기는 점수가 높은 쪽이다. LIVE 경기는 13점 이상이면서 2점 차 이상 앞선 맵만 끝난 맵으로 보고,
 * 진행 중인 맵은 승자를 표시하지 않는다.
 */
internal fun MatchMap.winner(matchStatus: MatchStatus): MatchSide? {
    val side = winnerOf(homeScore, awayScore) ?: return null
    return when (matchStatus) {
        MatchStatus.COMPLETED -> side
        MatchStatus.LIVE -> {
            val home = homeScore ?: return null
            val away = awayScore ?: return null
            side.takeIf { maxOf(home, away) >= MapWinningRounds && kotlin.math.abs(home - away) >= 2 }
        }
        else -> null
    }
}

private const val MapWinningRounds = 13

internal fun RelatedMatch.accessibilityLabel(): String {
    val home = homeScore
    val away = awayScore
    return if (home != null && away != null) {
        "경기 상세: $homeTeamName $home, $awayTeamName $away"
    } else {
        "경기 상세: $homeTeamName 대 $awayTeamName"
    }
}

private fun winnerOf(homeScore: Int?, awayScore: Int?): MatchSide? {
    val home = homeScore ?: return null
    val away = awayScore ?: return null
    return when {
        home > away -> MatchSide.Home
        away > home -> MatchSide.Away
        else -> null
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

@Preview(showBackground = true, widthDp = 360, heightDp = 965)
@Composable
private fun MatchDetailCompletedPreview() {
    VlrTheme {
        MatchDetailContent(
            uiState = MatchDetailUiState(MatchDetailContentState.Content(PreviewMatch)),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            onBack = {},
            onEventClick = {},
            onTeamClick = {},
            onMatchClick = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 650)
@Composable
private fun MatchDetailUpcomingPreview() {
    VlrTheme {
        MatchDetailContent(
            uiState = MatchDetailUiState(
                MatchDetailContentState.Content(
                    PreviewMatch.copy(
                        status = MatchStatus.UPCOMING,
                        relativeTimeLabel = "2d 2h",
                        homeScore = null,
                        awayScore = null,
                        description = null,
                        maps = emptyList(),
                    ),
                ),
            ),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            onBack = {},
            onEventClick = {},
            onTeamClick = {},
            onMatchClick = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun MatchDetailLoadingPreview() {
    VlrTheme {
        MatchDetailContent(
            uiState = MatchDetailUiState(),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            onBack = {},
            onEventClick = {},
            onTeamClick = {},
            onMatchClick = {},
            onRetry = {},
        )
    }
}

private val PreviewMatch = MatchDetail(
    id = "675209",
    status = MatchStatus.COMPLETED,
    timeLabel = "Monday, September 21 6:00 PM KST",
    relativeTimeLabel = null,
    scheduledAtUtc = kotlin.time.Instant.parse("2026-09-21T09:00:00Z"),
    homeTeam = MatchTeam(name = "KIWOOM DRX", id = "8185"),
    awayTeam = MatchTeam(name = "Paper Rex", id = "624"),
    homeScore = 2,
    awayScore = 1,
    event = MatchEvent(name = "VCT Pacific 2026", series = "Playoffs: Upper Final", id = "2775"),
    description = "DRX ban Split; PRX ban Bind; DRX pick Haven; PRX pick Ascent; DRX ban Icebox; PRX ban Sunset; Lotus remains",
    seriesFormat = "Bo3",
    maps = listOf(
        MatchMap("Haven", 13, 8),
        MatchMap("Ascent", 10, 13),
        MatchMap("Lotus", 13, 8),
    ),
    headToHead = listOf(
        RelatedMatch("1", "KIWOOM DRX", "Paper Rex", 2, 0),
        RelatedMatch("2", "Paper Rex", "KIWOOM DRX", 1, 2),
        RelatedMatch("3", "KIWOOM DRX", "Paper Rex", 1, 2),
    ),
    pastMatches = emptyList(),
)

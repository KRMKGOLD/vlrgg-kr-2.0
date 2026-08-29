package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_match
import vlrggmobile.app.shared.generated.resources.ic_person

internal const val PLAYER_DETAIL_LOADING_TAG = "player-detail-loading"
internal const val PLAYER_DETAIL_HEADER_TAG = "player-detail-header"
internal const val PLAYER_DETAIL_TEAM_SECTION_TAG = "player-detail-team-section"
internal const val PLAYER_DETAIL_STATS_SECTION_TAG = "player-detail-stats-section"
internal const val PLAYER_DETAIL_MATCHES_SECTION_TAG = "player-detail-matches-section"
internal const val PLAYER_DETAIL_LOADING_HEADER_AVATAR_TAG = "player-detail-loading-header-avatar"
internal fun playerTeamRowTag(teamId: String) = "player-team-$teamId"
internal fun playerMatchCardTag(matchId: String) = "player-match-$matchId"

@Composable
fun PlayerDetailContent(
    uiState: PlayerDetailUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = { PlayerDetailTopBar(onBack) },
    ) { padding ->
        when (val state = uiState.contentState) {
            PlayerDetailContentState.Loading -> PlayerDetailLoading(
                listState,
                Modifier.fillMaxSize().padding(padding),
            )
            is PlayerDetailContentState.Content -> PlayerDetailBody(
                state.player, listState, onTeamClick, onMatchClick,
                Modifier.fillMaxSize().padding(padding),
            )
            PlayerDetailContentState.Error -> Box(Modifier.fillMaxSize().padding(padding))
        }
    }
    if (uiState.contentState == PlayerDetailContentState.Error) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("정보를 불러오지 못했습니다") },
            text = { Text("네트워크 상태를 확인하고 다시 시도해 주세요.") },
            confirmButton = { VlrButton("재시도", onClick = onRetry) },
            dismissButton = { VlrButton("뒤로가기", variant = VlrButtonVariant.Secondary, onClick = onBack) },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )
    }
}

@Composable
private fun PlayerDetailTopBar(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(56.dp).background(VlrTheme.colors.surface)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            VlrIconButton(
                contentDescription = "뒤로 가기", onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = VlrDimensions.Space1),
                icon = { Icon(vectorResource(Res.drawable.ic_arrow_back), null) },
            )
            Text("Player Profile", Modifier.align(Alignment.Center), style = VlrTheme.typography.pageTitle, color = VlrTheme.colors.textPrimary)
        }
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
    }
}

@Composable
private fun PlayerDetailBody(
    player: PlayerDetail,
    listState: LazyListState,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    modifier: Modifier,
) = LazyColumn(state = listState, modifier = modifier) {
    item("header") { PlayerHeader(player) }
    divider("header-divider")
    item("team") { CurrentTeamSection(player, onTeamClick) }
    divider("team-divider")
    item("stats") { AgentStatsSection(player.agentStats) }
    divider("stats-divider")
    item("matches") { RecentMatchesSection(player.recentMatches, onMatchClick) }
    item("bottom-space") { Spacer(Modifier.height(VlrDimensions.Space8)) }
}

private fun LazyListScope.divider(key: String) = item(key) {
    HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
}

@Composable
private fun PlayerHeader(player: PlayerDetail) {
    val profile = player.profile
    val country = listOfNotNull(
        profile.countryName?.takeIf(String::isNotBlank),
        profile.countryCode?.takeIf(String::isNotBlank)?.uppercase(),
    ).distinct().joinToString(" · ")
    val metadata = listOfNotNull(
        profile.realName?.takeIf(String::isNotBlank),
        country.takeIf(String::isNotEmpty),
    ).joinToString(" · ")
    Column(
        Modifier.fillMaxWidth().testTag(PLAYER_DETAIL_HEADER_TAG).padding(VlrDimensions.Space4).semantics { contentDescription = "선수: ${profile.handle}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(96.dp).width(96.dp).clip(CircleShape).background(VlrTheme.colors.surfaceSubtle), contentAlignment = Alignment.Center) {
            Text(profile.handle.stablePlaceholder(), style = VlrTheme.typography.display, color = VlrTheme.colors.textBrand)
        }
        Spacer(Modifier.height(VlrDimensions.Space3))
        Text(profile.handle, style = VlrTheme.typography.display, color = VlrTheme.colors.textPrimary, textAlign = TextAlign.Center)
        if (metadata.isNotEmpty()) Text(metadata, style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (profile.aliases.isNotEmpty()) Text(profile.aliases.joinToString(" · "), style = VlrTheme.typography.labelSmall, color = VlrTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

private fun String.stablePlaceholder(): String = trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

@Composable
private fun CurrentTeamSection(player: PlayerDetail, onTeamClick: (String) -> Unit) {
    Section(PLAYER_DETAIL_TEAM_SECTION_TAG, "현재 소속 팀") {
        player.currentTeam?.let { team ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(playerTeamRowTag(team.id)).semantics { contentDescription = "팀 상세: ${team.name}" }
                    .clickable(role = Role.Button) { onTeamClick(team.id) }.padding(vertical = VlrDimensions.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(team.name, Modifier.weight(1f), style = VlrTheme.typography.bodyStrong, color = VlrTheme.colors.textPrimary)
            }
        } ?: SectionEmpty("소속 팀 정보가 없습니다", Res.drawable.ic_person)
    }
}

@Composable
private fun AgentStatsSection(stats: List<PlayerAgentStat>) {
    Section(PLAYER_DETAIL_STATS_SECTION_TAG, "에이전트 통계") {
        if (stats.isEmpty()) {
            SectionEmpty("에이전트 통계 정보가 없습니다")
        } else {
            val scroll = rememberScrollState()
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(112.dp)) {
                    MetricText("Agent", true)
                    stats.forEach { MetricText(it.agentName, false) }
                }
                Row(Modifier.horizontalScroll(scroll)) {
                    StatsColumn("Maps", stats) { it.mapsPlayed.toString() }
                    StatsColumn("Pick Rate", stats) { it.pickRatePercent?.let { value -> "$value%" } ?: "—" }
                    StatsColumn("Rating", stats) { it.rating?.toString() ?: "—" }
                    StatsColumn("ACS", stats) { it.averageCombatScore?.toString() ?: "—" }
                    StatsColumn("K/D", stats) { it.killDeathRatio?.toString() ?: "—" }
                    StatsColumn("KAST", stats) { it.kastPercent?.let { value -> "$value%" } ?: "—" }
                    StatsColumn("ADR", stats) { it.averageDamagePerRound?.toString() ?: "—" }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatsColumn(
    title: String,
    stats: List<PlayerAgentStat>,
    value: (PlayerAgentStat) -> String,
) {
    Column(Modifier.width(84.dp)) {
        MetricText(title, true)
        stats.forEach { MetricText(value(it), false) }
    }
}

@Composable
private fun MetricText(text: String, header: Boolean) = Text(
    text, Modifier.heightIn(min = 40.dp).padding(horizontal = VlrDimensions.Space2, vertical = VlrDimensions.Space2),
    style = if (header) VlrTheme.typography.labelSmall else VlrTheme.typography.body,
    color = if (header) VlrTheme.colors.textSecondary else VlrTheme.colors.textPrimary,
    textAlign = TextAlign.Center,
)

@Composable
private fun RecentMatchesSection(matches: List<PlayerRecentMatch>, onMatchClick: (String) -> Unit) {
    Section(PLAYER_DETAIL_MATCHES_SECTION_TAG, "최근 경기") {
        if (matches.isEmpty()) SectionEmpty("최근 경기 기록이 없습니다", Res.drawable.ic_match)
        else matches.forEach { match -> RecentMatchCard(match, onMatchClick) }
    }
}

@Composable
private fun RecentMatchCard(match: PlayerRecentMatch, onMatchClick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(playerMatchCardTag(match.id)).semantics { contentDescription = "경기 상세: ${match.eventName}" }
            .clickable(role = Role.Button) { onMatchClick(match.id) }.padding(vertical = VlrDimensions.Space2),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
    ) {
        Text(match.eventName, style = VlrTheme.typography.bodyStrong, color = VlrTheme.colors.textPrimary)
        match.eventStage?.takeIf(String::isNotBlank)?.let { Text(it, style = VlrTheme.typography.labelSmall, color = VlrTheme.colors.textSecondary) }
        Text("${match.teamA.name}${match.teamA.tag?.let { " ($it)" }.orEmpty()}  ${match.teamAScore?.toString() ?: "—"} : ${match.teamBScore?.toString() ?: "—"}  ${match.teamB.name}${match.teamB.tag?.let { " ($it)" }.orEmpty()}", style = VlrTheme.typography.body, color = VlrTheme.colors.textPrimary)
        Text(listOfNotNull(match.outcome.displayLabel(), match.playedOn).joinToString(" · "), style = VlrTheme.typography.labelSmall, color = VlrTheme.colors.textSecondary)
    }
}

@Composable
private fun Section(tag: String, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag).padding(VlrDimensions.Space4)) {
        Text(title, Modifier.semantics { heading() }, style = VlrTheme.typography.sectionTitle, color = VlrTheme.colors.textPrimary)
        Spacer(Modifier.height(VlrDimensions.Space3))
        content()
    }
}

@Composable
private fun SectionEmpty(
    message: String,
    icon: org.jetbrains.compose.resources.DrawableResource? = null,
) = Column(
    Modifier.fillMaxWidth().heightIn(min = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
) {
    icon?.let {
        Icon(vectorResource(it), null, Modifier.height(40.dp), tint = VlrTheme.colors.outline)
        Spacer(Modifier.height(VlrDimensions.Space2))
    }
    Text(message, style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary, textAlign = TextAlign.Center)
}

@Composable
private fun PlayerDetailLoading(listState: LazyListState, modifier: Modifier) = LazyColumn(
    state = listState, modifier = modifier.testTag(PLAYER_DETAIL_LOADING_TAG).semantics { contentDescription = "선수 상세를 불러오는 중" },
) {
    item("loading-header") { LoadingHeader() }
    divider("loading-header-divider")
    item("loading-team") { LoadingSection(rows = listOf(48.dp)) }
    divider("loading-team-divider")
    item("loading-stats") { LoadingSection(rows = listOf(128.dp)) }
    divider("loading-stats-divider")
    item("loading-matches") { LoadingSection(rows = listOf(88.dp, 88.dp)) }
}

@Composable
private fun LoadingHeader() = Column(
    Modifier.fillMaxWidth().padding(VlrDimensions.Space4),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Box(
        Modifier.size(96.dp).testTag(PLAYER_DETAIL_LOADING_HEADER_AVATAR_TAG).clip(CircleShape).background(VlrTheme.colors.surfaceSubtle),
    )
    Spacer(Modifier.height(VlrDimensions.Space3))
    Skeleton(160.dp, 28.dp)
    Spacer(Modifier.height(VlrDimensions.Space2))
    Skeleton(112.dp, 18.dp)
}

@Composable
private fun LoadingSection(rows: List<androidx.compose.ui.unit.Dp>) = Column(
    Modifier.fillMaxWidth().padding(VlrDimensions.Space4),
) {
    Skeleton(96.dp, 20.dp)
    Spacer(Modifier.height(VlrDimensions.Space3))
    rows.forEachIndexed { index, height ->
        Skeleton(null, height)
        if (index != rows.lastIndex) Spacer(Modifier.height(VlrDimensions.Space2))
    }
}

@Composable
private fun Skeleton(width: androidx.compose.ui.unit.Dp?, height: androidx.compose.ui.unit.Dp) = Box(
    (if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
        .height(height)
        .clip(RoundedCornerShape(VlrDimensions.Space1))
        .background(VlrTheme.colors.surfaceSubtle),
)

private fun PlayerRecentMatchOutcome.displayLabel(): String = when (this) {
    PlayerRecentMatchOutcome.WIN -> "승리"
    PlayerRecentMatchOutcome.LOSS -> "패배"
    PlayerRecentMatchOutcome.UNKNOWN -> "결과 미정"
}

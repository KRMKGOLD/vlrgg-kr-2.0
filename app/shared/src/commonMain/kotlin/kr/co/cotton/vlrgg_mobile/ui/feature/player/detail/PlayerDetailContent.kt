package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_match
import vlrggmobile.app.shared.generated.resources.ic_person
import vlrggmobile.app.shared.generated.resources.ic_star_filled
import vlrggmobile.app.shared.generated.resources.ic_star_outline

internal const val PLAYER_DETAIL_LOADING_TAG = "player-detail-loading"
internal const val PLAYER_DETAIL_HEADER_TAG = "player-detail-header"
internal const val PLAYER_DETAIL_TEAM_SECTION_TAG = "player-detail-team-section"
internal const val PLAYER_DETAIL_STATS_SECTION_TAG = "player-detail-stats-section"
internal const val PLAYER_DETAIL_MATCHES_SECTION_TAG = "player-detail-matches-section"
internal const val PLAYER_DETAIL_LOADING_HEADER_AVATAR_TAG = "player-detail-loading-header-avatar"
internal const val PLAYER_DETAIL_FAVORITE_OUTLINE_TAG = "player-detail-favorite-outline"
internal const val PLAYER_DETAIL_FAVORITE_FILLED_TAG = "player-detail-favorite-filled"
internal const val PLAYER_DETAIL_FAVORITE_SNACKBAR_TAG = "player-detail-favorite-snackbar"
internal fun playerHeaderImageTag(playerId: String) = "player-header-image-$playerId"
internal fun playerHeaderImagePlaceholderTag(playerId: String) = "player-header-image-placeholder-$playerId"
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
    onFavoriteClick: () -> Unit,
    onFavoriteRetry: () -> Unit,
    onFavoriteRestoreRetry: () -> Unit,
    onFavoriteErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { onFavoriteErrorDismiss() }
    }

    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.surface,
        topBar = {
            PlayerDetailTopBar(
                favorite = uiState.favorite,
                onBack = onBack,
                onFavoriteClick = onFavoriteClick,
            )
        },
        snackbarHost = {
            val favorite = uiState.favorite
            when {
                favorite.failedIntent != null -> FavoriteFailureSnackbar(
                    message = when (favorite.failedIntent) {
                        PlayerFavoriteMutationIntent.Add -> "즐겨찾기 추가에 실패했습니다."
                        PlayerFavoriteMutationIntent.Remove -> "즐겨찾기 해제에 실패했습니다."
                    },
                    onRetry = onFavoriteRetry,
                    testTag = PLAYER_DETAIL_FAVORITE_SNACKBAR_TAG,
                )

                favorite.hasRestoreFailure -> FavoriteFailureSnackbar(
                    message = "즐겨찾기 상태를 불러오지 못했습니다.",
                    onRetry = onFavoriteRestoreRetry,
                    testTag = PLAYER_DETAIL_FAVORITE_SNACKBAR_TAG,
                )
            }
        },
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
private fun PlayerDetailTopBar(
    favorite: PlayerFavoriteUiState,
    onBack: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(56.dp).background(VlrTheme.colors.surface)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            VlrIconButton(
                contentDescription = "뒤로 가기", onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = VlrDimensions.Space1),
                icon = { Icon(vectorResource(Res.drawable.ic_arrow_back), null) },
            )
            PlayerFavoriteButton(
                favorite = favorite,
                onClick = onFavoriteClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = VlrDimensions.Space1),
            )
            Text("Player Profile", Modifier.align(Alignment.Center), style = VlrTheme.typography.pageTitle, color = VlrTheme.colors.textPrimary)
        }
        HorizontalDivider(thickness = VlrDimensions.OutlineWidth, color = VlrTheme.colors.outline)
    }
}

@Composable
private fun PlayerFavoriteButton(
    favorite: PlayerFavoriteUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!favorite.isRestored) return

    VlrIconButton(
        contentDescription = if (favorite.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
        onClick = onClick,
        enabled = !favorite.isMutationInProgress,
        modifier = modifier.testTag(
            if (favorite.isFavorite) {
                PLAYER_DETAIL_FAVORITE_FILLED_TAG
            } else {
                PLAYER_DETAIL_FAVORITE_OUTLINE_TAG
            },
        ),
        icon = {
            Icon(
                imageVector = vectorResource(
                    if (favorite.isFavorite) Res.drawable.ic_star_filled else Res.drawable.ic_star_outline,
                ),
                contentDescription = null,
                tint = if (favorite.isFavorite) VlrTheme.colors.actionPrimary else VlrTheme.colors.textSecondary,
            )
        },
    )
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
    val imageUrl = profile.imageUrl?.takeIf(String::isNotBlank)
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
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
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(playerHeaderImageTag(player.id)),
                )
            } else {
                Text(
                    text = profile.handle.stablePlaceholder(),
                    modifier = Modifier.testTag(playerHeaderImagePlaceholderTag(player.id)),
                    style = VlrTheme.typography.display,
                    color = VlrTheme.colors.textBrand,
                )
            }
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
            PlayerCurrentTeamCard(team = team, onClick = { onTeamClick(team.id) })
        } ?: SectionEmpty("소속 팀 정보가 없습니다", Res.drawable.ic_person)
    }
}

@Composable
private fun AgentStatsSection(stats: List<PlayerAgentStat>) {
    Section(PLAYER_DETAIL_STATS_SECTION_TAG, "에이전트 통계") {
        if (stats.isEmpty()) {
            SectionEmpty("에이전트 통계 정보가 없습니다")
        } else {
            PlayerAgentStatsTable(stats)
        }
    }
}

@Composable
private fun RecentMatchesSection(matches: List<PlayerRecentMatch>, onMatchClick: (String) -> Unit) {
    Section(PLAYER_DETAIL_MATCHES_SECTION_TAG, "최근 경기") {
        if (matches.isEmpty()) SectionEmpty("최근 경기 기록이 없습니다", Res.drawable.ic_match)
        else Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
            matches.forEach { match ->
                PlayerRecentMatchCard(match = match, onClick = { onMatchClick(match.id) })
            }
        }
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
    item("loading-team") {
        LoadingSection { LoadingCurrentTeamCard() }
    }
    divider("loading-team-divider")
    item("loading-stats") {
        LoadingSection { LoadingAgentStatsTable() }
    }
    divider("loading-stats-divider")
    item("loading-matches") {
        LoadingSection {
            Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                LoadingRecentMatchCard()
                LoadingRecentMatchCard()
            }
        }
    }
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
private fun LoadingSection(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(VlrDimensions.Space4),
) {
    Skeleton(96.dp, 20.dp)
    Spacer(Modifier.height(VlrDimensions.Space3))
    content()
}

@Composable
private fun LoadingCurrentTeamCard() = Skeleton(width = null, height = 72.dp)

@Composable
private fun LoadingAgentStatsTable() = Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.OutlineWidth)) {
    Skeleton(width = null, height = 56.dp)
    Skeleton(width = null, height = 52.dp)
    Skeleton(width = null, height = 52.dp)
}

@Composable
private fun LoadingRecentMatchCard() = Skeleton(width = null, height = 96.dp)

@Composable
private fun Skeleton(width: androidx.compose.ui.unit.Dp?, height: androidx.compose.ui.unit.Dp) = Box(
    (if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
        .height(height)
        .clip(RoundedCornerShape(VlrDimensions.Space1))
        .background(VlrTheme.colors.surfaceSubtle),
)

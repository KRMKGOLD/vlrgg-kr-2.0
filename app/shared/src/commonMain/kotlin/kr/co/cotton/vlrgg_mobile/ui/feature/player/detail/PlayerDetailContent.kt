package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerAgentStat
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerDetail
import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatch
import kr.co.cotton.vlrgg_mobile.ui.component.DetailTopBand
import kr.co.cotton.vlrgg_mobile.ui.component.EntityHero
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteStarIcon
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotSurface
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonHero
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
import kr.co.cotton.vlrgg_mobile.ui.component.StatsSort
import kr.co.cotton.vlrgg_mobile.ui.component.VlrErrorDialog
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.favoriteStarTint
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.time.Duration.Companion.ZERO

internal const val PLAYER_DETAIL_LOADING_TAG = "player-detail-loading"
internal const val PLAYER_DETAIL_ERROR_SKELETON_TAG = "player-detail-error-skeleton"
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
    agentStatsHorizontalScrollState: ScrollState,
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    onRetry: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFavoriteRetry: () -> Unit,
    onFavoriteRestoreRetry: () -> Unit,
    onFavoriteErrorDismiss: () -> Unit,
    onAgentStatsSortColumn: (PlayerAgentStatsSortColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { onFavoriteErrorDismiss() }
    }
    val dismissedBusy = uiState.busyRetry?.takeUnless { it.isDialogVisible }
    var isBusyCooldownActive by remember(dismissedBusy) {
        mutableStateOf(dismissedBusy?.canRetry() == false)
    }

    LaunchedEffect(dismissedBusy) {
        val busy = dismissedBusy ?: return@LaunchedEffect
        val remaining = busy.remainingDelay()
        if (remaining > ZERO) {
            delay(remaining)
        }
        isBusyCooldownActive = false
    }

    Scaffold(
        modifier = modifier,
        containerColor = VlrTheme.colors.canvas,
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
            PlayerDetailContentState.Loading -> PlayerDetailSkeleton(
                listState,
                isLoading = true,
                Modifier.fillMaxSize().padding(padding),
            )
            is PlayerDetailContentState.Content -> PlayerDetailBody(
                state.player,
                uiState.agentStatsSort,
                listState,
                agentStatsHorizontalScrollState,
                onTeamClick,
                onMatchClick,
                onAgentStatsSortColumn,
                Modifier.fillMaxSize().padding(padding),
            )
            PlayerDetailContentState.Error -> PlayerDetailSkeleton(
                listState,
                isLoading = false,
                Modifier.fillMaxSize().padding(padding),
            )
        }
    }
    if (
        uiState.contentState == PlayerDetailContentState.Error &&
        uiState.busyRetry?.isDialogVisible != true &&
        !isBusyCooldownActive
    ) {
        VlrErrorDialog(
            title = "정보를 불러오지 못했습니다",
            message = "네트워크 상태를 확인하고 다시 시도해 주세요.",
            confirmLabel = "재시도",
            onConfirm = onRetry,
            dismissLabel = "뒤로가기",
            onDismiss = onBack,
            onDismissRequest = {},
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
    DetailTopBand(
        onBack = onBack,
        actions = { iconColor ->
            PlayerFavoriteButton(favorite = favorite, onClick = onFavoriteClick, iconColor = iconColor)
        },
    )
}

@Composable
private fun PlayerFavoriteButton(
    favorite: PlayerFavoriteUiState,
    onClick: () -> Unit,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!favorite.isRestored) return

    VlrIconButton(
        contentDescription = if (favorite.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
        onClick = onClick,
        enabled = !favorite.isMutationInProgress,
        tint = favoriteStarTint(favorite.isFavorite, iconColor),
        modifier = modifier.testTag(
            if (favorite.isFavorite) {
                PLAYER_DETAIL_FAVORITE_FILLED_TAG
            } else {
                PLAYER_DETAIL_FAVORITE_OUTLINE_TAG
            },
        ),
        icon = { FavoriteStarIcon(favorite.isFavorite) },
    )
}

/**
 * `.pen` 51-1: hero, then white `현재 팀`·`요원별 기록`·`최근 경기` sections on `canvas`, 8dp apart and
 * 8dp below the hero. Each section keeps its title and shows a Section message when empty (51-4).
 */
@Composable
private fun PlayerDetailBody(
    player: PlayerDetail,
    agentStatsSort: StatsSort<PlayerAgentStatsSortColumn>?,
    listState: LazyListState,
    agentStatsHorizontalScrollState: ScrollState,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    onAgentStatsSortColumn: (PlayerAgentStatsSortColumn) -> Unit,
    modifier: Modifier,
) {
    val currentYear = remember { KoreanDateTimeFormat.currentYear() }
    LazyColumn(
        state = listState,
        modifier = modifier.background(VlrTheme.colors.canvas),
        contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        item("header") { PlayerHeader(player) }
        item("team") { CurrentTeamSection(player, onTeamClick) }
        item("stats") {
            AgentStatsSection(
                stats = player.agentStats,
                horizontalScrollState = agentStatsHorizontalScrollState,
                sort = agentStatsSort,
                onSortColumn = onAgentStatsSortColumn,
            )
        }
        item("matches") { RecentMatchesSection(player.recentMatches, currentYear, onMatchClick) }
    }
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
    EntityHero(
        name = profile.handle,
        meta = listOf(metadata, profile.aliases.joinToString(" · ")),
        modifier = Modifier
            .testTag(PLAYER_DETAIL_HEADER_TAG)
            .semantics { contentDescription = "선수: ${profile.handle}" },
        logo = {
            LogoSlot(
                imageUrl = profile.imageUrl,
                kind = LogoSlotKind.Player,
                placeholderText = logoInitials(profile.handle),
                size = LogoSlotDefaults.HeroSize,
                surface = LogoSlotSurface.Dark,
                imageTestTag = playerHeaderImageTag(player.id),
                placeholderTestTag = playerHeaderImagePlaceholderTag(player.id),
            )
        },
    )
}

@Composable
private fun CurrentTeamSection(player: PlayerDetail, onTeamClick: (String) -> Unit) {
    val team = player.currentTeam
    Section(PLAYER_DETAIL_TEAM_SECTION_TAG, "현재 팀") {
        if (team == null) {
            SectionMessage("소속 팀 정보가 없습니다")
        } else {
            PlayerCurrentTeamCard(team = team, onClick = { onTeamClick(team.id) })
        }
    }
}

@Composable
private fun AgentStatsSection(
    stats: List<PlayerAgentStat>,
    horizontalScrollState: ScrollState,
    sort: StatsSort<PlayerAgentStatsSortColumn>?,
    onSortColumn: (PlayerAgentStatsSortColumn) -> Unit,
) {
    Section(PLAYER_DETAIL_STATS_SECTION_TAG, "요원별 기록", meta = "전체 기간".takeIf { stats.isNotEmpty() }) {
        if (stats.isEmpty()) {
            SectionMessage("에이전트 통계 정보가 없습니다")
        } else {
            PlayerAgentStatsTable(
                stats = stats,
                horizontalScrollState = horizontalScrollState,
                sort = sort,
                onSortColumn = onSortColumn,
            )
        }
    }
}

@Composable
private fun RecentMatchesSection(
    matches: List<PlayerRecentMatch>,
    currentYear: Int,
    onMatchClick: (String) -> Unit,
) {
    Section(
        tag = PLAYER_DETAIL_MATCHES_SECTION_TAG,
        title = "최근 경기",
        count = matches.size.takeIf { it > 0 }?.toString(),
        meta = "최대 5경기".takeIf { matches.isNotEmpty() },
    ) {
        if (matches.isEmpty()) {
            SectionMessage("최근 경기 기록이 없습니다")
        } else {
            matches.forEach { match ->
                PlayerRecentMatchCard(match = match, currentYear = currentYear, onClick = { onMatchClick(match.id) })
            }
        }
    }
}

/** White section with a [SectionHeader]; the meta (`전체 기간`, `최대 5경기`) only describes shown rows. */
@Composable
private fun Section(
    tag: String,
    title: String,
    count: String? = null,
    meta: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(VlrTheme.colors.surface).testTag(tag)) {
        SectionHeader(title = title, count = count, meta = meta)
        content()
    }
}

/**
 * `.pen` 51-3·51-5: dark hero skeleton with a round photo slot and two white sections of list-row
 * skeletons on `canvas`. The overall error shows the same skeleton under its dialog, so only
 * [isLoading] reads as progress.
 */
@Composable
private fun PlayerDetailSkeleton(listState: LazyListState, isLoading: Boolean, modifier: Modifier) {
    val progress = if (isLoading) {
        Modifier
            .testTag(PLAYER_DETAIL_LOADING_TAG)
            .semantics {
                contentDescription = "선수 상세를 불러오는 중"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
    } else {
        Modifier.testTag(PLAYER_DETAIL_ERROR_SKELETON_TAG)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.background(VlrTheme.colors.canvas).then(progress),
        contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        item("skeleton-hero") {
            SkeletonHero(Modifier.testTag(PLAYER_DETAIL_LOADING_HEADER_AVATAR_TAG), circularLogo = true)
        }
        listOf(3, 2).forEachIndexed { index, rows ->
            item("skeleton-section-$index") {
                Column(Modifier.fillMaxWidth().background(VlrTheme.colors.surface)) {
                    repeat(rows) { SkeletonRow() }
                }
            }
        }
    }
}

package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.ui.component.DetailTopBand
import kr.co.cotton.vlrgg_mobile.ui.component.EntityHero
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteStarIcon
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRow
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotSurface
import kr.co.cotton.vlrgg_mobile.ui.component.NewsRow
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonHero
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonRow
import kr.co.cotton.vlrgg_mobile.ui.component.VlrErrorDialog
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.favoriteStarTint
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components.TeamMatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components.TeamMatchSection
import kr.co.cotton.vlrgg_mobile.ui.format.KoreanDateTimeFormat
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.time.Duration.Companion.ZERO

internal const val TEAM_DETAIL_LOADING_TAG = "team-detail-loading"
internal const val TEAM_DETAIL_ERROR_SKELETON_TAG = "team-detail-error-skeleton"
internal const val TEAM_DETAIL_HEADER_TAG = "team-detail-header"
internal const val TEAM_DETAIL_UPCOMING_SECTION_TAG = "team-detail-upcoming-section"
internal const val TEAM_DETAIL_RECENT_SECTION_TAG = "team-detail-recent-section"
internal const val TEAM_DETAIL_PLAYERS_SECTION_TAG = "team-detail-players-section"
internal const val TEAM_DETAIL_STAFF_SECTION_TAG = "team-detail-staff-section"
internal const val TEAM_DETAIL_NEWS_SECTION_TAG = "team-detail-news-section"
internal const val TEAM_DETAIL_FAVORITE_OUTLINE_TAG = "team-detail-favorite-outline"
internal const val TEAM_DETAIL_FAVORITE_FILLED_TAG = "team-detail-favorite-filled"
internal const val TEAM_DETAIL_FAVORITE_SNACKBAR_TAG = "team-detail-favorite-snackbar"

internal fun teamMatchCardTag(matchId: String): String = "team-match-$matchId"
internal fun teamPlayerRowTag(playerId: String): String = "team-player-$playerId"
internal fun teamStaffRowTag(staffId: String): String = "team-staff-$staffId"
internal fun teamHeaderLogoTag(teamId: String): String = "team-header-logo-$teamId"
internal fun teamHeaderLogoPlaceholderTag(teamId: String): String = "team-header-logo-placeholder-$teamId"
internal fun teamRosterImageTag(memberId: String): String = "team-roster-image-$memberId"
internal fun teamRosterImagePlaceholderTag(memberId: String): String = "team-roster-image-placeholder-$memberId"
internal fun teamNewsRowTag(articleId: String, slug: String): String = "team-news-$articleId/$slug"
internal fun teamNewsPublishedDateTag(articleId: String, slug: String): String =
    "team-news-date-$articleId/$slug"

@Composable
fun TeamDetailContent(
    uiState: TeamDetailUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onMatchClick: (matchId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    onNewsClick: (articleId: String, slug: String) -> Unit,
    onRetry: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onFavoriteRetry: () -> Unit,
    onFavoriteRestoreRetry: () -> Unit,
    onFavoriteErrorDismiss: () -> Unit,
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
            TeamDetailTopBar(
                favorite = uiState.favorite,
                onBack = onBack,
                onFavoriteToggle = onFavoriteToggle,
            )
        },
        snackbarHost = {
            val favorite = uiState.favorite
            when {
                favorite.failedIntent != null -> FavoriteFailureSnackbar(
                    message = when (favorite.failedIntent) {
                        TeamFavoriteMutationIntent.Add -> "즐겨찾기 추가에 실패했습니다."
                        TeamFavoriteMutationIntent.Remove -> "즐겨찾기 해제에 실패했습니다."
                    },
                    onRetry = onFavoriteRetry,
                    testTag = TEAM_DETAIL_FAVORITE_SNACKBAR_TAG,
                )

                favorite.hasRestoreFailure -> FavoriteFailureSnackbar(
                    message = "즐겨찾기 상태를 불러오지 못했습니다.",
                    onRetry = onFavoriteRestoreRetry,
                    testTag = TEAM_DETAIL_FAVORITE_SNACKBAR_TAG,
                )
            }
        },
    ) { contentPadding ->
        when (val contentState = uiState.contentState) {
            TeamDetailContentState.Loading -> TeamDetailSkeleton(
                listState = listState,
                isLoading = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            is TeamDetailContentState.Content -> TeamDetailBody(
                team = contentState.team,
                listState = listState,
                onMatchClick = onMatchClick,
                onPlayerClick = onPlayerClick,
                onNewsClick = onNewsClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )

            TeamDetailContentState.Error -> TeamDetailSkeleton(
                listState = listState,
                isLoading = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }

    if (
        uiState.contentState == TeamDetailContentState.Error &&
        uiState.busyRetry?.isDialogVisible != true &&
        !isBusyCooldownActive
    ) {
        TeamDetailErrorDialog(
            onRetry = onRetry,
            onBack = onBack,
        )
    }
}

@Composable
private fun TeamDetailTopBar(
    favorite: TeamFavoriteUiState,
    onBack: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    DetailTopBand(
        onBack = onBack,
        actions = { iconColor ->
            if (favorite.isRestored) {
                VlrIconButton(
                    contentDescription = if (favorite.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                    onClick = onFavoriteToggle,
                    enabled = !favorite.isMutationInProgress,
                    tint = favoriteStarTint(favorite.isFavorite, iconColor),
                    modifier = Modifier.testTag(
                        if (favorite.isFavorite) {
                            TEAM_DETAIL_FAVORITE_FILLED_TAG
                        } else {
                            TEAM_DETAIL_FAVORITE_OUTLINE_TAG
                        },
                    ),
                    icon = { FavoriteStarIcon(favorite.isFavorite) },
                )
            }
        },
    )
}

/**
 * `.pen` 50-1: hero, then white `예정 경기`·`최근 경기`·`선수`·`스태프`·`뉴스` sections on `canvas`,
 * 8dp apart and 8dp below the hero. Each section keeps its title and shows a Section message
 * when empty (50-4). Rows are their own lazy items, since a team can carry a hundred news rows.
 */
@Composable
private fun TeamDetailBody(
    team: TeamDetail,
    listState: LazyListState,
    onMatchClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onNewsClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentYear = remember { KoreanDateTimeFormat.currentYear() }
    LazyColumn(
        state = listState,
        modifier = modifier.background(VlrTheme.colors.canvas),
        contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
    ) {
        item(key = "header") {
            TeamHeader(team = team)
        }
        teamSection(
            key = "upcoming",
            title = "예정 경기",
            rows = team.upcomingMatches,
            rowId = TeamMatch::id,
            emptyMessage = "예정된 경기가 없습니다",
            sectionTag = TEAM_DETAIL_UPCOMING_SECTION_TAG,
        ) { match ->
            TeamMatchCard(
                match = match,
                section = TeamMatchSection.Upcoming,
                currentYear = currentYear,
                onClick = { onMatchClick(match.id) },
            )
        }
        teamSection(
            key = "recent",
            title = "최근 경기",
            rows = team.recentMatches,
            rowId = TeamMatch::id,
            emptyMessage = "최근 경기 기록이 없습니다",
            sectionTag = TEAM_DETAIL_RECENT_SECTION_TAG,
        ) { match ->
            TeamMatchCard(
                match = match,
                section = TeamMatchSection.Recent,
                currentYear = currentYear,
                onClick = { onMatchClick(match.id) },
            )
        }
        teamSection(
            key = "players",
            title = "선수",
            rows = team.players,
            rowId = TeamRosterMember::id,
            emptyMessage = "선수 정보가 없습니다",
            sectionTag = TEAM_DETAIL_PLAYERS_SECTION_TAG,
        ) { player ->
            TeamRosterRow(
                member = player,
                testTag = teamPlayerRowTag(player.id),
                contentDescription = "선수 상세: ${player.handle}",
                onClick = { onPlayerClick(player.id) },
            )
        }
        teamSection(
            key = "staff",
            title = "스태프",
            rows = team.staff,
            rowId = TeamRosterMember::id,
            emptyMessage = "스태프 정보가 없습니다",
            sectionTag = TEAM_DETAIL_STAFF_SECTION_TAG,
        ) { member ->
            TeamRosterRow(
                member = member,
                testTag = teamStaffRowTag(member.id),
            )
        }
        teamSection(
            key = "news",
            title = "뉴스",
            rows = team.news,
            rowId = { "${it.articleId}/${it.slug}" },
            emptyMessage = "관련 뉴스가 없습니다",
            sectionTag = TEAM_DETAIL_NEWS_SECTION_TAG,
        ) { article ->
            TeamNewsRow(
                news = article,
                onClick = { onNewsClick(article.articleId, article.slug) },
            )
        }
    }
}

/**
 * An 8dp `canvas` gap, then the white section title (tagged [sectionTag]) with its count, or with a
 * Section message when [rows] is empty, then one lazy item per row. Row keys add an occurrence
 * count to [rowId], so a repeated source row cannot collide.
 */
private fun <T> LazyListScope.teamSection(
    key: String,
    title: String,
    rows: List<T>,
    rowId: (T) -> String,
    emptyMessage: String,
    sectionTag: String,
    row: @Composable (T) -> Unit,
) {
    item(key = "$key-gap") { Spacer(Modifier.height(VlrDimensions.Space2)) }
    item(key = "$key-heading") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VlrTheme.colors.surface)
                .testTag(sectionTag),
        ) {
            SectionHeader(title = title, count = rows.size.takeIf { it > 0 }?.toString())
            if (rows.isEmpty()) SectionMessage(message = emptyMessage)
        }
    }
    val occurrences = mutableMapOf<String, Int>()
    val keyedRows = rows.map { value ->
        val id = rowId(value)
        val occurrence = occurrences.getOrElse(id) { 0 } + 1
        occurrences[id] = occurrence
        "$key-$id#$occurrence" to value
    }
    items(keyedRows, key = { it.first }) { (_, value) -> row(value) }
}

@Composable
private fun TeamHeader(
    team: TeamDetail,
) {
    EntityHero(
        name = team.name,
        meta = listOfNotNull(
            listOfNotNull(
                team.tag?.takeIf(String::isNotBlank),
                team.country?.takeIf(String::isNotBlank),
            ).joinToString(" · ").takeIf(String::isNotEmpty),
        ),
        modifier = Modifier.testTag(TEAM_DETAIL_HEADER_TAG),
        logo = {
            LogoSlot(
                imageUrl = team.logoUrl,
                kind = LogoSlotKind.Team,
                placeholderText = team.tag?.takeIf(String::isNotBlank) ?: logoInitials(team.name),
                size = LogoSlotDefaults.HeroSize,
                surface = LogoSlotSurface.Dark,
                imageTestTag = teamHeaderLogoTag(team.id),
                placeholderTestTag = teamHeaderLogoPlaceholderTag(team.id),
            )
        },
    )
}

@Composable
private fun TeamRosterRow(
    member: TeamRosterMember,
    testTag: String,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    IdentityRow(
        title = member.handle,
        meta = listOfNotNull(
            member.realName?.takeIf(String::isNotBlank),
            member.roleLabels.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        ).joinToString(" · ").takeIf(String::isNotEmpty),
        leading = {
            LogoSlot(
                imageUrl = member.imageUrl,
                kind = LogoSlotKind.Player,
                placeholderText = logoInitials(member.handle),
                imageTestTag = teamRosterImageTag(member.id),
                placeholderTestTag = teamRosterImagePlaceholderTag(member.id),
            )
        },
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = Modifier.testTag(testTag),
    )
}

/** Team news row; the date is `2026년 9월 15일` when the server sends `publishedDate`, otherwise the source. */
@Composable
private fun TeamNewsRow(
    news: TeamNews,
    onClick: () -> Unit,
) {
    NewsRow(
        title = news.title,
        byline = news.publishedDate?.let(KoreanDateTimeFormat::date) ?: news.publishedDateText,
        onClick = onClick,
        contentDescription = "뉴스 상세: ${news.title}",
        bylineTestTag = teamNewsPublishedDateTag(news.articleId, news.slug),
        modifier = Modifier.testTag(teamNewsRowTag(news.articleId, news.slug)),
    )
}

/**
 * `.pen` 50-3·50-5: dark hero skeleton and two white sections of list-row skeletons on `canvas`.
 * The overall error shows the same skeleton under its dialog, so only [isLoading] reads as progress.
 */
@Composable
private fun TeamDetailSkeleton(
    listState: LazyListState,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = if (isLoading) {
        Modifier
            .testTag(TEAM_DETAIL_LOADING_TAG)
            .semantics {
                contentDescription = "팀 상세를 불러오는 중"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
    } else {
        Modifier.testTag(TEAM_DETAIL_ERROR_SKELETON_TAG)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .background(VlrTheme.colors.canvas)
            .then(progress),
        contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        item(key = "skeleton-hero") { SkeletonHero() }
        listOf(3, 2).forEachIndexed { index, rows ->
            item(key = "skeleton-section-$index") {
                Column(Modifier.fillMaxWidth().background(VlrTheme.colors.surface)) {
                    repeat(rows) { SkeletonRow() }
                }
            }
        }
    }
}

@Composable
private fun TeamDetailErrorDialog(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    VlrErrorDialog(
        title = "정보를 불러오지 못했습니다",
        message = "네트워크 상태를 확인하고 다시 시도해 주세요.",
        confirmLabel = "재시도",
        onConfirm = onRetry,
        dismissLabel = "뒤로가기",
        onDismiss = onBack,
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

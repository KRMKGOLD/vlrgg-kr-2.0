package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.VlrErrorDialog
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components.TeamMatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components.TeamMatchSection
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.time.Duration.Companion.ZERO
import kr.co.cotton.vlrgg_mobile.ui.component.DetailTopBand
import kr.co.cotton.vlrgg_mobile.ui.component.EntityHero
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteStarIcon
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRow
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotSurface
import kr.co.cotton.vlrgg_mobile.ui.component.NewsRow
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonHero
import kr.co.cotton.vlrgg_mobile.ui.component.favoriteStarTint
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials

internal const val TEAM_DETAIL_LOADING_TAG = "team-detail-loading"
internal const val TEAM_DETAIL_HEADER_TAG = "team-detail-header"
internal const val TEAM_DETAIL_UPCOMING_SECTION_TAG = "team-detail-upcoming-section"
internal const val TEAM_DETAIL_RECENT_SECTION_TAG = "team-detail-recent-section"
internal const val TEAM_DETAIL_ROSTER_SECTION_TAG = "team-detail-roster-section"
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
        containerColor = VlrTheme.colors.surface,
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
            TeamDetailContentState.Loading -> TeamDetailLoading(
                listState = listState,
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

            TeamDetailContentState.Error -> Box(
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

@Composable
private fun TeamDetailBody(
    team: TeamDetail,
    listState: LazyListState,
    onMatchClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onNewsClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        item(key = "header") {
            TeamHeader(team = team)
        }
        item(key = "upcoming") {
            TeamMatchesSection(
                title = "예정된 경기",
                matches = team.upcomingMatches,
                section = TeamMatchSection.Upcoming,
                emptyMessage = "예정된 경기가 없습니다",
                onMatchClick = onMatchClick,
                modifier = Modifier.testTag(TEAM_DETAIL_UPCOMING_SECTION_TAG),
            )
        }
        divider(key = "upcoming-divider")
        item(key = "recent") {
            TeamMatchesSection(
                title = "최근 경기",
                matches = team.recentMatches,
                section = TeamMatchSection.Recent,
                emptyMessage = "최근 경기 기록이 없습니다",
                onMatchClick = onMatchClick,
                modifier = Modifier.testTag(TEAM_DETAIL_RECENT_SECTION_TAG),
            )
        }
        divider(key = "recent-divider")
        item(key = "roster") {
            TeamRosterSection(
                players = team.players,
                staff = team.staff,
                onPlayerClick = onPlayerClick,
                modifier = Modifier.testTag(TEAM_DETAIL_ROSTER_SECTION_TAG),
            )
        }
        divider(key = "roster-divider")
        item(key = "news") {
            TeamNewsSection(
                news = team.news,
                onNewsClick = onNewsClick,
                modifier = Modifier.testTag(TEAM_DETAIL_NEWS_SECTION_TAG),
            )
        }
        item(key = "bottom-space") {
            Spacer(Modifier.height(VlrDimensions.Space8))
        }
    }
}

private fun LazyListScope.divider(key: String) {
    item(key = key) {
        HorizontalDivider(
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.line,
        )
    }
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
private fun TeamMatchesSection(
    title: String,
    matches: List<kr.co.cotton.vlrgg_mobile.domain.model.team.TeamMatch>,
    section: TeamMatchSection,
    emptyMessage: String,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = VlrDimensions.Space4),
    ) {
        SectionTitle(
            title = title,
            modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
        )
        Spacer(Modifier.height(VlrDimensions.Space3))
        if (matches.isEmpty()) {
            SectionEmpty(
                message = emptyMessage,
                modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
            )
        } else {
            matches.forEach { match ->
                TeamMatchCard(
                    match = match,
                    section = section,
                    onClick = { onMatchClick(match.id) },
                )
            }
        }
    }
}

@Composable
private fun TeamRosterSection(
    players: List<TeamRosterMember>,
    staff: List<TeamRosterMember>,
    onPlayerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VlrDimensions.Space4),
    ) {
        SectionTitle(
            title = "로스터",
            modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
        )
        Spacer(Modifier.height(VlrDimensions.Space3))

        if (players.isEmpty() && staff.isEmpty()) {
            SectionEmpty(
                message = "로스터 정보가 없습니다",
                modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
            )
            return
        }

        RosterGroupTitle("선수")
        if (players.isEmpty()) {
            InlineEmpty("선수 정보가 없습니다")
        } else {
            players.forEach { player ->
                TeamRosterRow(
                    member = player,
                    testTag = teamPlayerRowTag(player.id),
                    contentDescription = "선수 상세: ${player.handle}",
                    onClick = { onPlayerClick(player.id) },
                )
            }
        }

        Spacer(Modifier.height(VlrDimensions.Space4))
        RosterGroupTitle("스태프")
        if (staff.isEmpty()) {
            InlineEmpty("스태프 정보가 없습니다")
        } else {
            staff.forEach { member ->
                TeamRosterRow(
                    member = member,
                    testTag = teamStaffRowTag(member.id),
                )
            }
        }
    }
}

@Composable
private fun RosterGroupTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
        style = VlrTheme.typography.meta,
        color = VlrTheme.colors.muted,
    )
    Spacer(Modifier.height(VlrDimensions.Space2))
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

@Composable
private fun TeamNewsSection(
    news: List<TeamNews>,
    onNewsClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VlrDimensions.Space4),
    ) {
        SectionTitle(
            title = "뉴스",
            modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
        )
        Spacer(Modifier.height(VlrDimensions.Space3))
        if (news.isEmpty()) {
            SectionEmpty(
                message = "관련 뉴스가 없습니다",
                modifier = Modifier.padding(horizontal = VlrDimensions.Space4),
            )
        } else {
            news.forEach { article ->
                TeamNewsRow(
                    news = article,
                    onClick = { onNewsClick(article.articleId, article.slug) },
                )
            }
        }
    }
}

@Composable
private fun TeamNewsRow(
    news: TeamNews,
    onClick: () -> Unit,
) {
    NewsRow(
        title = news.title,
        byline = news.publishedDateText,
        onClick = onClick,
        contentDescription = "뉴스 상세: ${news.title}",
        bylineTestTag = teamNewsPublishedDateTag(news.articleId, news.slug),
        modifier = Modifier.testTag(teamNewsRowTag(news.articleId, news.slug)),
    )
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = VlrTheme.typography.section,
        color = VlrTheme.colors.ink,
    )
}

@Composable
private fun SectionEmpty(
    message: String,
    modifier: Modifier = Modifier,
) {
    SectionMessage(message = message, modifier = modifier, horizontalPadding = 0.dp)
}

@Composable
private fun InlineEmpty(message: String) {
    SectionMessage(message = message)
}

@Composable
private fun TeamDetailLoading(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val skeletonShape = RoundedCornerShape(VlrDimensions.Space1)

    LazyColumn(
        state = listState,
        modifier = modifier
            .testTag(TEAM_DETAIL_LOADING_TAG)
            .semantics { contentDescription = "팀 상세를 불러오는 중" },
    ) {
        item(key = "loading-header") {
            SkeletonHero()
        }
        item(key = "loading-upcoming") {
            LoadingSection(title = "예정된 경기") {
                LoadingMatchCard(shape = skeletonShape)
            }
        }
        divider(key = "loading-upcoming-divider")
        item(key = "loading-recent") {
            LoadingSection(title = "최근 경기") {
                Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                    repeat(2) { LoadingMatchCard(shape = skeletonShape) }
                }
            }
        }
        divider(key = "loading-recent-divider")
        item(key = "loading-roster") {
            LoadingSection(title = "로스터") {
                Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
                    Text(
                        text = "선수",
                        style = VlrTheme.typography.meta,
                        color = VlrTheme.colors.muted,
                    )
                    repeat(2) { LoadingRosterRow() }
                    Text(
                        text = "스태프",
                        style = VlrTheme.typography.meta,
                        color = VlrTheme.colors.muted,
                    )
                    LoadingRosterRow()
                }
            }
        }
        divider(key = "loading-roster-divider")
        item(key = "loading-news") {
            LoadingSection(title = "뉴스") {
                Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3)) {
                    repeat(2) {
                        Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1)) {
                            SkeletonBox(Modifier.fillMaxWidth().height(20.dp))
                            SkeletonBox(Modifier.size(width = 80.dp, height = 12.dp))
                        }
                    }
                }
            }
        }
        item(key = "loading-bottom-space") {
            Spacer(Modifier.height(VlrDimensions.Space8))
        }
    }
}

@Composable
private fun LoadingSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(VlrDimensions.Space4),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun LoadingMatchCard(shape: RoundedCornerShape) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.line, shape)
            .padding(VlrDimensions.Space3),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
            SkeletonBox(Modifier.size(width = 64.dp, height = 24.dp).clip(CircleShape))
            SkeletonBox(Modifier.weight(1f).height(16.dp))
            SkeletonBox(Modifier.size(width = 80.dp, height = 16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3)) {
            SkeletonBox(Modifier.weight(1f).height(20.dp))
            SkeletonBox(Modifier.size(width = 32.dp, height = 20.dp))
            SkeletonBox(Modifier.weight(1f).height(20.dp))
        }
    }
}

@Composable
private fun LoadingRosterRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(32.dp).clip(CircleShape))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        ) {
            SkeletonBox(Modifier.size(width = 96.dp, height = 16.dp))
            SkeletonBox(Modifier.size(width = 128.dp, height = 12.dp))
        }
        SkeletonBox(Modifier.size(width = 72.dp, height = 16.dp))
    }
}

@Composable
private fun SkeletonBox(modifier: Modifier) {
    SkeletonBlock(modifier)
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

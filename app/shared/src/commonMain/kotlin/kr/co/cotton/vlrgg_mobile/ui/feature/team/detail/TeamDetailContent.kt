package kr.co.cotton.vlrgg_mobile.ui.feature.team.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.window.DialogProperties
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamDetail
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamNews
import kr.co.cotton.vlrgg_mobile.domain.model.team.TeamRosterMember
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components.TeamMatchCard
import kr.co.cotton.vlrgg_mobile.ui.feature.team.detail.components.TeamMatchSection
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_error
import vlrggmobile.app.shared.generated.resources.ic_match
import vlrggmobile.app.shared.generated.resources.ic_news
import vlrggmobile.app.shared.generated.resources.ic_person
import vlrggmobile.app.shared.generated.resources.ic_star_filled
import vlrggmobile.app.shared.generated.resources.ic_star_outline

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

    if (uiState.contentState == TeamDetailContentState.Error) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(VlrTheme.colors.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            VlrIconButton(
                contentDescription = "뒤로 가기",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = VlrDimensions.Space1),
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_arrow_back),
                        contentDescription = null,
                    )
                },
            )
            Text(
                text = "Team Detail",
                modifier = Modifier.align(Alignment.Center),
                style = VlrTheme.typography.pageTitle,
                color = VlrTheme.colors.textPrimary,
            )
            if (favorite.isRestored) {
                VlrIconButton(
                    contentDescription = if (favorite.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                    onClick = onFavoriteToggle,
                    enabled = !favorite.isMutationInProgress,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = VlrDimensions.Space1)
                        .testTag(
                            if (favorite.isFavorite) {
                                TEAM_DETAIL_FAVORITE_FILLED_TAG
                            } else {
                                TEAM_DETAIL_FAVORITE_OUTLINE_TAG
                            },
                        ),
                    icon = {
                        Icon(
                            imageVector = vectorResource(
                                if (favorite.isFavorite) Res.drawable.ic_star_filled else Res.drawable.ic_star_outline,
                            ),
                            contentDescription = null,
                            tint = if (favorite.isFavorite) {
                                VlrTheme.colors.actionPrimary
                            } else {
                                VlrTheme.colors.textPrimary
                            },
                        )
                    },
                )
            }
        }
        HorizontalDivider(
            thickness = VlrDimensions.OutlineWidth,
            color = VlrTheme.colors.outline,
        )
    }
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
        divider(key = "header-divider")
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
            color = VlrTheme.colors.outline,
        )
    }
}

@Composable
private fun TeamHeader(
    team: TeamDetail,
) {
    val imageUrl = team.logoUrl?.takeIf(String::isNotBlank)
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val metadata = listOfNotNull(
        team.tag?.takeIf(String::isNotBlank),
        team.country?.takeIf(String::isNotBlank),
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TEAM_DETAIL_HEADER_TAG)
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space6,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(VlrTheme.colors.surfaceSubtle)
                .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(teamHeaderLogoTag(team.id)),
                )
            } else {
                Text(
                    text = team.tag?.takeIf(String::isNotBlank) ?: team.name.stableInitials(),
                    modifier = Modifier.testTag(teamHeaderLogoPlaceholderTag(team.id)),
                    style = VlrTheme.typography.display,
                    color = VlrTheme.colors.textBrand,
                )
            }
        }
        Spacer(Modifier.height(VlrDimensions.Space3))
        Text(
            text = team.name,
            style = VlrTheme.typography.display,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (metadata.isNotEmpty()) {
            Spacer(Modifier.height(VlrDimensions.Space1))
            Text(
                text = metadata,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun String.stableInitials(): String = trim()
    .split(Regex("\\s+"))
    .filter(String::isNotEmpty)
    .take(2)
    .mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
    .joinToString("")

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
            .padding(VlrDimensions.Space4),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
    ) {
        SectionTitle(title)
        if (matches.isEmpty()) {
            SectionEmpty(
                message = emptyMessage,
                icon = Res.drawable.ic_match,
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
                icon = Res.drawable.ic_person,
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
        style = VlrTheme.typography.label,
        color = VlrTheme.colors.textSecondary,
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
    val imageUrl = member.imageUrl?.takeIf(String::isNotBlank)
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val interactiveModifier = if (onClick != null && contentDescription != null) {
        Modifier
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag)
            .then(interactiveModifier)
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space2,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(VlrTheme.colors.surfaceSubtle),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(teamRosterImageTag(member.id)),
                )
            } else {
                Text(
                    text = member.handle.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    modifier = Modifier.testTag(teamRosterImagePlaceholderTag(member.id)),
                    style = VlrTheme.typography.label,
                    color = VlrTheme.colors.textBrand,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        ) {
            Text(
                text = member.handle,
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
            )
            member.realName?.takeIf(String::isNotBlank)?.let { realName ->
                Text(
                    text = realName,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        }
        member.roleLabels.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { roles ->
            Text(
                text = roles,
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
    HorizontalDivider(
        thickness = VlrDimensions.OutlineWidth,
        color = VlrTheme.colors.outline,
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
                icon = Res.drawable.ic_news,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(teamNewsRowTag(news.articleId, news.slug))
            .semantics { contentDescription = "뉴스 상세: ${news.title}" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = VlrDimensions.Space4,
                vertical = VlrDimensions.Space3,
            ),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
    ) {
        Text(
            text = news.title,
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
        )
        news.publishedDateText?.takeIf(String::isNotBlank)?.let { publishedDateText ->
            Text(
                text = publishedDateText,
                modifier = Modifier.testTag(teamNewsPublishedDateTag(news.articleId, news.slug)),
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.textSecondary,
            )
        }
    }
    HorizontalDivider(
        thickness = VlrDimensions.OutlineWidth,
        color = VlrTheme.colors.outline,
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
        style = VlrTheme.typography.sectionTitle,
        color = VlrTheme.colors.textPrimary,
    )
}

@Composable
private fun SectionEmpty(
    message: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = VlrTheme.colors.outline,
        )
        Spacer(Modifier.height(VlrDimensions.Space2))
        Text(
            text = message,
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InlineEmpty(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = VlrDimensions.Space4),
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = VlrDimensions.Space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                SkeletonBox(Modifier.size(96.dp).clip(CircleShape))
                SkeletonBox(Modifier.size(width = 128.dp, height = 28.dp))
                SkeletonBox(Modifier.size(width = 96.dp, height = 16.dp))
            }
        }
        divider(key = "loading-header-divider")
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
                        style = VlrTheme.typography.label,
                        color = VlrTheme.colors.textSecondary,
                    )
                    repeat(2) { LoadingRosterRow() }
                    Text(
                        text = "스태프",
                        style = VlrTheme.typography.label,
                        color = VlrTheme.colors.textSecondary,
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
            .border(VlrDimensions.OutlineWidth, VlrTheme.colors.outline, shape)
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(VlrDimensions.Space1))
            .background(VlrTheme.colors.skeleton),
    )
}

@Composable
private fun TeamDetailErrorDialog(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VlrTheme.colors.surfaceSelected),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_error),
                    contentDescription = null,
                    tint = VlrTheme.colors.actionPrimary,
                )
            }
        },
        title = {
            Text(
                text = "정보를 불러오지 못했습니다",
                style = VlrTheme.typography.sectionTitle,
                color = VlrTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "네트워크 상태를 확인하고 다시 시도해 주세요.",
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                VlrButton(
                    text = "재시도",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                VlrButton(
                    text = "뒤로가기",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    variant = VlrButtonVariant.Secondary,
                )
            }
        },
        containerColor = VlrTheme.colors.surface,
    )
}

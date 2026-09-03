package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrButtonVariant
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_error
import vlrggmobile.app.shared.generated.resources.ic_search
import vlrggmobile.app.shared.generated.resources.ic_star_filled

internal const val MY_PAGE_LIST_TAG = "my_page_list"
internal const val MY_PAGE_TOP_APP_BAR_TAG = "my_page_top_app_bar"
internal const val MY_PAGE_FULL_ERROR_TAG = "my_page_full_error"
internal const val MY_PAGE_REMOVAL_SNACKBAR_TAG = "my_page_removal_snackbar"
internal const val MY_PAGE_TEAM_SECTION_TAG = "my_page_team_section"
internal const val MY_PAGE_PLAYER_SECTION_TAG = "my_page_player_section"

internal fun myPageTeamRowTag(id: String): String = "my_page_team_$id"

internal fun myPagePlayerRowTag(id: String): String = "my_page_player_$id"

@Composable
fun MyPageScreen(
    onSearch: () -> Unit,
    onTeamClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    viewModel: MyPageViewModel = metroViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    MyPageContent(
        uiState = uiState,
        listState = listState,
        onSearch = onSearch,
        onTeamClick = onTeamClick,
        onPlayerClick = onPlayerClick,
        onRemoveTeam = viewModel::removeFavoriteTeam,
        onRemovePlayer = viewModel::removeFavoritePlayer,
        onRetry = viewModel::retry,
        onRetryTeams = viewModel::retryFavoriteTeams,
        onRetryPlayers = viewModel::retryFavoritePlayers,
        onRemovalRetry = viewModel::retryFavoriteRemoval,
        modifier = modifier,
    )
}

@Composable
internal fun MyPageContent(
    uiState: MyPageUiState,
    listState: LazyListState,
    onSearch: () -> Unit,
    onTeamClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onRemoveTeam: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryTeams: () -> Unit,
    onRetryPlayers: () -> Unit,
    onRemovalRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VlrTheme.colors.surface,
        topBar = { MyPageTopAppBar(onSearch) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (uiState.isFullError) {
                FullError(
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MY_PAGE_FULL_ERROR_TAG),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MY_PAGE_LIST_TAG),
                    contentPadding = PaddingValues(
                        horizontal = VlrDimensions.Space4,
                        vertical = VlrDimensions.Space6,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
                ) {
                    item(key = "favorite_teams_header") {
                        SectionHeader("Favorite Teams", MY_PAGE_TEAM_SECTION_TAG)
                    }
                    when (val teams = uiState.favoriteTeams) {
                        FavoriteSectionState.Loading -> item(key = "favorite_teams_loading") {
                            SectionCard(
                                modifier = Modifier.semantics {
                                    contentDescription = "즐겨찾기한 팀을 불러오는 중"
                                },
                            ) { SectionLoading("즐겨찾기한 팀을 불러오는 중") }
                        }

                        FavoriteSectionState.Empty -> item(key = "favorite_teams_empty") {
                            SectionMessage("즐겨찾기한 팀이 없습니다.")
                        }

                        FavoriteSectionState.Error -> item(key = "favorite_teams_error") {
                            SectionMessage(
                                message = "즐겨찾기한 팀을 불러오지 못했습니다.",
                                action = {
                                    VlrButton(
                                        text = "재시도",
                                        onClick = onRetryTeams,
                                        variant = VlrButtonVariant.Secondary,
                                    )
                                },
                            )
                        }

                        is FavoriteSectionState.Content -> items(
                            items = teams.favorites,
                            key = { favorite -> "favorite_team_${favorite.id}" },
                        ) { favorite ->
                            SectionCard {
                                FavoriteTeamRow(
                                    favorite = favorite,
                                    onClick = onTeamClick,
                                    onRemove = onRemoveTeam,
                                )
                            }
                        }
                    }
                    item(key = "favorite_sections_gap") {
                        Spacer(Modifier.height(VlrDimensions.Space2))
                    }
                    item(key = "favorite_players_header") {
                        SectionHeader("Favorite Players", MY_PAGE_PLAYER_SECTION_TAG)
                    }
                    when (val players = uiState.favoritePlayers) {
                        FavoriteSectionState.Loading -> item(key = "favorite_players_loading") {
                            SectionCard(
                                modifier = Modifier.semantics {
                                    contentDescription = "즐겨찾기한 선수를 불러오는 중"
                                },
                            ) { SectionLoading("즐겨찾기한 선수를 불러오는 중") }
                        }

                        FavoriteSectionState.Empty -> item(key = "favorite_players_empty") {
                            SectionMessage("즐겨찾기한 선수가 없습니다.")
                        }

                        FavoriteSectionState.Error -> item(key = "favorite_players_error") {
                            SectionMessage(
                                message = "즐겨찾기한 선수를 불러오지 못했습니다.",
                                action = {
                                    VlrButton(
                                        text = "재시도",
                                        onClick = onRetryPlayers,
                                        variant = VlrButtonVariant.Secondary,
                                    )
                                },
                            )
                        }

                        is FavoriteSectionState.Content -> items(
                            items = players.favorites,
                            key = { favorite -> "favorite_player_${favorite.id}" },
                        ) { favorite ->
                            SectionCard {
                                FavoritePlayerRow(
                                    favorite = favorite,
                                    onClick = onPlayerClick,
                                    onRemove = onRemovePlayer,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.failedRemoval != null) {
                FavoriteFailureSnackbar(
                    message = "즐겨찾기 해제에 실패했습니다.",
                    onRetry = onRemovalRetry,
                    testTag = MY_PAGE_REMOVAL_SNACKBAR_TAG,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun MyPageTopAppBar(onSearch: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(MY_PAGE_TOP_APP_BAR_TAG)
                .padding(horizontal = VlrDimensions.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MyPage",
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.pageTitle,
                color = VlrTheme.colors.textPrimary,
            )
            VlrIconButton(
                contentDescription = "검색",
                onClick = onSearch,
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_search),
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
private fun SectionHeader(title: String, testTag: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        style = VlrTheme.typography.sectionTitle,
        color = VlrTheme.colors.textPrimary,
    )
}

@Composable
private fun SectionLoading(description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(VlrDimensions.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = VlrTheme.colors.actionPrimary,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(VlrDimensions.Space3))
        Text(
            text = description,
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun FavoriteTeamRow(
    favorite: FavoriteTeam,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    FavoriteRow(
        id = favorite.id,
        title = favorite.name,
        subtitle = listOfNotNull(favorite.tag, favorite.country).joinToString(" · ").ifBlank { null },
        rowDescription = "팀 상세: ${favorite.name}",
        removalDescription = "${favorite.name} 즐겨찾기 해제",
        onClick = onClick,
        onRemove = onRemove,
        modifier = Modifier.testTag(myPageTeamRowTag(favorite.id)),
    )
}

@Composable
private fun FavoritePlayerRow(
    favorite: FavoritePlayer,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    FavoriteRow(
        id = favorite.id,
        title = favorite.handle,
        subtitle = listOfNotNull(favorite.realName, favorite.countryName).joinToString(" · ").ifBlank { null },
        rowDescription = "선수 상세: ${favorite.handle}",
        removalDescription = "${favorite.handle} 즐겨찾기 해제",
        onClick = onClick,
        onRemove = onRemove,
        modifier = Modifier.testTag(myPagePlayerRowTag(favorite.id)),
    )
}

@Composable
private fun FavoriteRow(
    id: String,
    title: String,
    subtitle: String?,
    rowDescription: String,
    removalDescription: String,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDetail = remember(id, onClick) { { onClick(id) } }
    val removeFavorite = remember(id, onRemove) { { onRemove(id) } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = openDetail)
            .semantics { contentDescription = rowDescription }
            .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(VlrTheme.colors.surfaceSubtle),
        )
        Spacer(Modifier.width(VlrDimensions.Space3))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        ) {
            Text(
                text = title,
                style = VlrTheme.typography.bodyStrong,
                color = VlrTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        VlrIconButton(
            contentDescription = removalDescription,
            onClick = removeFavorite,
            icon = {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_star_filled),
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VlrDimensions.CardCornerRadius),
        color = VlrTheme.colors.surface,
        border = BorderStroke(VlrDimensions.OutlineWidth, VlrTheme.colors.outline),
        content = content,
    )
}

@Composable
private fun SectionMessage(
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .padding(VlrDimensions.Space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            action?.let {
                Spacer(Modifier.height(VlrDimensions.Space3))
                it()
            }
        }
    }
}

@Composable
private fun FullError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(VlrDimensions.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = VlrTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(VlrDimensions.Space4))
        Text(
            text = "즐겨찾기를 불러오지 못했습니다.",
            style = VlrTheme.typography.bodyStrong,
            color = VlrTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VlrDimensions.Space4))
        VlrButton(text = "재시도", onClick = onRetry)
    }
}

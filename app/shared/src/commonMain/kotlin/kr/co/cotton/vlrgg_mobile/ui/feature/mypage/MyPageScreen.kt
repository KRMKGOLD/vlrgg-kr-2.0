package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRow
import kr.co.cotton.vlrgg_mobile.ui.component.IdentityRowTrailing
import kr.co.cotton.vlrgg_mobile.ui.component.InlineRetry
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlot
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotDefaults
import kr.co.cotton.vlrgg_mobile.ui.component.LogoSlotKind
import kr.co.cotton.vlrgg_mobile.ui.component.RootHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.SkeletonBlock
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.logoInitials
import kr.co.cotton.vlrgg_mobile.ui.component.rowDivider
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_info

internal const val MY_PAGE_LIST_TAG = "my_page_list"
internal const val MY_PAGE_TOP_APP_BAR_TAG = "my_page_top_app_bar"
internal const val MY_PAGE_FULL_ERROR_TAG = "my_page_full_error"
internal const val MY_PAGE_REMOVAL_SNACKBAR_TAG = "my_page_removal_snackbar"
internal const val MY_PAGE_TEAM_SECTION_TAG = "my_page_team_section"
internal const val MY_PAGE_PLAYER_SECTION_TAG = "my_page_player_section"

internal fun myPageTeamRowTag(id: String): String = "my_page_team_$id"
internal fun myPageTeamImageTag(id: String): String = "my_page_team_image_$id"
internal fun myPageTeamImagePlaceholderTag(id: String): String = "my_page_team_image_placeholder_$id"

internal fun myPagePlayerRowTag(id: String): String = "my_page_player_$id"
internal fun myPagePlayerImageTag(id: String): String = "my_page_player_image_$id"
internal fun myPagePlayerImagePlaceholderTag(id: String): String = "my_page_player_image_placeholder_$id"

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
        containerColor = VlrTheme.colors.canvas,
        topBar = {
            RootHeader(
                title = "마이페이지",
                onSearch = onSearch,
                modifier = Modifier.testTag(MY_PAGE_TOP_APP_BAR_TAG),
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (uiState.isFullError) {
                StatePanel(
                    title = "즐겨찾기를 불러오지 못했습니다.",
                    actionLabel = "재시도",
                    onAction = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VlrTheme.colors.surface)
                        .testTag(MY_PAGE_FULL_ERROR_TAG),
                )
            } else {
                // White sections on the canvas, 8dp apart (`.pen` 30-1). Each item paints its own
                // surface so team tiles and player rows stay lazy items.
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MY_PAGE_LIST_TAG),
                    contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
                ) {
                    favoriteTeamsSection(
                        teams = uiState.favoriteTeams,
                        onTeamClick = onTeamClick,
                        onRemoveTeam = onRemoveTeam,
                        onRetryTeams = onRetryTeams,
                    )
                    item(key = "favorite_sections_gap") {
                        Spacer(Modifier.height(VlrDimensions.Space2))
                    }
                    favoritePlayersSection(
                        players = uiState.favoritePlayers,
                        onPlayerClick = onPlayerClick,
                        onRemovePlayer = onRemovePlayer,
                        onRetryPlayers = onRetryPlayers,
                    )
                    item(key = "favorite_storage_note") {
                        StorageNote()
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

private fun LazyListScope.favoriteTeamsSection(
    teams: FavoriteSectionState<FavoriteTeam>,
    onTeamClick: (String) -> Unit,
    onRemoveTeam: (String) -> Unit,
    onRetryTeams: () -> Unit,
) {
    item(key = "favorite_teams_header") {
        SectionHeader(
            title = "팀",
            count = (teams as? FavoriteSectionState.Content)?.favorites?.size?.toString(),
            modifier = Modifier
                .testTag(MY_PAGE_TEAM_SECTION_TAG)
                .background(VlrTheme.colors.surface),
        )
    }
    when (teams) {
        FavoriteSectionState.Loading -> item(key = "favorite_teams_loading") {
            TeamTileRow(
                isFirst = true,
                modifier = Modifier.sectionLoading("즐겨찾기한 팀을 불러오는 중"),
            ) {
                repeat(TeamTilesPerRow) { FavoriteTeamTileSkeleton(Modifier.weight(1f)) }
            }
        }

        FavoriteSectionState.Empty -> item(key = "favorite_teams_empty") {
            SectionMessage("즐겨찾기한 팀이 없습니다.", Modifier.background(VlrTheme.colors.surface))
        }

        FavoriteSectionState.Error -> item(key = "favorite_teams_error") {
            InlineRetry(
                message = "즐겨찾기한 팀을 불러오지 못했습니다.",
                onRetry = onRetryTeams,
                showBottomDivider = false,
                modifier = Modifier.background(VlrTheme.colors.surface),
            )
        }

        is FavoriteSectionState.Content -> {
            val rows = teams.favorites.chunked(TeamTilesPerRow)
            itemsIndexed(
                items = rows,
                key = { _, row -> "favorite_team_row_${row.first().id}" },
            ) { index, row ->
                TeamTileRow(isFirst = index == 0) {
                    row.forEach { favorite ->
                        FavoriteTeamTile(
                            favorite = favorite,
                            onClick = onTeamClick,
                            onRemove = onRemoveTeam,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(myPageTeamRowTag(favorite.id)),
                        )
                    }
                    repeat(TeamTilesPerRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    item(key = "favorite_teams_bottom") {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(VlrDimensions.Space4)
                .background(VlrTheme.colors.surface),
        )
    }
}

private fun LazyListScope.favoritePlayersSection(
    players: FavoriteSectionState<FavoritePlayer>,
    onPlayerClick: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onRetryPlayers: () -> Unit,
) {
    item(key = "favorite_players_header") {
        SectionHeader(
            title = "선수",
            count = (players as? FavoriteSectionState.Content)?.favorites?.size?.toString(),
            modifier = Modifier
                .testTag(MY_PAGE_PLAYER_SECTION_TAG)
                .background(VlrTheme.colors.surface),
        )
    }
    when (players) {
        FavoriteSectionState.Loading -> item(key = "favorite_players_loading") {
            Column(Modifier.sectionLoading("즐겨찾기한 선수를 불러오는 중")) {
                repeat(PlayerSkeletonRows) { PlayerRowSkeleton() }
            }
        }

        FavoriteSectionState.Empty -> item(key = "favorite_players_empty") {
            SectionMessage("즐겨찾기한 선수가 없습니다.", Modifier.background(VlrTheme.colors.surface))
        }

        FavoriteSectionState.Error -> item(key = "favorite_players_error") {
            InlineRetry(
                message = "즐겨찾기한 선수를 불러오지 못했습니다.",
                onRetry = onRetryPlayers,
                showBottomDivider = false,
                modifier = Modifier.background(VlrTheme.colors.surface),
            )
        }

        is FavoriteSectionState.Content -> items(
            items = players.favorites,
            key = { favorite -> "favorite_player_${favorite.id}" },
        ) { favorite ->
            FavoritePlayerRow(
                favorite = favorite,
                onClick = onPlayerClick,
                onRemove = onRemovePlayer,
            )
        }
    }
}

private const val TeamTilesPerRow = 2
private const val PlayerSkeletonRows = 2

/** One row of two equal-height tiles; the first row sits 4dp under the header, later rows 8dp apart. */
@Composable
private fun TeamTileRow(
    isFirst: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = if (isFirst) VlrDimensions.Space1 else VlrDimensions.Space2,
            )
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
        content = content,
    )
}

/** Section skeletons keep the existing loading description and announce indeterminate progress. */
private fun Modifier.sectionLoading(description: String): Modifier = semantics {
    contentDescription = description
    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
}

/** Placeholder for a player identity row (`.pen` 30-2 `Row skeleton`). */
@Composable
private fun PlayerRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .rowDivider(VlrTheme.colors.line)
            .padding(horizontal = VlrDimensions.Space4, vertical = 14.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LogoSlotDefaults.RowSize)
                .background(VlrTheme.colors.skeleton, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2)) {
            SkeletonBlock(Modifier.size(width = 96.dp, height = 14.dp))
            SkeletonBlock(Modifier.size(width = 150.dp, height = 10.dp))
        }
    }
}

/** `info` note under the sections: favorites live only on this device (`.pen` 30-1). */
@Composable
private fun StorageNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VlrDimensions.Space4, vertical = VlrDimensions.Space2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_info),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = VlrTheme.colors.muted,
        )
        Text(
            text = "즐겨찾기는 이 기기에만 저장됩니다.",
            modifier = Modifier.weight(1f),
            style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
            color = VlrTheme.colors.muted,
        )
    }
}

@Composable
private fun FavoritePlayerRow(
    favorite: FavoritePlayer,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val openDetail = remember(favorite.id, onClick) { { onClick(favorite.id) } }
    val removeFavorite = remember(favorite.id, onRemove) { { onRemove(favorite.id) } }
    IdentityRow(
        title = favorite.handle,
        meta = listOfNotNull(favorite.realName, favorite.countryName).joinToString(" · ").ifBlank { null },
        leading = {
            LogoSlot(
                imageUrl = favorite.imageUrl,
                kind = LogoSlotKind.Player,
                placeholderText = logoInitials(favorite.handle),
                imageTestTag = myPagePlayerImageTag(favorite.id),
                placeholderTestTag = myPagePlayerImagePlaceholderTag(favorite.id),
            )
        },
        onClick = openDetail,
        contentDescription = "선수 상세: ${favorite.handle}",
        trailing = IdentityRowTrailing.FavoriteRemove(
            contentDescription = "${favorite.handle} 즐겨찾기 해제",
            onClick = removeFavorite,
        ),
        modifier = Modifier.testTag(myPagePlayerRowTag(favorite.id)),
    )
}

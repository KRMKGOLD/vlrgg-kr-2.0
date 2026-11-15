package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
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
    val gridState = rememberLazyGridState()

    MyPageContent(
        uiState = uiState,
        gridState = gridState,
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
    gridState: LazyGridState,
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
                // Both team columns get the same pixel width: `GridCells.Fixed` gives an odd pixel to
                // the first column, which would wrap the two tiles' names differently.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val teamCellWidth = constraints.maxWidth / TeamTilesPerRow
                    // White sections on the canvas, 8dp apart (`.pen` 30-1). A two-column grid keeps
                    // every team tile and player row its own keyed item, so removing a favorite above
                    // the viewport does not move the tiles being viewed. Only team tiles take one cell.
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(TeamTilesPerRow),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(MY_PAGE_LIST_TAG),
                        contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
                    ) {
                        favoriteTeamsSection(
                            teams = uiState.favoriteTeams,
                        teamCellWidth = teamCellWidth,
                            onTeamClick = onTeamClick,
                            onRemoveTeam = onRemoveTeam,
                            onRetryTeams = onRetryTeams,
                        )
                        fullWidthItem(key = "favorite_sections_gap") {
                            Spacer(Modifier.height(VlrDimensions.Space2))
                        }
                        favoritePlayersSection(
                            players = uiState.favoritePlayers,
                            onPlayerClick = onPlayerClick,
                            onRemovePlayer = onRemovePlayer,
                            onRetryPlayers = onRetryPlayers,
                        )
                        fullWidthItem(key = "favorite_storage_note") {
                            StorageNote()
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

private fun LazyGridScope.favoriteTeamsSection(
    teams: FavoriteSectionState<FavoriteTeam>,
    teamCellWidth: Int,
    onTeamClick: (String) -> Unit,
    onRemoveTeam: (String) -> Unit,
    onRetryTeams: () -> Unit,
) {
    fullWidthItem(key = "favorite_teams_header") {
        SectionHeader(
            title = "팀",
            count = (teams as? FavoriteSectionState.Content)?.favorites?.size?.toString(),
            modifier = Modifier
                .testTag(MY_PAGE_TEAM_SECTION_TAG)
                .background(VlrTheme.colors.surface),
        )
    }
    when (teams) {
        FavoriteSectionState.Loading -> fullWidthItem(key = "favorite_teams_loading") {
            Row(
                modifier = Modifier
                    .sectionLoading("즐겨찾기한 팀을 불러오는 중")
                    .fillMaxWidth()
                    .background(VlrTheme.colors.surface)
                    .padding(start = VlrDimensions.Space4, end = VlrDimensions.Space4, top = VlrDimensions.Space1),
                horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                repeat(TeamTilesPerRow) { FavoriteTeamTileSkeleton(Modifier.weight(1f)) }
            }
        }

        FavoriteSectionState.Empty -> fullWidthItem(key = "favorite_teams_empty") {
            SectionMessage("즐겨찾기한 팀이 없습니다.", Modifier.background(VlrTheme.colors.surface))
        }

        FavoriteSectionState.Error -> fullWidthItem(key = "favorite_teams_error") {
            InlineRetry(
                message = "즐겨찾기한 팀을 불러오지 못했습니다.",
                onRetry = onRetryTeams,
                showBottomDivider = false,
                modifier = Modifier.background(VlrTheme.colors.surface),
            )
        }

        is FavoriteSectionState.Content -> {
            val favorites = teams.favorites
            itemsIndexed(
                items = favorites,
                key = { _, favorite -> "favorite_team_${favorite.id}" },
                span = { index, _ -> GridItemSpan(if (isAloneInRow(index, favorites.size)) maxLineSpan else 1) },
            ) { index, favorite ->
                TeamTileCell(index = index, count = favorites.size, cellWidth = teamCellWidth) {
                    FavoriteTeamTile(
                        favorite = favorite,
                        onClick = onTeamClick,
                        onRemove = onRemoveTeam,
                        rowPartner = if (isAloneInRow(index, favorites.size)) null else favorites[index xor 1],
                        modifier = Modifier
                            .weight(1f)
                            .testTag(myPageTeamRowTag(favorite.id)),
                    )
                }
            }
        }
    }
    fullWidthItem(key = "favorite_teams_bottom") {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(VlrDimensions.Space4)
                .background(VlrTheme.colors.surface),
        )
    }
}

private fun LazyGridScope.favoritePlayersSection(
    players: FavoriteSectionState<FavoritePlayer>,
    onPlayerClick: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onRetryPlayers: () -> Unit,
) {
    fullWidthItem(key = "favorite_players_header") {
        SectionHeader(
            title = "선수",
            count = (players as? FavoriteSectionState.Content)?.favorites?.size?.toString(),
            modifier = Modifier
                .testTag(MY_PAGE_PLAYER_SECTION_TAG)
                .background(VlrTheme.colors.surface),
        )
    }
    when (players) {
        FavoriteSectionState.Loading -> fullWidthItem(key = "favorite_players_loading") {
            Column(Modifier.sectionLoading("즐겨찾기한 선수를 불러오는 중")) {
                repeat(PlayerSkeletonRows) { PlayerRowSkeleton() }
            }
        }

        FavoriteSectionState.Empty -> fullWidthItem(key = "favorite_players_empty") {
            SectionMessage("즐겨찾기한 선수가 없습니다.", Modifier.background(VlrTheme.colors.surface))
        }

        FavoriteSectionState.Error -> fullWidthItem(key = "favorite_players_error") {
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
            span = { GridItemSpan(maxLineSpan) },
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

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)

/** An odd last team spans the full line and keeps half its width, so its surface reaches both edges. */
private fun isAloneInRow(index: Int, count: Int): Boolean =
    index == count - 1 && index % TeamTilesPerRow == 0

/**
 * Grid cell around one team tile: 16dp screen insets, 8dp between the two columns (4dp on each
 * inner side), 4dp under the header for the first line and 8dp between later lines. A cell wider
 * than [cellWidth] (the first column on an odd pixel width) moves the extra pixel into its outer
 * inset, so the tiles of a row are laid out at the same width.
 */
@Composable
private fun TeamTileCell(
    index: Int,
    count: Int,
    cellWidth: Int,
    tile: @Composable RowScope.() -> Unit,
) {
    val alone = isAloneInRow(index, count)
    val isStartColumn = index % TeamTilesPerRow == 0
    val halfGap = VlrDimensions.Space2 / 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .then(if (alone) Modifier else Modifier.evenCellWidth(cellWidth))
            .padding(
                start = if (isStartColumn) VlrDimensions.Space4 else halfGap,
                end = if (isStartColumn && !alone) halfGap else VlrDimensions.Space4,
                top = if (index < TeamTilesPerRow) VlrDimensions.Space1 else VlrDimensions.Space2,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        tile()
        if (alone) Spacer(Modifier.weight(1f))
    }
}

/** Lays the cell content out at [width] and leaves any extra pixels on the start (outer) side. */
private fun Modifier.evenCellWidth(width: Int): Modifier = layout { measurable, constraints ->
    val contentWidth = width.coerceIn(constraints.minWidth.coerceAtMost(width), constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = contentWidth, maxWidth = contentWidth))
    val extra = constraints.maxWidth - contentWidth
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(extra, 0) }
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

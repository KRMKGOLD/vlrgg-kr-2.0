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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kr.co.cotton.vlrgg_mobile.ui.component.FavoriteFailureSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.InlineRetry
import kr.co.cotton.vlrgg_mobile.ui.component.RootHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionMessage
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_star_fill

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
        containerColor = VlrTheme.colors.surface,
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
                        .testTag(MY_PAGE_FULL_ERROR_TAG),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MY_PAGE_LIST_TAG),
                    contentPadding = PaddingValues(bottom = VlrDimensions.Space6),
                    verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
                ) {
                    item(key = "favorite_teams_header") {
                        SectionHeader("Favorite Teams", Modifier.testTag(MY_PAGE_TEAM_SECTION_TAG))
                    }
                    when (val teams = uiState.favoriteTeams) {
                        FavoriteSectionState.Loading -> item(key = "favorite_teams_loading") {
                            SectionCard(
                                modifier = Modifier.padding(horizontal = VlrDimensions.Space4).semantics {
                                    contentDescription = "즐겨찾기한 팀을 불러오는 중"
                                },
                            ) { SectionLoading("즐겨찾기한 팀을 불러오는 중") }
                        }

                        FavoriteSectionState.Empty -> item(key = "favorite_teams_empty") {
                            SectionMessage("즐겨찾기한 팀이 없습니다.")
                        }

                        FavoriteSectionState.Error -> item(key = "favorite_teams_error") {
                            InlineRetry(
                                message = "즐겨찾기한 팀을 불러오지 못했습니다.",
                                onRetry = onRetryTeams,
                                showBottomDivider = false,
                            )
                        }

                        is FavoriteSectionState.Content -> items(
                            items = teams.favorites,
                            key = { favorite -> "favorite_team_${favorite.id}" },
                        ) { favorite ->
                            SectionCard(Modifier.padding(horizontal = VlrDimensions.Space4)) {
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
                        SectionHeader("Favorite Players", Modifier.testTag(MY_PAGE_PLAYER_SECTION_TAG))
                    }
                    when (val players = uiState.favoritePlayers) {
                        FavoriteSectionState.Loading -> item(key = "favorite_players_loading") {
                            SectionCard(
                                modifier = Modifier.padding(horizontal = VlrDimensions.Space4).semantics {
                                    contentDescription = "즐겨찾기한 선수를 불러오는 중"
                                },
                            ) { SectionLoading("즐겨찾기한 선수를 불러오는 중") }
                        }

                        FavoriteSectionState.Empty -> item(key = "favorite_players_empty") {
                            SectionMessage("즐겨찾기한 선수가 없습니다.")
                        }

                        FavoriteSectionState.Error -> item(key = "favorite_players_error") {
                            InlineRetry(
                                message = "즐겨찾기한 선수를 불러오지 못했습니다.",
                                onRetry = onRetryPlayers,
                                showBottomDivider = false,
                            )
                        }

                        is FavoriteSectionState.Content -> items(
                            items = players.favorites,
                            key = { favorite -> "favorite_player_${favorite.id}" },
                        ) { favorite ->
                            SectionCard(Modifier.padding(horizontal = VlrDimensions.Space4)) {
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
            color = VlrTheme.colors.accent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(VlrDimensions.Space3))
        Text(
            text = description,
            style = VlrTheme.typography.bodySm,
            color = VlrTheme.colors.muted,
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
        imageUrl = favorite.imageUrl,
        imageTag = myPageTeamImageTag(favorite.id),
        imagePlaceholderTag = myPageTeamImagePlaceholderTag(favorite.id),
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
        imageUrl = favorite.imageUrl,
        imageTag = myPagePlayerImageTag(favorite.id),
        imagePlaceholderTag = myPagePlayerImagePlaceholderTag(favorite.id),
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
    imageUrl: String? = null,
    imageTag: String? = null,
    imagePlaceholderTag: String? = null,
    rowDescription: String,
    removalDescription: String,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDetail = remember(id, onClick) { { onClick(id) } }
    val removeFavorite = remember(id, onRemove) { { onRemove(id) } }
    val nonBlankImageUrl = imageUrl?.takeIf(String::isNotBlank)
    var imageFailed by remember(nonBlankImageUrl) { mutableStateOf(false) }

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
                .background(VlrTheme.colors.canvas),
        ) {
            if (nonBlankImageUrl != null && !imageFailed) {
                AsyncImage(
                    model = nonBlankImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(imageTag?.let(Modifier::testTag) ?: Modifier),
                )
            } else if (imagePlaceholderTag != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(imagePlaceholderTag),
                )
            }
        }
        Spacer(Modifier.width(VlrDimensions.Space3))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        ) {
            Text(
                text = title,
                style = VlrTheme.typography.body,
                color = VlrTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = VlrTheme.typography.label,
                    color = VlrTheme.colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        VlrIconButton(
            contentDescription = removalDescription,
            onClick = removeFavorite,
            tint = VlrTheme.colors.accent,
            icon = {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_star_fill),
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
        shape = RoundedCornerShape(VlrDimensions.CornerRadius),
        color = VlrTheme.colors.surface,
        border = BorderStroke(VlrDimensions.OutlineWidth, VlrTheme.colors.line),
        content = content,
    )
}

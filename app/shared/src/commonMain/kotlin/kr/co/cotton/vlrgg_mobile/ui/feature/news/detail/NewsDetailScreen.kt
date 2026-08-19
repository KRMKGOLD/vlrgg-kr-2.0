package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun NewsDetailScreen(
    articleId: String,
    slug: String,
    onBack: () -> Unit,
    onTeamClick: (teamId: String) -> Unit,
    onPlayerClick: (playerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<NewsDetailViewModel, NewsDetailViewModel.Factory>(
        key = "$articleId/$slug",
    ) {
        create(articleId, slug)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NewsDetailContent(
        uiState = uiState,
        onBack = onBack,
        onTeamClick = onTeamClick,
        onPlayerClick = onPlayerClick,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

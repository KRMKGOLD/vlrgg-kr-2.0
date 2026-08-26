package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search

@Composable
fun MyPageScreen(
    onSearch: () -> Unit,
    viewModel: MyPageViewModel = metroViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyPageContent(
        uiState = uiState,
        onSearch = onSearch,
        modifier = modifier,
    )
}

@Composable
fun MyPageContent(
    uiState: MyPageUiState,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = VlrDimensions.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "My Page",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (uiState.isSignedIn) "Signed in" else "Account unavailable",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

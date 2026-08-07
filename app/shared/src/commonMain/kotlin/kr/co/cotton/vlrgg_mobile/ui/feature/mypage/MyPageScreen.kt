package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun MyPageScreen(
    viewModel: MyPageViewModel = metroViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyPageContent(
        uiState = uiState,
        modifier = modifier,
    )
}

@Composable
fun MyPageContent(
    uiState: MyPageUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "MyPage",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = if (uiState.isSignedIn) "Signed in" else "Account unavailable",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

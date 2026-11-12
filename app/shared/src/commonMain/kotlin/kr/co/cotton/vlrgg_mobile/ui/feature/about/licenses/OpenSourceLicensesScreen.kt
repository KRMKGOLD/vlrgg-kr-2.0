package kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.ui.component.DetailHeader
import kr.co.cotton.vlrgg_mobile.ui.component.StatePanel
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back

internal const val OPEN_SOURCE_LICENSES_TITLE = "오픈소스 라이선스"
internal const val LICENSE_DIALOG_CONFIRM_TEXT = "확인"

@Composable
fun OpenSourceLicensesScreen(
    readLicenseDefinitions: suspend () -> String,
    onBack: () -> Unit,
    viewModel: OpenSourceLicensesViewModel = metroViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.load(readLicenseDefinitions)
    }

    OpenSourceLicensesContent(
        uiState = uiState,
        onBack = onBack,
        modifier = modifier,
    )
}

/** AboutLibraries' stock list and license dialog under the app's Back + title bar. */
@Composable
internal fun OpenSourceLicensesContent(
    uiState: OpenSourceLicensesUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VlrTheme.colors.surface,
        topBar = { OpenSourceLicensesTopBar(onBack) },
    ) { contentPadding ->
        if (uiState.isLoadFailed) {
            StatePanel(
                title = "오픈소스 라이선스 정보를 불러올 수 없습니다.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else if (uiState.libraries == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .semantics { stateDescription = "로딩 중" },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = VlrTheme.colors.accent,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            LibrariesContainer(
                libraries = uiState.libraries,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                licenseDialogConfirmText = LICENSE_DIALOG_CONFIRM_TEXT,
            )
        }
    }
}

@Composable
private fun OpenSourceLicensesTopBar(onBack: () -> Unit) {
    DetailHeader(onBack = onBack, title = OPEN_SOURCE_LICENSES_TITLE)
}

package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
fun AboutScreen(
    platform: AboutPlatform,
    onSearch: () -> Unit,
    viewModel: AboutViewModel = metroViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(platform) {
        viewModel.updateBuildVersion(platform.buildVersion)
    }

    AboutContent(
        uiState = uiState,
        onSearch = onSearch,
        onSourceClick = {
            platform.openUrl(ABOUT_SOURCE_URL) { opened ->
                viewModel.onSourceOpenResult(opened)
            }
        },
        onCopySourceClick = {
            viewModel.onSourceCopyResult(platform.copyText(ABOUT_SOURCE_URL))
        },
        onDismissFeedback = viewModel::dismissFeedback,
        modifier = modifier,
    )
}

@Composable
internal fun AboutContent(
    uiState: AboutUiState,
    onSearch: () -> Unit,
    onSourceClick: () -> Unit,
    onCopySourceClick: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VlrTheme.colors.surface,
        topBar = { AboutTopAppBar(onSearch) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VlrDimensions.Space4)
                    .padding(top = VlrDimensions.Space4, bottom = VlrDimensions.Space4),
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
            ) {
                AppIdentitySection(uiState.versionLabel)
                SourceCodeSection(onSourceClick)
                ThemeSection()
                AttributionSection()
            }

            AboutFeedbackSnackbar(
                feedback = uiState.feedback,
                onCopySourceClick = onCopySourceClick,
                onDismissFeedback = onDismissFeedback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = VlrDimensions.Space4)
                    .padding(bottom = VlrDimensions.Space3),
            )
        }
    }
}

@Composable
private fun AboutTopAppBar(onSearch: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = VlrDimensions.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "About",
                modifier = Modifier.weight(1f),
                style = VlrTheme.typography.pageTitle,
                color = VlrTheme.colors.actionPrimary,
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
private fun AppIdentitySection(versionLabel: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Text(
            text = "VLR.GG Mobile 2.0",
            style = VlrTheme.typography.display,
            color = VlrTheme.colors.textPrimary,
        )
        Text(
            text = "발로란트 e스포츠의 모든 정보와 경기 결과를 가장 빠르고 정확하게 전달합니다.",
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
        )
        Surface(
            border = BorderStroke(VlrDimensions.OutlineWidth, VlrTheme.colors.outline),
            color = VlrTheme.colors.surfaceSubtle,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        ) {
            Text(
                text = versionLabel,
                modifier = Modifier.padding(horizontal = VlrDimensions.Space2, vertical = VlrDimensions.Space1),
                style = VlrTheme.typography.labelSmall,
                color = VlrTheme.colors.textSecondary,
            )
        }
        HorizontalDivider(color = VlrTheme.colors.outline)
    }
}

@Composable
private fun SourceCodeSection(onSourceClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VlrDimensions.MinimumTouchTarget)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Source Code 외부 링크 열기",
                    onClick = onSourceClick,
                )
                .semantics { contentDescription = "Source Code 외부 링크 열기" }
                .padding(vertical = VlrDimensions.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Source Code", style = VlrTheme.typography.bodyStrong, color = VlrTheme.colors.textPrimary)
                Text(
                    "github.com/KRMKGOLD/vlrgg-kr-2.0",
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
            Box(
                modifier = Modifier.size(VlrDimensions.MinimumTouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↗",
                    style = VlrTheme.typography.sectionTitle,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        }
        HorizontalDivider(color = VlrTheme.colors.outline)
    }
}

@Composable
private fun ThemeSection() {
    AboutInfoSection(
        title = "Current Theme · Light",
        body = "다크 모드는 추후 업데이트될 예정입니다.",
        withDivider = true,
    )
}

@Composable
private fun AttributionSection() {
    AboutInfoSection(
        title = "Data Source: VLR.GG",
        body = "이 앱은 비공식 개인 프로젝트로 운영됩니다.",
        withDivider = false,
    )
}

@Composable
private fun AboutInfoSection(
    title: String,
    body: String,
    withDivider: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
    ) {
        Text(title, style = VlrTheme.typography.bodyStrong, color = VlrTheme.colors.textPrimary)
        Text(body, style = VlrTheme.typography.body, color = VlrTheme.colors.textSecondary)
        if (withDivider) HorizontalDivider(
            modifier = Modifier.padding(top = VlrDimensions.Space3),
            color = VlrTheme.colors.outline,
        )
    }
}

@Composable
private fun AboutFeedbackSnackbar(
    feedback: AboutFeedback?,
    onCopySourceClick: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (feedback) {
        AboutFeedback.SourceLinkError -> Snackbar(
            modifier = modifier,
            action = {
                Text(
                    text = "링크 복사",
                    modifier = Modifier
                        .heightIn(min = VlrDimensions.MinimumTouchTarget)
                        .clickable(role = Role.Button, onClick = onCopySourceClick)
                        .padding(horizontal = VlrDimensions.Space2),
                    style = VlrTheme.typography.bodyStrong,
                    color = VlrTheme.colors.actionPrimary,
                )
            },
            containerColor = VlrTheme.colors.textPrimary,
            contentColor = VlrTheme.colors.surface,
        ) { Text("소스 코드를 열 수 없습니다.") }

        AboutFeedback.SourceLinkCopied -> Snackbar(
            modifier = modifier
                .clickable(role = Role.Button, onClickLabel = "안내 닫기", onClick = onDismissFeedback),
            containerColor = VlrTheme.colors.textPrimary,
            contentColor = VlrTheme.colors.surface,
        ) { Text("링크를 복사했습니다.") }

        null -> Unit
    }
}

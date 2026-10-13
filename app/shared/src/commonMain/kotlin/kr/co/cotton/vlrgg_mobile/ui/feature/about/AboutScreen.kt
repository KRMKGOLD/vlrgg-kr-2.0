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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.ui.component.VlrIconButton
import kr.co.cotton.vlrgg_mobile.ui.component.VlrSnackbar
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_search
import kotlinx.coroutines.delay

internal const val AboutLinkErrorBaseDurationMillis = 4_000L

@Composable
fun AboutScreen(
    platform: AboutPlatform,
    onSearch: () -> Unit,
    viewModel: AboutViewModel = metroViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val linkOpenCallbackGate = remember(platform) { AboutLinkOpenCallbackGate() }
    val accessibilityManager = LocalAccessibilityManager.current

    LaunchedEffect(platform) {
        viewModel.updateBuildVersion(platform.buildVersion)
    }
    DisposableEffect(linkOpenCallbackGate) {
        onDispose(linkOpenCallbackGate::dispose)
    }
    DisposableEffect(Unit) {
        onDispose(viewModel::dismissFeedback)
    }
    LaunchedEffect(uiState.failedLink, uiState.linkErrorId, accessibilityManager) {
        if (uiState.failedLink != null) {
            delay(
                accessibilityManager?.calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis = AboutLinkErrorBaseDurationMillis,
                    containsIcons = false,
                    containsText = true,
                    containsControls = false,
                ) ?: AboutLinkErrorBaseDurationMillis,
            )
            viewModel.dismissFeedback()
        }
    }

    AboutContent(
        uiState = uiState,
        onSearch = onSearch,
        onLinkClick = { link ->
            val attemptId = linkOpenCallbackGate.beginAttempt()
            platform.openUrl(link.url) { opened ->
                if (linkOpenCallbackGate.accepts(attemptId)) {
                    viewModel.onLinkOpenResult(link, opened)
                }
            }
        },
        modifier = modifier,
    )
}

/** Keeps one About composition from accepting callbacks after navigation away. */
internal class AboutLinkOpenCallbackGate {
    private var active = true
    private var latestAttemptId = 0L

    fun beginAttempt(): Long {
        latestAttemptId += 1
        return latestAttemptId
    }

    fun accepts(attemptId: Long): Boolean = active && attemptId == latestAttemptId

    fun dispose() {
        active = false
    }
}

@Composable
internal fun AboutContent(
    uiState: AboutUiState,
    onSearch: () -> Unit,
    onLinkClick: (AboutLink) -> Unit,
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
                ProjectLinksSection(onLinkClick)
                ThemeSection()
                AttributionSection()
            }

            uiState.failedLink?.let { link ->
                VlrSnackbar(
                    message = link.openFailureMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = VlrDimensions.Space3),
                )
            }
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
private fun AppIdentitySection(versionLabel: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
    ) {
        Text(
            text = "VLR.GG Mobile (Unofficial)",
            style = VlrTheme.typography.display,
            color = VlrTheme.colors.textPrimary,
        )
        Text(
            text = "VLR.GG에 공개된 발로란트 e스포츠 경기·대회·팀·선수 정보를 모바일에서 보기 편하게 정리한 비공식 앱입니다.",
            style = VlrTheme.typography.body,
            color = VlrTheme.colors.textSecondary,
        )
        versionLabel?.let { label ->
            Surface(
                border = BorderStroke(VlrDimensions.OutlineWidth, VlrTheme.colors.outline),
                color = VlrTheme.colors.surfaceSubtle,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = VlrDimensions.Space2, vertical = VlrDimensions.Space1),
                    style = VlrTheme.typography.labelSmall,
                    color = VlrTheme.colors.textSecondary,
                )
            }
        }
        HorizontalDivider(color = VlrTheme.colors.outline)
    }
}

@Composable
private fun ProjectLinksSection(onLinkClick: (AboutLink) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AboutLink.entries.forEach { link ->
            AboutLinkRow(link = link, onClick = { onLinkClick(link) })
        }
    }
}

@Composable
private fun AboutLinkRow(
    link: AboutLink,
    onClick: () -> Unit,
) {
    val accessibilityLabel = "${link.title} 외부 링크 열기"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VlrDimensions.MinimumTouchTarget)
                .clickable(
                    role = Role.Button,
                    onClickLabel = accessibilityLabel,
                    onClick = onClick,
                )
                .semantics { contentDescription = accessibilityLabel }
                .padding(vertical = VlrDimensions.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(link.title, style = VlrTheme.typography.bodyStrong, color = VlrTheme.colors.textPrimary)
                Text(
                    link.destinationLabel,
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

private val AboutLink.title: String
    get() = when (this) {
        AboutLink.SourceCode -> "소스 코드"
        AboutLink.PrivacyPolicy -> "개인정보처리방침"
        AboutLink.Contact -> "문의"
    }

private val AboutLink.destinationLabel: String
    get() = when (this) {
        AboutLink.SourceCode -> "github.com/KRMKGOLD/vlrgg-kr-2.0"
        AboutLink.PrivacyPolicy -> "GitHub · docs/privacy-policy.md"
        AboutLink.Contact -> "GitHub Issues"
    }

private val AboutLink.openFailureMessage: String
    get() = when (this) {
        AboutLink.SourceCode -> "소스 코드를 열 수 없습니다."
        AboutLink.PrivacyPolicy -> "개인정보처리방침을 열 수 없습니다."
        AboutLink.Contact -> "문의 페이지를 열 수 없습니다."
    }

@Composable
private fun ThemeSection() {
    AboutInfoSection(
        title = "현재 테마 · 라이트",
        body = "다크 모드는 추후 업데이트될 예정입니다.",
        withDivider = true,
    )
}

@Composable
private fun AttributionSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        AboutInfoSection(
            title = "데이터 출처: VLR.GG",
            body = "경기·대회·팀·선수 정보는 VLR.GG에 공개된 데이터를 가져와 정리합니다.",
            withDivider = false,
        )
        AboutInfoSection(
            title = "비공식 앱 고지",
            body = ABOUT_UNAFFILIATED_NOTICE,
            withDivider = false,
        )
        AboutInfoSection(
            title = "상표 고지",
            body = ABOUT_RIOT_TRADEMARK_NOTICE,
            withDivider = false,
        )
    }
}

internal const val ABOUT_UNAFFILIATED_NOTICE =
    "이 앱은 개인이 만든 비공식 앱입니다. VLR.GG 및 Riot Games와 제휴하지 않았으며, " +
        "VLR.GG 또는 Riot Games의 승인이나 후원을 받지 않았습니다."

internal const val ABOUT_RIOT_TRADEMARK_NOTICE =
    "VLR.GG Mobile (Unofficial)은 Riot Games의 보증을 받지 않았으며 Riot Games 또는 " +
        "Riot Games 자산의 제작·관리에 공식적으로 관여하는 누구의 견해나 의견도 반영하지 않습니다. " +
        "Riot Games, VALORANT(발로란트) 및 모든 관련 자산은 Riot Games, Inc.의 상표 또는 등록 상표입니다."

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

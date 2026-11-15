package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kr.co.cotton.vlrgg_mobile.ui.component.RootHeader
import kr.co.cotton.vlrgg_mobile.ui.component.SectionHeader
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChip
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kr.co.cotton.vlrgg_mobile.ui.component.VlrSnackbar
import kr.co.cotton.vlrgg_mobile.ui.component.rowDivider
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kr.co.cotton.vlrgg_mobile.ui.theme.withKoreanWordBreak
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.app_icon
import vlrggmobile.app.shared.generated.resources.ic_arrow_outward
import vlrggmobile.app.shared.generated.resources.ic_chevron_right
import vlrggmobile.app.shared.generated.resources.ic_code
import vlrggmobile.app.shared.generated.resources.ic_description
import vlrggmobile.app.shared.generated.resources.ic_mail
import vlrggmobile.app.shared.generated.resources.ic_privacy_tip
import kotlinx.coroutines.delay

internal const val AboutLinkErrorBaseDurationMillis = 4_000L

@Composable
fun AboutScreen(
    platform: AboutPlatform,
    onSearch: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
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
        onOpenSourceLicensesClick = onOpenSourceLicensesClick,
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
    onOpenSourceLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VlrTheme.colors.canvas,
        topBar = { RootHeader(title = "정보", onSearch = onSearch) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Flat white sections separated by 8dp of canvas (`.pen` 70-1).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space2),
            ) {
                AppIdentitySection(uiState.versionLabel)
                ProjectLinksSection(onLinkClick, onOpenSourceLicensesClick)
                ThemeSection()
                AttributionSection()
            }

            uiState.failedLink?.let { link ->
                VlrSnackbar(
                    message = link.openFailureMessage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

internal const val ABOUT_INTRODUCTION =
    "VLR.GG에 공개된 발로란트 e스포츠 경기·대회·팀·선수 정보를 모바일에서 보기 편하게 정리한 비공식 앱입니다."

@Composable
private fun AppIdentitySection(versionLabel: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space3,
                bottom = VlrDimensions.Space6,
            ),
        verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space4),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconShape = RoundedCornerShape(AppIconCornerRadius)
            // Decorative: the app name next to it carries the identity.
            Image(
                imageVector = vectorResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(AppIconSize)
                    .clip(iconShape)
                    .border(VlrDimensions.OutlineWidth, VlrTheme.colors.line, iconShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
            ) {
                Text(
                    text = ABOUT_APP_NAME,
                    style = VlrTheme.typography.display.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    color = VlrTheme.colors.ink,
                )
                versionLabel?.let { label ->
                    StatusChip(
                        status = StatusChipStatus.Version,
                        label = label,
                        modifier = Modifier.testTag(ABOUT_VERSION_CHIP_TAG),
                    )
                }
            }
        }
        Text(
            text = ABOUT_INTRODUCTION,
            style = VlrTheme.typography.bodySm.copy(lineHeight = 22.4.sp).withKoreanWordBreak(),
            color = VlrTheme.colors.ink2,
        )
    }
}

internal const val ABOUT_APP_NAME = "VLR.GG Mobile (Unofficial)"
internal const val ABOUT_VERSION_CHIP_TAG = "about-version-chip"
private val AppIconSize = 56.dp
private val AppIconCornerRadius = 12.dp

@Composable
private fun ProjectLinksSection(
    onLinkClick: (AboutLink) -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface),
    ) {
        SectionHeader(title = "프로젝트")
        AboutLink.entries.forEach { link ->
            AboutLinkRow(
                icon = link.icon,
                title = link.title,
                detail = link.destinationLabel,
                accessibilityLabel = link.accessibilityLabel,
                isExternal = true,
                onClick = { onLinkClick(link) },
            )
        }
        AboutLinkRow(
            icon = Res.drawable.ic_description,
            title = "오픈소스 라이선스",
            detail = "앱에 포함된 라이브러리",
            accessibilityLabel = ABOUT_OPEN_SOURCE_LICENSES_LABEL,
            isExternal = false,
            onClick = onOpenSourceLicensesClick,
        )
    }
}

internal const val ABOUT_OPEN_SOURCE_LICENSES_LABEL = "오픈소스 라이선스 화면 열기"
internal const val ABOUT_EXTERNAL_LINK_ICON_TAG = "about-link-external"
internal const val ABOUT_INTERNAL_LINK_ICON_TAG = "about-link-internal"

/**
 * Link row (`DESIGN.md` 8.2): leading icon, title and detail, then `arrow_outward` for a destination
 * outside the app or `chevron_right` for an in-app screen. One button node named by [accessibilityLabel].
 */
@Composable
private fun AboutLinkRow(
    icon: DrawableResource,
    title: String,
    detail: String,
    accessibilityLabel: String,
    isExternal: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VlrDimensions.MinimumTouchTarget)
            .rowDivider(VlrTheme.colors.line)
            .clickable(
                role = Role.Button,
                onClickLabel = accessibilityLabel,
                onClick = onClick,
            )
            .semantics { contentDescription = accessibilityLabel }
            .padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space1,
                top = VlrDimensions.Space2,
                bottom = VlrDimensions.Space2,
            ),
        horizontalArrangement = Arrangement.spacedBy(VlrDimensions.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = VlrTheme.colors.ink2,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(title, style = VlrTheme.typography.body, color = VlrTheme.colors.ink)
            Text(
                detail,
                style = VlrTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                color = VlrTheme.colors.muted,
            )
        }
        Box(
            modifier = Modifier.size(VlrDimensions.MinimumTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vectorResource(
                    if (isExternal) Res.drawable.ic_arrow_outward else Res.drawable.ic_chevron_right,
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .testTag(if (isExternal) ABOUT_EXTERNAL_LINK_ICON_TAG else ABOUT_INTERNAL_LINK_ICON_TAG),
                tint = VlrTheme.colors.muted,
            )
        }
    }
}

private val AboutLink.icon: DrawableResource
    get() = when (this) {
        AboutLink.SourceCode -> Res.drawable.ic_code
        AboutLink.PrivacyPolicy -> Res.drawable.ic_privacy_tip
        AboutLink.Contact -> Res.drawable.ic_mail
    }

private val AboutLink.title: String
    get() = when (this) {
        AboutLink.SourceCode -> "소스 코드"
        AboutLink.PrivacyPolicy -> "개인정보처리방침"
        AboutLink.Contact -> "문의"
    }

private val AboutLink.destinationLabel: String
    get() = when (this) {
        AboutLink.SourceCode, AboutLink.PrivacyPolicy -> "GitHub에서 보기"
        AboutLink.Contact -> "teamcotton124@gmail.com"
    }

private val AboutLink.accessibilityLabel: String
    get() = when (this) {
        AboutLink.SourceCode, AboutLink.PrivacyPolicy -> "$title 외부 링크 열기"
        AboutLink.Contact -> "메일 앱에서 문의 메일 쓰기"
    }

private val AboutLink.openFailureMessage: String
    get() = when (this) {
        AboutLink.SourceCode -> "소스 코드를 열 수 없습니다."
        AboutLink.PrivacyPolicy -> "개인정보처리방침을 열 수 없습니다."
        AboutLink.Contact -> "메일 앱을 열 수 없습니다."
    }

@Composable
private fun ThemeSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .padding(bottom = VlrDimensions.Space5),
    ) {
        SectionHeader(title = "화면 테마")
        Column(
            modifier = Modifier.padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space1,
            ),
            verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1),
        ) {
            Text(
                text = "라이트",
                style = VlrTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = VlrTheme.colors.ink,
            )
            Text(
                text = ABOUT_THEME_NOTE,
                style = VlrTheme.typography.meta.copy(lineHeight = 20.sp).withKoreanWordBreak(),
                color = VlrTheme.colors.muted,
            )
        }
    }
}

internal const val ABOUT_THEME_NOTE = "현재 라이트 테마만 지원합니다. 다크 모드는 이후 업데이트에서 제공할 예정입니다."

@Composable
private fun AttributionSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VlrTheme.colors.surface)
            .padding(bottom = VlrDimensions.Space6),
    ) {
        SectionHeader(title = "출처와 고지")
        Column(
            modifier = Modifier.padding(
                start = VlrDimensions.Space4,
                end = VlrDimensions.Space4,
                top = VlrDimensions.Space1,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AboutNotice(
                title = "데이터 출처 · VLR.GG",
                body = "경기·대회·팀·선수 정보는 VLR.GG에 공개된 데이터를 가져와 정리합니다.",
            )
            AboutNotice(title = "비공식 앱 고지", body = ABOUT_UNAFFILIATED_NOTICE)
            AboutNotice(title = "상표 고지", body = ABOUT_RIOT_TRADEMARK_NOTICE)
        }
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
private fun AboutNotice(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VlrDimensions.Space1)) {
        Text(
            text = title,
            style = VlrTheme.typography.meta.copy(fontWeight = FontWeight.Bold),
            color = VlrTheme.colors.ink,
        )
        Text(
            text = body,
            style = VlrTheme.typography.meta.copy(lineHeight = 20.8.sp).withKoreanWordBreak(),
            color = VlrTheme.colors.ink2,
        )
    }
}

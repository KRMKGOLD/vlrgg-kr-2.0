package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrLightColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VlrListBlocksUiTest {
    @Test
    fun logoSlotFallsBackToSilentPlaceholdersAndHeroSizing() = runComposeUiTest {
        setContent {
            VlrTheme {
                Column {
                    LogoSlot(
                        imageUrl = " ",
                        kind = LogoSlotKind.Team,
                        placeholderText = "DRX",
                        modifier = Modifier.testTag("team-slot"),
                        placeholderTestTag = "team-placeholder",
                    )
                    LogoSlot(
                        imageUrl = null,
                        kind = LogoSlotKind.Event,
                        modifier = Modifier.testTag("event-slot"),
                        placeholderTestTag = "event-placeholder",
                    )
                    LogoSlot(
                        imageUrl = null,
                        kind = LogoSlotKind.Player,
                        placeholderText = logoInitials("Rb"),
                        size = LogoSlotDefaults.HeroSize,
                        surface = LogoSlotSurface.Dark,
                        modifier = Modifier.testTag("player-slot"),
                        placeholderTestTag = "player-placeholder",
                    )
                }
            }
        }

        onNodeWithTag("team-slot").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        val teamPlaceholder = onNodeWithTag("team-placeholder", useUnmergedTree = true)
        assertEquals("DRX", teamPlaceholder.text())
        assertTrue(teamPlaceholder.fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        assertEquals(14.sp, teamPlaceholder.textStyle().fontSize)
        assertEquals(VlrLightColors.muted, teamPlaceholder.textStyle().color)

        // Events and series fall back to the `trophy` icon, which carries no text or label.
        onNodeWithTag("event-placeholder", useUnmergedTree = true).assertExists()
        assertFalse(
            onNodeWithTag("event-placeholder", useUnmergedTree = true).fetchSemanticsNode().config
                .contains(SemanticsProperties.ContentDescription),
        )

        onNodeWithTag("player-slot").assertWidthIsEqualTo(72.dp)
        val playerPlaceholder = onNodeWithTag("player-placeholder", useUnmergedTree = true)
        assertEquals("RB", playerPlaceholder.text())
        assertEquals(22.sp, playerPlaceholder.textStyle().fontSize)
        assertEquals("KD", logoInitials("Kiwoom DRX"))
    }

    @Test
    fun identityRowTrailingMatchesItsTarget() = runComposeUiTest {
        var opened = 0
        var removed = 0
        setContent {
            VlrTheme {
                Column(Modifier.width(360.dp)) {
                    IdentityRow(
                        title = "KIWOOM DRX",
                        meta = "DRX · 대한민국",
                        leading = { LogoSlot(imageUrl = null, kind = LogoSlotKind.Team, placeholderText = "DRX") },
                        onClick = { opened += 1 },
                        contentDescription = "팀 상세: KIWOOM DRX",
                        modifier = Modifier.testTag("chevron-row"),
                    )
                    IdentityRow(
                        title = "Rb",
                        leading = { LogoSlot(imageUrl = null, kind = LogoSlotKind.Player, placeholderText = "RB") },
                        onClick = { opened += 10 },
                        contentDescription = "선수 상세: Rb",
                        trailing = IdentityRowTrailing.FavoriteRemove("Rb 즐겨찾기 해제", onClick = { removed += 1 }),
                        modifier = Modifier.testTag("favorite-row"),
                    )
                    IdentityRow(
                        title = "termi",
                        meta = "Head Coach",
                        leading = { LogoSlot(imageUrl = null, kind = LogoSlotKind.Player, placeholderText = "TE") },
                        modifier = Modifier.testTag("staff-row"),
                    )
                }
            }
        }

        onNodeWithTag("chevron-row").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened)
        onNodeWithContentDescription("팀 상세: KIWOOM DRX").assertIsDisplayed()

        // The remove star is its own 48dp target and never opens the detail.
        onNodeWithContentDescription("Rb 즐겨찾기 해제")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, removed)
        assertEquals(1, opened)
        onNodeWithContentDescription("선수 상세: Rb").performClick()
        assertEquals(11, opened)

        // Staff rows neither navigate nor reserve a trailing slot.
        assertFalse(onNodeWithTag("staff-row").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        onNodeWithText("Head Coach").assertIsDisplayed()
    }

    @Test
    fun finishedMatchRowEmphasizesTheWinnerAndGroupsTheScoresForAccessibility() = runComposeUiTest {
        var clicks = 0
        setContent {
            VlrTheme {
                Column(Modifier.width(360.dp)) {
                    MatchRow(
                        model = MatchRowModel(
                            home = MatchRowTeam("KIWOOM DRX", 2),
                            away = MatchRowTeam("Paper Rex", 1),
                            status = MatchRowStatus.Finished("오후 3:00"),
                            context = "VCT Pacific 2026 · Playoffs",
                            stateDescription = "종료된 경기",
                            scoreTestTag = "finished",
                        ),
                        onClick = { clicks += 1 },
                        modifier = Modifier.testTag("finished-row"),
                    )
                    MatchRow(
                        model = MatchRowModel(
                            home = MatchRowTeam("T1", null),
                            away = MatchRowTeam("Gen.G", null),
                            status = MatchRowStatus.Scheduled("오후 8:00", "1시간 32분 후"),
                            scoreTestTag = "scheduled",
                        ),
                        onClick = {},
                    )
                    MatchRow(
                        model = MatchRowModel(
                            home = MatchRowTeam("FNATIC", 1),
                            away = MatchRowTeam("Team Heretics", 0),
                            status = MatchRowStatus.Live,
                            scoreTestTag = "live",
                        ),
                        onClick = {},
                    )
                }
            }
        }

        onNodeWithContentDescription("경기 상세: KIWOOM DRX 2, Paper Rex 1")
            .assertHasClickAction()
            .assert(hasStateDescription("종료된 경기"))
            .performClick()
        assertEquals(1, clicks)
        onNodeWithTag("finished-row").assertHeightIsAtLeast(48.dp)

        val winnerScore = onNodeWithTag(matchRowHomeScoreTag("finished"), useUnmergedTree = true)
        val loserScore = onNodeWithTag(matchRowAwayScoreTag("finished"), useUnmergedTree = true)
        assertEquals(VlrLightColors.ink, winnerScore.textStyle().color)
        assertEquals(VlrLightColors.muted, loserScore.textStyle().color)
        assertEquals(20.sp, winnerScore.textStyle().fontSize)
        val winnerName = onNodeWithText("KIWOOM DRX", useUnmergedTree = true).textStyle()
        val loserName = onNodeWithText("Paper Rex", useUnmergedTree = true).textStyle()
        assertEquals(FontWeight.Bold, winnerName.fontWeight)
        assertEquals(VlrLightColors.ink, winnerName.color)
        assertEquals(FontWeight.Medium, loserName.fontWeight)
        assertEquals(VlrLightColors.muted, loserName.color)
        onNodeWithText("종료", useUnmergedTree = true).assertIsDisplayed()

        // Upcoming rows carry no score; the description names the teams instead.
        onNodeWithContentDescription("경기 상세: T1 대 Gen.G").assertIsDisplayed()
        onNodeWithTag(matchRowHomeScoreTag("scheduled"), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("1시간 32분 후", useUnmergedTree = true).assertIsDisplayed()

        // Live scores are shown without a winner.
        onNodeWithText("LIVE", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("경기 상세: FNATIC 1, Team Heretics 0").assertIsDisplayed()
        assertEquals(VlrLightColors.ink, onNodeWithTag(matchRowAwayScoreTag("live"), useUnmergedTree = true).textStyle().color)
    }

    @Test
    fun teamMatchRowShowsOutcomeAndStatusStyles() = runComposeUiTest {
        var clicks = 0
        setContent {
            VlrTheme {
                Column(Modifier.width(360.dp)) {
                    TeamMatchRow(
                        title = "Nongshim RedForce vs BBL Esports",
                        onClick = { clicks += 1 },
                        contentDescription = "경기 상세: Nongshim RedForce 2, BBL Esports 0",
                        context = "Esports World Cup 2026 · Playoffs",
                        status = TeamMatchRowStatus.Score("2 : 0"),
                        date = "2026/07/12",
                        outcome = TeamMatchOutcome.Win,
                        statusTestTag = "win-status",
                    )
                    TeamMatchRow(
                        title = "Nongshim RedForce vs T1",
                        onClick = {},
                        contentDescription = "경기 상세: Nongshim RedForce 대 T1",
                        status = TeamMatchRowStatus.Score("—", isMissing = true),
                        outcome = TeamMatchOutcome.Unknown,
                        statusTestTag = "unknown-status",
                    )
                    TeamMatchRow(
                        title = "vs Paper Rex",
                        onClick = {},
                        contentDescription = "경기 상세: KIWOOM DRX 대 Paper Rex",
                        status = TeamMatchRowStatus.Label("18시간 5분 후"),
                        statusTestTag = "upcoming-status",
                    )
                }
            }
        }

        onNodeWithContentDescription("경기 상세: Nongshim RedForce 2, BBL Esports 0")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, clicks)

        val win = onNodeWithText("승리", useUnmergedTree = true)
        win.assertWidthIsEqualTo(60.dp)
        assertEquals(VlrLightColors.accent, win.textStyle().color)
        assertEquals(FontWeight.ExtraBold, win.textStyle().fontWeight)
        assertEquals(VlrLightColors.muted, onNodeWithText("결과 미정", useUnmergedTree = true).textStyle().color)

        assertEquals(20.sp, onNodeWithTag("win-status", useUnmergedTree = true).textStyle().fontSize)
        assertEquals(VlrLightColors.ink, onNodeWithTag("win-status", useUnmergedTree = true).textStyle().color)
        assertEquals(VlrLightColors.muted, onNodeWithTag("unknown-status", useUnmergedTree = true).textStyle().color)
        val upcoming = onNodeWithTag("upcoming-status", useUnmergedTree = true).textStyle()
        assertEquals(13.sp, upcoming.fontSize)
        assertEquals(FontWeight.Bold, upcoming.fontWeight)

        // Team rows have no result column.
        onNodeWithText("패배", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun newsRowHeadlineVariantAndAccessibleTarget() = runComposeUiTest {
        var clicks = 0
        setContent {
            VlrTheme {
                Column(Modifier.width(360.dp)) {
                    NewsRow(
                        title = "KIWOOM DRX advance to the Pacific playoffs",
                        byline = newsByline("jenopelle", "September 19, 2026"),
                        onClick = { clicks += 1 },
                        headline = true,
                        contentDescription = "뉴스 상세: KIWOOM DRX advance to the Pacific playoffs",
                    )
                    NewsRow(
                        title = "purp0 searching for new team",
                        byline = newsByline(" ", "September 18, 2026"),
                        onClick = {},
                        bylineTestTag = "plain-byline",
                    )
                }
            }
        }

        onNodeWithContentDescription("뉴스 상세: KIWOOM DRX advance to the Pacific playoffs")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, clicks)
        assertEquals(20.sp, onNodeWithText("KIWOOM DRX advance to the Pacific playoffs", useUnmergedTree = true).textStyle().fontSize)
        val plainTitle = onNodeWithText("purp0 searching for new team", useUnmergedTree = true).textStyle()
        assertEquals(15.sp, plainTitle.fontSize)
        assertEquals(FontWeight.SemiBold, plainTitle.fontWeight)
        onNodeWithText("jenopelle · September 19, 2026", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("September 18, 2026", onNodeWithTag("plain-byline", useUnmergedTree = true).text())
        onNode(hasClickAction() and hasTextExactly("purp0 searching for new team")).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun detailTopBandRequestsLightIconsOnlyWhileShownAndUsesTheDarkStar() = runComposeUiTest {
        val applied = mutableListOf<Boolean>()
        val controller = StatusBarIconsController { applied += it }
        var showBand by mutableStateOf(true)
        var onTint = Color.Unspecified
        var offTint = Color.Unspecified
        setContent {
            VlrTheme {
                onTint = favoriteStarTint(isFavorite = true, iconColor = VlrTheme.colors.onPanel)
                offTint = favoriteStarTint(isFavorite = false, iconColor = VlrTheme.colors.onPanel)
                CompositionLocalProvider(LocalStatusBarIconsController provides controller) {
                    if (showBand) {
                        Column {
                            DetailTopBand(
                                onBack = {},
                                windowInsets = WindowInsets(0),
                                actions = { iconColor ->
                                    VlrIconButton(
                                        contentDescription = "즐겨찾기 해제",
                                        onClick = {},
                                        tint = favoriteStarTint(isFavorite = true, iconColor = iconColor),
                                        icon = { FavoriteStarIcon(isFavorite = true) },
                                    )
                                },
                            )
                            EntityHero(
                                name = "KIWOOM DRX",
                                meta = listOf("DRX · 대한민국"),
                                logo = {
                                    LogoSlot(
                                        imageUrl = null,
                                        kind = LogoSlotKind.Team,
                                        placeholderText = "DRX",
                                        size = LogoSlotDefaults.HeroSize,
                                        surface = LogoSlotSurface.Dark,
                                    )
                                },
                                modifier = Modifier.testTag("hero"),
                            )
                        }
                    }
                }
            }
        }

        assertEquals(listOf(true), applied)
        onNodeWithContentDescription("뒤로 가기").assertWidthIsEqualTo(48.dp)
        onNodeWithContentDescription("즐겨찾기 해제").assertHeightIsEqualTo(48.dp)
        val name = onNodeWithText("KIWOOM DRX").assert(isHeading())
        assertEquals(VlrLightColors.onPanel, name.textStyle().color)
        assertEquals(28.sp, name.textStyle().fontSize)
        assertEquals(VlrLightColors.onPanelMuted, onNodeWithText("DRX · 대한민국").textStyle().color)
        assertEquals(VlrLightColors.onPanelAccent, onTint)
        assertEquals(VlrLightColors.onPanel, offTint)

        showBand = false
        waitForIdle()
        assertEquals(listOf(true, false), applied)
    }

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style
    }

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun hasStateDescription(value: String) =
        androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun hasTextExactly(value: String) = androidx.compose.ui.test.hasText(value)
}

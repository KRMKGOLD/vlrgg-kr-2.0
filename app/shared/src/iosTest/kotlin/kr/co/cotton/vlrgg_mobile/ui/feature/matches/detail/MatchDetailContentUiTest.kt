package kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MatchDetailContentUiTest {

    @Test
    fun loadingKeepsBackAndSkeletonGeometryWithoutSampleMatchData() = runComposeUiTest {
        setContent { Fixture(MatchDetailUiState.Loading) }

        onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
        onNodeWithTag(MATCH_DETAIL_LOADING_TAG).assertExists()
        onNodeWithText(HOME_TEAM.name).assertDoesNotExist()
        onNodeWithText(EVENT.name).assertDoesNotExist()
        onNodeWithText("Maps").assertExists()
        onNodeWithText("Head to Head").assertExists()
    }

    @Test
    fun contentKeepsHeroThenMapsThenHeadToHeadWithNullableScoreMarkers() = runComposeUiTest {
        setContent { Fixture(MatchDetailUiState.Content(completedMatch)) }

        val heroTop = onNodeWithTag(MATCH_DETAIL_HERO_TAG).fetchSemanticsNode().boundsInRoot.top
        val mapsTop = onNodeWithTag(MATCH_DETAIL_MAPS_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        val headToHeadTop = onNodeWithTag(MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue(heroTop < mapsTop)
        assertTrue(mapsTop < headToHeadTop)

        onNodeWithText("완료됨").assertExists()
        onNodeWithTag(matchDetailMapTag("Lotus")).performScrollTo()
        onAllNodesWithText("0 - —").assertCountEquals(2)
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).performScrollTo()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).assertTextContains("0 - —")
        onNodeWithText("past match must not render").assertDoesNotExist()
        onNodeWithContentDescription("알림").assertDoesNotExist()
        onNodeWithContentDescription("즐겨찾기").assertDoesNotExist()
        onNodeWithText("구독").assertDoesNotExist()
    }

    @Test
    fun emptySectionsRemainIndependentFromSuccessfulHero() = runComposeUiTest {
        setContent {
            Fixture(
                MatchDetailUiState.Content(completedMatch.copy(maps = emptyList())),
            )
        }

        onNodeWithTag(matchDetailTeamTag("home")).assertExists()
        onNodeWithText("맵 정보가 없습니다").assertExists()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).performScrollTo()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).assertExists()

        setContent {
            Fixture(
                MatchDetailUiState.Content(completedMatch.copy(headToHead = emptyList())),
            )
        }

        onNodeWithTag(matchDetailMapTag("Lotus")).performScrollTo()
        onNodeWithTag(matchDetailMapTag("Lotus")).assertExists()
        onNodeWithTag(MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG).performScrollTo()
        onNodeWithText("상대 전적이 없습니다").assertExists()

        setContent {
            Fixture(
                MatchDetailUiState.Content(
                    completedMatch.copy(maps = emptyList(), headToHead = emptyList()),
                ),
            )
        }

        onNodeWithText("맵 정보가 없습니다").assertExists()
        onNodeWithTag(MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG).performScrollTo()
        onNodeWithText("상대 전적이 없습니다").assertExists()
    }

    @Test
    fun everySupportedStatusHasNonColorText() = runComposeUiTest {
        val expectations = listOf(
            MatchStatus.UPCOMING to "예정",
            MatchStatus.POSTPONED to "연기됨",
            MatchStatus.LIVE to "LIVE",
            MatchStatus.COMPLETED to "완료됨",
            MatchStatus.CANCELLED to "취소됨",
            MatchStatus.UNAVAILABLE to "정보 없음",
        )

        expectations.forEach { (status, label) ->
            setContent {
                Fixture(MatchDetailUiState.Content(completedMatch.copy(status = status)))
            }
            onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun terminalContentExplainsLimitedInformationAndNullIdsAreNotClickable() = runComposeUiTest {
        val terminalMatch = completedMatch.copy(
            status = MatchStatus.UNAVAILABLE,
            homeTeam = HOME_TEAM.copy(id = null),
            awayTeam = AWAY_TEAM.copy(id = null),
            event = EVENT.copy(id = null),
            homeScore = null,
            awayScore = null,
            maps = emptyList(),
            headToHead = emptyList(),
        )
        setContent { Fixture(MatchDetailUiState.Content(terminalMatch)) }

        onNodeWithText("이 경기는 제한된 정보만 제공됩니다").assertExists()
        onNodeWithTag(MATCH_DETAIL_EVENT_TAG).assert(!hasClickAction())
        onNodeWithTag(matchDetailTeamTag("home")).assert(!hasClickAction())
        onNodeWithTag(matchDetailTeamTag("away")).assert(!hasClickAction())
        onNodeWithTag(MATCH_DETAIL_MAPS_SECTION_TAG).performScrollTo()
        onNodeWithText("이 경기의 맵 정보는 제공되지 않습니다").assertExists()
        onNodeWithTag(MATCH_DETAIL_HEAD_TO_HEAD_SECTION_TAG).performScrollTo()
        onNodeWithText("이 경기의 상대 전적은 제공되지 않습니다").assertExists()
    }

    @Test
    fun identityAndHeadToHeadCallbacksReceiveExactIdsAndErrorOnlyOffersRetry() = runComposeUiTest {
        var eventId: String? = null
        var teamId: String? = null
        var matchId: String? = null
        var retries = 0
        var backs = 0
        setContent {
            Fixture(
                MatchDetailUiState.Content(completedMatch),
                onEventClick = { eventId = it },
                onTeamClick = { teamId = it },
                onMatchClick = { matchId = it },
                onBack = { backs += 1 },
                onRetry = { retries += 1 },
            )
        }

        onNodeWithTag(MATCH_DETAIL_EVENT_TAG).performClick()
        onNodeWithTag(matchDetailTeamTag("home")).performClick()
        onNodeWithTag(matchDetailTeamTag("away")).performClick()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).performScrollTo()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).performClick()
        assertEquals(EVENT.id, eventId)
        assertEquals(AWAY_TEAM.id, teamId)
        assertEquals(RELATED_MATCH.id, matchId)

        setContent {
            Fixture(
                MatchDetailUiState.Error,
                onBack = { backs += 1 },
                onRetry = { retries += 1 },
            )
        }
        onNodeWithText(HOME_TEAM.name).assertDoesNotExist()
        onNodeWithText("경기 정보를 불러오지 못했습니다").assertIsDisplayed()
        onNodeWithContentDescription("재시도").performClick()
        onNodeWithContentDescription("뒤로 가기").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun interactiveTargetsMeetMinimumSizeAndLongOfficialTextRemainsAvailable() = runComposeUiTest {
        val longHomeName = "대한민국 발로란트 공식 프로게임단 아주 긴 홈 팀 이름"
        val longAwayName = "Pacific Championship 공식 초장문 어웨이 팀 이름"
        val longEventName = "2026 발로란트 챔피언스 투어 퍼시픽 공식 국제 대회 결승전"
        val longDescription = "긴 한국어 경기 설명도 화면 폭을 넘어 레이아웃을 깨뜨리지 않고 안전하게 표시되어야 합니다."
        val longMatch = completedMatch.copy(
            homeTeam = HOME_TEAM.copy(name = longHomeName),
            awayTeam = AWAY_TEAM.copy(name = longAwayName),
            event = EVENT.copy(name = longEventName),
            description = longDescription,
        )

        setContent { Fixture(MatchDetailUiState.Content(longMatch)) }

        onNodeWithContentDescription("뒤로 가기").assertHeightIsAtLeast(48.dp)
        onNodeWithTag(MATCH_DETAIL_EVENT_TAG)
            .assertHeightIsAtLeast(48.dp)
            .assertTextContains(longEventName)
        onNodeWithTag(matchDetailTeamTag("home"))
            .assertHeightIsAtLeast(48.dp)
            .assertTextContains(longHomeName)
        onNodeWithTag(matchDetailTeamTag("away"))
            .assertHeightIsAtLeast(48.dp)
            .assertTextContains(longAwayName)
        onNodeWithText(longDescription).assertExists()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).performScrollTo()
        onNodeWithTag(matchDetailHeadToHeadTag(RELATED_MATCH.id)).assertHeightIsAtLeast(48.dp)
    }
}

@Composable
private fun Fixture(
    uiState: MatchDetailUiState,
    onBack: () -> Unit = {},
    onEventClick: (String) -> Unit = {},
    onTeamClick: (String) -> Unit = {},
    onMatchClick: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    VlrTheme {
        MatchDetailContent(
            uiState = uiState,
            listState = rememberLazyListState(),
            onBack = onBack,
            onEventClick = onEventClick,
            onTeamClick = onTeamClick,
            onMatchClick = onMatchClick,
            onRetry = onRetry,
        )
    }
}

private val HOME_TEAM = MatchTeam(name = "Paper Rex", id = "paper-rex")
private val AWAY_TEAM = MatchTeam(name = "FNATIC", id = "fnatic")
private val EVENT = MatchEvent(name = "VALORANT Champions Tour Pacific", series = "Stage 2", id = "vct-pacific")
private val RELATED_MATCH = RelatedMatch(
    id = "related-match",
    homeTeamName = "Paper Rex",
    awayTeamName = "FNATIC",
    homeScore = 0,
    awayScore = null,
)
private val completedMatch = MatchDetail(
    id = "match-45",
    status = MatchStatus.COMPLETED,
    timeLabel = "종료",
    relativeTimeLabel = null,
    scheduledAt = "2026-08-29 17:00",
    homeTeam = HOME_TEAM,
    awayTeam = AWAY_TEAM,
    homeScore = 0,
    awayScore = 2,
    event = EVENT,
    description = "정규 시즌 경기",
    seriesFormat = "Bo3",
    maps = listOf(MatchMap(name = "Lotus", homeScore = 0, awayScore = null)),
    headToHead = listOf(RELATED_MATCH),
    pastMatches = listOf(RELATED_MATCH.copy(id = "past-match", homeTeamName = "past match must not render")),
)

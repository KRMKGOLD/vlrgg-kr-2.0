package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail as MatchDetailModel
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchEvent
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchMap
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchStatus
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchTeam
import kr.co.cotton.vlrgg_mobile.domain.model.matches.RelatedMatch
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MATCH_DETAIL_HERO_TAG
import kr.co.cotton.vlrgg_mobile.ui.feature.matches.detail.MatchDetailViewModel

/**
 * D1's real screen contract, shared by every pre-existing MatchDetail navigation source.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
internal fun ComposeUiTest.assertRealMatchDetailDestination() {
    onNodeWithText("match_detail").assertDoesNotExist()
    onNodeWithTag("match_detail").assertDoesNotExist()
    onNodeWithTag(MATCH_DETAIL_HERO_TAG).assertExists()
}

internal fun fixtureMatchDetailFactory(
    repository: MatchRepository,
): MatchDetailViewModel.Factory = MatchDetailViewModel.Factory { matchId ->
    MatchDetailViewModel(repository, matchId)
}

internal class FixtureMatchRepository(
    private val detailFor: (String) -> MatchDetailModel = ::fixtureMatchDetail,
) : MatchRepository {
    val requestedIds = mutableListOf<String>()

    override suspend fun getUpcomingMatches(page: Int) = error("Match list is not used")

    override suspend fun getResults(page: Int) = error("Match list is not used")

    override suspend fun getMatchDetail(matchId: String): AppResult<MatchDetailModel> {
        requestedIds += matchId
        return AppResult.Success(detailFor(matchId))
    }
}

internal fun fixtureMatchDetail(matchId: String): MatchDetailModel = MatchDetailModel(
    id = matchId,
    status = MatchStatus.COMPLETED,
    timeLabel = "18:00",
    relativeTimeLabel = null,
    scheduledAt = "2026-08-29",
    homeTeam = MatchTeam(name = "Home $matchId", id = "home-$matchId"),
    awayTeam = MatchTeam(name = "Away $matchId", id = "away-$matchId"),
    homeScore = 2,
    awayScore = 1,
    event = MatchEvent(name = "Event $matchId", series = "Playoffs", id = "event-$matchId"),
    description = "Fixture match $matchId",
    seriesFormat = "Bo3",
    maps = listOf(MatchMap(name = "Haven", homeScore = 13, awayScore = 9)),
    headToHead = listOf(
        RelatedMatch(
            id = "related-$matchId",
            homeTeamName = "Home $matchId",
            awayTeamName = "Away $matchId",
            homeScore = 2,
            awayScore = 0,
        ),
    ),
    pastMatches = emptyList(),
)

package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kotlin.test.*

class MatchesServiceTest {
    private val parser = VlrMatchesParser()
    private val mapper = MatchesMapper()

    @Test
    fun `service maps fresh upstream content and does not retain a stale fallback`() = runBlocking {
        var requestCount = 0
        val scraper = object : MatchesScraper {
            override suspend fun fetchList(category: MatchListCategory, page: Int): ScrapedMatchesPage {
                requestCount += 1
                if (requestCount == 2) {
                    throw UpstreamNetworkFailure(Url("https://www.vlr.gg/matches"))
                }
                return ScrapedMatchesPage(Url("https://www.vlr.gg/matches"), fixtureHtml("upcoming.html"))
            }

            override suspend fun fetchDetail(matchId: String): ScrapedMatchDetail = error("not used")
        }
        val service = DefaultMatchesService(scraper, parser, mapper)

        val response = service.getMatches(MatchListCategory.UPCOMING, page = 1)
        assertEquals("709685", response.groups.single().matches.single().id)
        assertNull(response.groups.single().matches.single().homeTeam.imageUrl)
        assertNull(response.groups.single().matches.single().awayTeam.imageUrl)

        assertFailsWith<UpstreamNetworkFailure> { service.getMatches(MatchListCategory.UPCOMING, page = 1) }
        assertEquals(2, requestCount)
    }

    @Test
    fun `mapper preserves explicit status and only exposes app-facing fields`() {
        val response = mapper.toDetailResponse(
            MatchDetailSource(
                summary = MatchSummarySource(
                    id = "709685",
                    status = MatchStatusSource.CANCELLED,
                    timeLabel = "5:00 PM",
                    relativeTimeLabel = null,
                    homeTeam = MatchTeamSource("Alpha", id = "1", imageUrl = "https://owcdn.net/img/alpha.png"),
                    awayTeam = MatchTeamSource("Beta", id = "2"),
                    homeScore = null,
                    awayScore = null,
                    event = MatchEventSource("Event", null, id = "3"),
                ),
                scheduledAt = null,
                description = null,
                seriesFormat = null,
                maps = emptyList(),
                headToHead = emptyList(),
                pastMatches = emptyList(),
            ),
        )

        assertEquals(MatchStatus.CANCELLED, response.status)
        assertEquals("Alpha", response.homeTeam.name)
        assertEquals("1", response.homeTeam.id)
        assertEquals("https://owcdn.net/img/alpha.png", response.homeTeam.imageUrl)
        assertNull(response.awayTeam.imageUrl)
        assertEquals("Event", response.event.name)
        assertEquals("3", response.event.id)
    }
}

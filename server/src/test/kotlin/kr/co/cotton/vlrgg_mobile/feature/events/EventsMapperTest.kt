package kr.co.cotton.vlrgg_mobile.feature.events

import kr.co.cotton.vlrgg_mobile.feature.matches.MatchStatus
import kotlin.test.*

class EventsMapperTest {
    private val mapper = EventsMapper()

    @Test
    fun `mapper groups event lists and keeps absent source values absent`() {
        val response = mapper.toEventListResponse(
            EventListSource(
                events = listOf(
                    EventSummarySource("1", "Live", EventStatusSource.ONGOING, null, null, null),
                    EventSummarySource("2", "Next", EventStatusSource.UPCOMING, null, "kr", null),
                    EventSummarySource("3", "Done", EventStatusSource.COMPLETED, null, null, null),
                    EventSummarySource("4", "Paused", EventStatusSource.PAUSED, null, null, null),
                ),
            ),
        )

        assertEquals(listOf("1"), response.ongoing.map { it.id })
        assertEquals(listOf("2"), response.upcoming.map { it.id })
        assertEquals(listOf("3", "4"), response.completedOrPaused.map { it.id })
        assertNull(response.ongoing.single().dateLabel)
        assertNull(response.ongoing.single().regionCode)
    }

    @Test
    fun `mapper exposes only source stat values without inventing defaults`() {
        val response = mapper.toEventStatsResponse(
            EventStatsSource.Available(
                players = listOf(
                    EventPlayerStatsSource(
                        playerId = "10",
                        playerName = "Player One",
                        teamAbbreviation = null,
                        roundsPlayed = null,
                        rating = 1.25,
                        averageCombatScore = null,
                        killDeathRatio = null,
                        averageDamagePerRound = null,
                        killAssistSurvivedTradedPercentage = null,
                    ),
                ),
            ),
        )

        assertEquals(EventStatsAvailability.AVAILABLE, response.availability)
        assertNull(response.players.single().roundsPlayed)
        assertNull(response.players.single().teamAbbreviation)
        assertEquals(1.25, response.players.single().rating)
    }

    @Test
    fun `mapper reuses match summary status and event identity contract`() {
        val response = mapper.toEventMatchesResponse(
            EventMatchesSource(
                matches = listOf(
                    EventMatchSource(
                        id = "501",
                        status = EventMatchStatusSource.POSTPONED,
                        timeLabel = "TBD",
                        relativeTimeLabel = null,
                        homeTeam = EventMatchTeamSource("Alpha"),
                        awayTeam = EventMatchTeamSource("Bravo"),
                        homeScore = null,
                        awayScore = null,
                        event = EventMatchEventSource("Masters Seoul", null, "100"),
                    ),
                ),
            ),
        ).items.single()

        assertEquals(MatchStatus.POSTPONED, response.status)
        assertEquals("Masters Seoul", response.event.name)
        assertEquals("100", response.event.id)
        assertNull(response.event.series)
    }
}

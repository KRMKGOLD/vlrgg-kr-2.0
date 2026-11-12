package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamRosterMemberDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TeamMapperTest {

    @Test
    fun responseMapsAllTeamSectionsWithoutChangingOrder() {
        val team = teamDetailResponse().toDomain()

        assertEquals("8185", team.id)
        assertEquals("KIWOOM DRX", team.name)
        assertEquals("KRX", team.tag)
        assertEquals("South Korea", team.country)
        assertEquals("https://owcdn.net/img/drx.png", team.logoUrl)
        assertEquals(listOf("698887"), team.upcomingMatches.map { it.id })
        assertEquals(listOf("698100"), team.recentMatches.map { it.id })
        assertEquals(listOf("4462"), team.players.map { it.id })
        assertEquals(listOf("775"), team.staff.map { it.id })
        assertEquals("MaKo", team.players.single().handle)
        assertEquals("https://owcdn.net/img/mako.png", team.players.single().imageUrl)
        assertEquals(listOf("player"), team.players.single().roleLabels)
        assertEquals("termi", team.staff.single().handle)
        assertEquals("https://owcdn.net/img/termi.png", team.staff.single().imageUrl)
        assertEquals(listOf("head coach"), team.staff.single().roleLabels)
        assertEquals("700755", team.news.single().articleId)
        assertEquals("kiwoom-drx-releases-rookie-hermes", team.news.single().slug)
    }

    @Test
    fun everyEmptySectionRemainsEmpty() {
        val team = teamDetailResponse(
            upcomingMatches = emptyList(),
            recentMatches = emptyList(),
            players = emptyList(),
            staff = emptyList(),
            news = emptyList(),
        ).toDomain()

        assertEquals(emptyList(), team.upcomingMatches)
        assertEquals(emptyList(), team.recentMatches)
        assertEquals(emptyList(), team.players)
        assertEquals(emptyList(), team.staff)
        assertEquals(emptyList(), team.news)
    }

    @Test
    fun nullableMetadataAndEmptySectionsArePreserved() {
        val team = teamDetailResponse(
            tag = null,
            country = null,
            logoUrl = null,
            upcomingMatches = emptyList(),
            recentMatches = listOf(
                match(
                    id = "698100",
                    eventName = null,
                    eventStage = null,
                    statusText = null,
                    scheduledAtText = null,
                ),
            ),
            players = listOf(
                rosterMember(
                    id = "4462",
                    realName = null,
                    roleLabels = emptyList(),
                    imageUrl = null,
                ),
            ),
            staff = emptyList(),
            news = listOf(TeamNewsDto("700755/kiwoom-drx", "DRX news", null)),
        ).toDomain()

        assertEquals(null, team.tag)
        assertEquals(null, team.country)
        assertEquals(null, team.logoUrl)
        assertEquals(emptyList(), team.upcomingMatches)
        assertEquals(null, team.recentMatches.single().eventName)
        assertEquals(null, team.recentMatches.single().eventStage)
        assertEquals(null, team.recentMatches.single().statusText)
        assertEquals(null, team.recentMatches.single().scheduledAtText)
        assertEquals(null, team.players.single().realName)
        assertEquals(null, team.players.single().imageUrl)
        assertEquals(emptyList(), team.players.single().roleLabels)
        assertEquals(emptyList(), team.staff)
        assertEquals(null, team.news.single().publishedDateText)
    }

    @Test
    fun scheduleDateAndTimeParseIsoValuesAndTimeNeedsADate() {
        val parsed = teamDetailResponse(
            upcomingMatches = listOf(match("1", scheduledDate = "2026-09-24", scheduledTime = "18:00")),
            recentMatches = listOf(
                match("2", scheduledDate = "2026-08-28", scheduledTime = null),
                match("3", scheduledDate = null, scheduledTime = "18:00"),
                match("4", scheduledDate = "2026/08/28", scheduledTime = "19:05"),
                match("5", scheduledDate = "2026-02-30", scheduledTime = "19:05"),
                match("6", scheduledDate = "2026-08-28", scheduledTime = "7:05 pm"),
                match("7"),
            ),
        ).toDomain()

        val upcoming = parsed.upcomingMatches.single()
        assertEquals(LocalDate(2026, 9, 24), upcoming.scheduledDate)
        assertEquals(LocalTime(18, 0), upcoming.scheduledTime)
        assertEquals("2026-08-28 17:00", upcoming.scheduledAtText)
        val recent = parsed.recentMatches
        val (dateOnly, timeOnly, slashDate, impossibleDate, badTime) = recent
        val absent = recent[5]
        assertEquals(LocalDate(2026, 8, 28), dateOnly.scheduledDate)
        assertNull(dateOnly.scheduledTime)
        // The contract nulls both when the date is unreadable, so a lone time is dropped.
        listOf(timeOnly, slashDate, impossibleDate, absent).forEach {
            assertNull(it.scheduledDate)
            assertNull(it.scheduledTime)
        }
        assertEquals(LocalDate(2026, 8, 28), badTime.scheduledDate)
        assertNull(badTime.scheduledTime)
    }

    @Test
    fun newsPublishedDateParsesIsoDatesAndKeepsTheSourceText() {
        fun news(publishedDate: String?) = TeamNewsDto(
            reference = "700755/kiwoom-drx-releases-rookie-hermes",
            title = "KIWOOM DRX releases rookie Hermes",
            publishedDateText = "2026/09/15",
            publishedDate = publishedDate,
        )
        val parsed = teamDetailResponse(
            news = listOf(news("2026-09-15"), news(null), news("2026-13-01"), news("2026/09/15")),
        ).toDomain().news

        assertEquals(LocalDate(2026, 9, 15), parsed[0].publishedDate)
        assertEquals(listOf(null, null, null), parsed.drop(1).map { it.publishedDate })
        assertEquals(List(4) { "2026/09/15" }, parsed.map { it.publishedDateText })
    }

    @Test
    fun malformedNewsReferenceIsRejected() {
        val response = teamDetailResponse(news = listOf(TeamNewsDto("invalid", "DRX news", null)))

        assertFailsWith<IllegalArgumentException> {
            response.toDomain()
        }
    }

    private fun teamDetailResponse(
        tag: String? = "KRX",
        country: String? = "South Korea",
        logoUrl: String? = "https://owcdn.net/img/drx.png",
        upcomingMatches: List<TeamMatchDto> = listOf(match("698887")),
        recentMatches: List<TeamMatchDto> = listOf(match("698100")),
        players: List<TeamRosterMemberDto> = listOf(rosterMember("4462")),
        staff: List<TeamRosterMemberDto> = listOf(rosterMember("775", roleLabels = listOf("head coach"))),
        news: List<TeamNewsDto> = listOf(
            TeamNewsDto(
                reference = "700755/kiwoom-drx-releases-rookie-hermes",
                title = "KIWOOM DRX releases rookie Hermes",
                publishedDateText = "2026-08-25",
            ),
        ),
    ) = TeamDetailResponseDto(
        id = "8185",
        name = "KIWOOM DRX",
        tag = tag,
        country = country,
        upcomingMatches = upcomingMatches,
        recentMatches = recentMatches,
        players = players,
        staff = staff,
        news = news,
        logoUrl = logoUrl,
    )

    private fun match(
        id: String,
        eventName: String? = "VCT Pacific",
        eventStage: String? = "Stage 2",
        statusText: String? = "in 2d",
        scheduledAtText: String? = "2026-08-28 17:00",
        scheduledDate: String? = null,
        scheduledTime: String? = null,
    ) = TeamMatchDto(
        id = id,
        eventName = eventName,
        eventStage = eventStage,
        teamName = "KIWOOM DRX",
        opponentName = "Sentinels",
        statusText = statusText,
        scheduledAtText = scheduledAtText,
        scheduledDate = scheduledDate,
        scheduledTime = scheduledTime,
    )

    private fun rosterMember(
        id: String,
        realName: String? = "Kim Myeong-kwan",
        roleLabels: List<String> = listOf("player"),
        imageUrl: String? = if (id == "4462") {
            "https://owcdn.net/img/mako.png"
        } else {
            "https://owcdn.net/img/termi.png"
        },
    ) = TeamRosterMemberDto(
        id = id,
        handle = if (id == "4462") "MaKo" else "termi",
        realName = realName,
        roleLabels = roleLabels,
        imageUrl = imageUrl,
    )
}

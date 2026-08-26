package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamDetailResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamMatchDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamNewsDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.team.TeamRosterMemberDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TeamMapperTest {

    @Test
    fun responseMapsAllTeamSectionsWithoutChangingOrder() {
        val team = teamDetailResponse().toDomain()

        assertEquals("8185", team.id)
        assertEquals("KIWOOM DRX", team.name)
        assertEquals("KRX", team.tag)
        assertEquals("South Korea", team.country)
        assertEquals(listOf("698887"), team.upcomingMatches.map { it.id })
        assertEquals(listOf("698100"), team.recentMatches.map { it.id })
        assertEquals(listOf("4462"), team.players.map { it.id })
        assertEquals(listOf("775"), team.staff.map { it.id })
        assertEquals("MaKo", team.players.single().handle)
        assertEquals(listOf("player"), team.players.single().roleLabels)
        assertEquals("termi", team.staff.single().handle)
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
            players = listOf(rosterMember(id = "4462", realName = null, roleLabels = emptyList())),
            staff = emptyList(),
            news = listOf(TeamNewsDto("700755/kiwoom-drx", "DRX news", null)),
        ).toDomain()

        assertEquals(null, team.tag)
        assertEquals(null, team.country)
        assertEquals(emptyList(), team.upcomingMatches)
        assertEquals(null, team.recentMatches.single().eventName)
        assertEquals(null, team.recentMatches.single().eventStage)
        assertEquals(null, team.recentMatches.single().statusText)
        assertEquals(null, team.recentMatches.single().scheduledAtText)
        assertEquals(null, team.players.single().realName)
        assertEquals(emptyList(), team.players.single().roleLabels)
        assertEquals(emptyList(), team.staff)
        assertEquals(null, team.news.single().publishedDateText)
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
    )

    private fun match(
        id: String,
        eventName: String? = "VCT Pacific",
        eventStage: String? = "Stage 2",
        statusText: String? = "in 2d",
        scheduledAtText: String? = "2026-08-28 17:00",
    ) = TeamMatchDto(
        id = id,
        eventName = eventName,
        eventStage = eventStage,
        teamName = "KIWOOM DRX",
        opponentName = "Sentinels",
        statusText = statusText,
        scheduledAtText = scheduledAtText,
    )

    private fun rosterMember(
        id: String,
        realName: String? = "Kim Myeong-kwan",
        roleLabels: List<String> = listOf("player"),
    ) = TeamRosterMemberDto(
        id = id,
        handle = if (id == "4462") "MaKo" else "termi",
        realName = realName,
        roleLabels = roleLabels,
    )
}

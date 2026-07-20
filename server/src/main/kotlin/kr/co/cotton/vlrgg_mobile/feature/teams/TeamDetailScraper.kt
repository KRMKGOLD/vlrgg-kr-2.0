package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Obtains the two current Team Detail source documents without retaining prior content. */
internal class TeamDetailScraper(
    private val transport: UpstreamHtmlTransport,
) {
    suspend fun scrape(teamId: TeamId): TeamDetailUpstreamContent {
        val overviewUrl = teamOverviewUrl(teamId)
        val newsUrl = teamNewsUrl(teamId)
        return TeamDetailUpstreamContent(
            overviewHtml = transport.get(overviewUrl),
            newsHtml = transport.get(newsUrl),
            overviewUrl = overviewUrl,
            newsUrl = newsUrl,
        )
    }
}

private fun teamOverviewUrl(teamId: TeamId): Url = Url("https://www.vlr.gg/team/${teamId.value}/")

private fun teamNewsUrl(teamId: TeamId): Url = Url("https://www.vlr.gg/team/news/${teamId.value}/")

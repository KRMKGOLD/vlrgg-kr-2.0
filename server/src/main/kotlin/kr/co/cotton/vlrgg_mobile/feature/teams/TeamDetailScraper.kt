package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Obtains the two current Team Detail source documents without retaining prior content. */
internal class TeamDetailScraper(
    private val transport: UpstreamHtmlTransport,
) {
    /** Fetches the current overview and news pages as sibling requests; failure and cancellation propagate structurally. */
    suspend fun scrape(teamId: TeamId): TeamDetailUpstreamContent = coroutineScope {
        val overviewUrl = teamOverviewUrl(teamId)
        val newsUrl = teamNewsUrl(teamId)
        val overviewHtml = async { transport.get(overviewUrl) }
        val newsHtml = async { transport.get(newsUrl) }
        TeamDetailUpstreamContent(
            overviewHtml = overviewHtml.await(),
            newsHtml = newsHtml.await(),
            overviewUrl = overviewUrl,
            newsUrl = newsUrl,
        )
    }
}

private fun teamOverviewUrl(teamId: TeamId): Url = Url("https://www.vlr.gg/team/${teamId.value}/")

private fun teamNewsUrl(teamId: TeamId): Url = Url("https://www.vlr.gg/team/news/${teamId.value}/")

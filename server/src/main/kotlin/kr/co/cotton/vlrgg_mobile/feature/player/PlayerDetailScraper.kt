package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Fetches one current all-time Player page; it retains no prior response. */
internal class PlayerDetailScraper(private val transport: UpstreamHtmlTransport) {
    suspend fun scrape(playerId: PlayerId): PlayerDetailUpstreamContent {
        val upstreamUrl = Url("https://www.vlr.gg/player/${playerId.value}/?timespan=all")
        return PlayerDetailUpstreamContent(transport.get(upstreamUrl), upstreamUrl)
    }
}

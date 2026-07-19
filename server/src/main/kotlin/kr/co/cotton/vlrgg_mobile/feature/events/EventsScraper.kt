package kr.co.cotton.vlrgg_mobile.feature.events

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

internal interface EventsScraper {
    suspend fun fetchEventList(): EventHtmlPage

    suspend fun fetchEventDetail(eventId: String): EventHtmlPage

    suspend fun fetchEventMatches(eventId: String): EventHtmlPage

    suspend fun fetchEventNews(eventId: String): EventHtmlPage

    suspend fun fetchEventStats(eventId: String): EventHtmlPage
}

/** Builds only fixed VLR.GG paths from route-validated numeric identifiers. */
internal class VlrEventsScraper(
    private val transport: UpstreamHtmlTransport,
) : EventsScraper {
    override suspend fun fetchEventList(): EventHtmlPage = fetch(Url("https://www.vlr.gg/events"))

    override suspend fun fetchEventDetail(eventId: String): EventHtmlPage =
        fetch(Url("https://www.vlr.gg/event/$eventId"))

    override suspend fun fetchEventMatches(eventId: String): EventHtmlPage =
        fetch(Url("https://www.vlr.gg/event/matches/$eventId/?series_id=all"))

    override suspend fun fetchEventNews(eventId: String): EventHtmlPage =
        fetch(Url("https://www.vlr.gg/event/news/$eventId"))

    override suspend fun fetchEventStats(eventId: String): EventHtmlPage =
        fetch(Url("https://www.vlr.gg/event/stats/$eventId"))

    private suspend fun fetch(url: Url): EventHtmlPage = EventHtmlPage(
        upstreamUrl = url,
        html = transport.get(url),
    )
}

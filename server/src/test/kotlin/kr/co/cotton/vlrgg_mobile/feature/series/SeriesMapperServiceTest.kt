package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.events.EventStatus
import kotlin.test.*

class SeriesMapperServiceTest {
    @Test
    fun `mapper reuses Event summary semantics and groups statuses without fake values`() {
        val response = SeriesMapper().map(
            SeriesSource(
                id = "85",
                name = "Series",
                description = null,
                events = listOf(
                    SeriesEventSource("101", "Live", SeriesEventStatusSource.ONGOING, null, null, null),
                    SeriesEventSource("102", "Next", SeriesEventStatusSource.UPCOMING, "Jul 1", "kr", null),
                    SeriesEventSource("201", "Done", SeriesEventStatusSource.COMPLETED, null, null, null),
                    SeriesEventSource("202", "Paused", SeriesEventStatusSource.PAUSED, null, null, null),
                ),
            ),
        )

        assertEquals("85", response.id)
        assertEquals(listOf(EventStatus.ONGOING, EventStatus.UPCOMING), response.upcomingEvents.map { it.status })
        assertEquals(listOf(EventStatus.COMPLETED, EventStatus.PAUSED), response.completedEvents.map { it.status })
        assertEquals("102", response.upcomingEvents[1].id)
        assertEquals("Jul 1", response.upcomingEvents[1].dateLabel)
        assertNull(response.upcomingEvents[1].imageUrl)
    }

    @Test
    fun `service fetches current data each time has no stale fallback and preserves cancellation`() = runBlocking {
        val transport = RecordingTransport()
        val service = SeriesService(VlrSeriesScraper(transport), SeriesParser(), SeriesMapper())
        val seriesId = SeriesId.fromPath("85")

        assertEquals("85", service.get(seriesId).id)
        transport.failure = UpstreamNetworkFailure(Url("https://www.vlr.gg/series/85"))
        assertFailsWith<UpstreamNetworkFailure> { service.get(seriesId) }
        assertEquals(2, transport.requestedUrls.size)
        assertTrue(transport.requestedUrls.all { it.toString() == "https://www.vlr.gg/series/85" })

        val cancelled = SeriesService(
            VlrSeriesScraper(object : UpstreamHtmlTransport {
                override suspend fun get(url: Url): String = throw CancellationException("cancel")
            }),
            SeriesParser(),
            SeriesMapper(),
        )
        assertFailsWith<CancellationException> { cancelled.get(seriesId) }
        Unit
    }

    private class RecordingTransport : UpstreamHtmlTransport {
        val requestedUrls = mutableListOf<Url>()
        var failure: Exception? = null

        override suspend fun get(url: Url): String {
            requestedUrls += url
            failure?.let { throw it }
            return checkNotNull(javaClass.classLoader.getResource("fixtures/series/verified-empty.html")).readText()
        }
    }
}

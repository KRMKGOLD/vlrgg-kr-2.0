package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class SeriesParserTest {
    private val parser = SeriesParser()
    private val pageUrl = Url("https://www.vlr.gg/series/85")
    private val seriesId = SeriesId.fromPath("85")

    @Test
    fun `parser keeps actual statuses and groups both source sections by status`() {
        val source = parse("both-groups.html")

        assertEquals("85", source.id)
        assertEquals("Valorant Challengers League 2026", source.name)
        assertEquals("Official Tier 2 circuit.", source.description)
        assertEquals(listOf("101", "102", "201", "202"), source.events.map(SeriesEventSource::id))
        assertEquals(
            listOf(SeriesEventStatusSource.ONGOING, SeriesEventStatusSource.UPCOMING, SeriesEventStatusSource.COMPLETED, SeriesEventStatusSource.PAUSED),
            source.events.map(SeriesEventSource::status),
        )
        assertEquals("Jul 1—10", source.events.first().dateLabel)
        assertEquals("kr", source.events.first().regionCode)
        assertEquals("https://www.vlr.gg/img/first.png", source.events.first().imageUrl)
    }

    @Test
    fun `parser accepts verified empty and one-populated-section pages`() {
        assertTrue(parse("verified-empty.html").events.isEmpty())
        assertEquals(listOf("101"), parse("upcoming-only.html").events.map(SeriesEventSource::id))
        assertEquals(listOf("201"), parse("completed-only.html").events.map(SeriesEventSource::id))
    }

    @Test
    fun `parser ignores polluted DOM outside the selected series container`() {
        val source = parse("polluted-dom.html")

        assertEquals(listOf("101"), source.events.map(SeriesEventSource::id))
    }

    @Test
    fun `parser deduplicates repeated stable event IDs in source order`() {
        val source = parse("duplicate-ids.html")

        assertEquals(listOf("101"), source.events.map(SeriesEventSource::id))
        assertEquals("First copy", source.events.single().name)
    }

    @Test
    fun `parser fails closed when the selected event container has an unexpected direct child`() {
        assertParsingFailure("unexpected-container-child.html")
    }

    @Test
    fun `parser fails closed when a selected event card has no href`() {
        assertParsingFailure("href-less-event-card.html")
    }

    @Test
    fun `parser fails closed for unknown contradictory and malformed source structures`() {
        listOf(
            "unknown-status.html",
            "duplicate-conflicting-status.html",
            "malformed-section.html",
            "malformed-row.html",
            "missing-event-name.html",
            "missing-series-name.html",
            "missing-required-structure.html",
        ).forEach { fixture ->
            assertParsingFailure(fixture)
        }
    }

    private fun assertParsingFailure(fixture: String) {
        val failure = assertFailsWith<SourceParsingFailure> { parse(fixture) }
        assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
        assertIs<IllegalStateException>(failure.cause)
    }

    private fun parse(fixture: String): SeriesSource = parser.parse(
        page = SeriesHtmlPage(pageUrl, fixture(fixture)),
        seriesId = seriesId,
    )

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader.getResource("fixtures/series/$name"),
    ).readText()
}

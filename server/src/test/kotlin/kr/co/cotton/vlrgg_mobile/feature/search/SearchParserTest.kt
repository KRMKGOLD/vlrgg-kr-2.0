package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.http.*
import java.nio.file.Files
import java.nio.file.Paths
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kotlin.test.*

class SearchParserTest {

    private val parser = SearchParser()
    private val upstreamUrl = Url("https://www.vlr.gg/search/?q=sentinels")

    @Test
    fun `parser classifies supported search cards and keeps optional descriptions`() {
        val source = parser.parse(readFixture("mixed-results.html"), upstreamUrl)

        assertEquals(
            listOf(
                SearchSourceResult(SearchSourceResultType.SERIES, "86", "Valorant Champions Tour 2026", null),
                SearchSourceResult(SearchSourceResultType.EVENT, "202", "Champions Seoul", "Aug 1, 2026 to Aug 24, 2026"),
                SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", "SEN · United States"),
                SearchSourceResult(SearchSourceResultType.PLAYER, "9", "TenZ", null),
            ),
            source.results,
        )
    }

    @Test
    fun `parser maps the real eventgroup search path to a Series result`() {
        val source = parser.parse(readFixture("series-real-dom.html"), upstreamUrl)

        assertEquals(
            listOf(
                SearchSourceResult(SearchSourceResultType.SERIES, "86", "Valorant Champions Tour 2026", null),
            ),
            source.results,
        )
    }

    @Test
    fun `parser accepts an empty valid results page`() {
        val source = parser.parse(readFixture("empty-results.html"), upstreamUrl)

        assertTrue(source.results.isEmpty())
    }

    @Test
    fun `parser handles a valid single type page`() {
        val source = parser.parse(readFixture("single-type-results.html"), upstreamUrl)

        assertEquals(
            listOf(
                SearchSourceResult(SearchSourceResultType.PLAYER, "9", "TenZ", "Tyson Ngo · Sentinels"),
                SearchSourceResult(SearchSourceResultType.PLAYER, "10", "zekken", "Zachary Patrone"),
            ),
            source.results,
        )
    }

    @Test
    fun `parser keeps identifiable results when optional descriptions are missing`() {
        val source = parser.parse(readFixture("missing-optionals.html"), upstreamUrl)

        assertEquals(
            listOf(SearchSourceResult(SearchSourceResultType.TEAM, "2", "Sentinels", null)),
            source.results,
        )
    }

    @Test
    fun `parser ignores unknown types instead of guessing a supported type`() {
        val source = parser.parse(readFixture("unknown-type.html"), upstreamUrl)

        assertTrue(source.results.isEmpty())
    }

    @Test
    fun `parser fails closed when the result card class drifts around a canonical supported link`() {
        assertFailsWith<SourceParsingFailure> {
            parser.parse(readFixture("renamed-result-card.html"), upstreamUrl)
        }
    }

    @Test
    fun `parser fails closed when the search item class drifts on a canonical supported link`() {
        assertFailsWith<SourceParsingFailure> {
            parser.parse(readFixture("renamed-search-item.html"), upstreamUrl)
        }
    }

    @Test
    fun `parser fails closed when Found results no longer use a canonical search path`() {
        assertFailsWith<SourceParsingFailure> {
            parser.parse(readFixture("supported-path-drift.html"), upstreamUrl)
        }
    }

    @Test
    fun `parser ignores malformed and polluted cards while retaining valid results`() {
        val source = parser.parse(readFixture("mixed-results.html"), upstreamUrl)

        assertEquals(setOf("86", "202", "2", "9"), source.results.map { it.id }.toSet())
    }

    @Test
    fun `parser maps a missing required results container to a parsing failure`() {
        val failure = assertFailsWith<SourceParsingFailure> {
            parser.parse(readFixture("malformed-structure.html"), upstreamUrl)
        }

        assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
        assertIs<IllegalStateException>(failure.cause)
    }

    @Test
    fun `parser maps an entirely malformed supported result set to a parsing failure`() {
        val failure = assertFailsWith<SourceParsingFailure> {
            parser.parse(readFixture("malformed-results.html"), upstreamUrl)
        }

        assertEquals("https://www.vlr.gg/", failure.canonicalUpstreamUrl)
        assertIs<IllegalStateException>(failure.cause)
    }

    private fun readFixture(name: String): String =
        Files.readString(Paths.get(requireNotNull(javaClass.getResource("/fixtures/search/$name")).toURI()))
}

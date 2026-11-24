package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kotlin.test.*

class SeriesRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private val transport = CountingTransport {
        checkNotNull(javaClass.classLoader.getResource("fixtures/series/both-groups.html")).readText()
    }

    private fun series(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureSeriesRoutes(createSeriesService(transport), cache) }, block)

    @Test
    fun `series detail is keyed by series id with the five minute ttl`() = series {
        assertCachedRouteContract(harness, transport, "/api/v1/series/85", harness.key(ResponseCacheRoute.SERIES_DETAIL, "85"), 300)
        assertEquals("test-sha:series-detail:85", harness.key(ResponseCacheRoute.SERIES_DETAIL, "85"))
    }

    @Test
    fun `invalid series requests never reach the store or upstream`() = series {
        assertInvalidRequestsSkipCacheAndUpstream(
            harness,
            transport,
            listOf(
                "/api/v1/series",
                "/api/v1/series/",
                "/api/v1/series/85/",
                "/api/v1/series/85/extra",
                "/api/v1/series/0",
                "/api/v1/series/085",
                "/api/v1/series/85?view=all",
            ),
        )
        assertEquals(0, transport.calls)
    }
}

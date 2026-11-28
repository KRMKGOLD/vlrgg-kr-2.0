package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kr.co.cotton.vlrgg_mobile.cache.uncachedBody
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kotlin.test.*

class SeriesRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private val transport = CountingTransport {
        checkNotNull(javaClass.classLoader.getResource("fixtures/series/both-groups.html")).readText()
    }

    @AfterTest
    fun close() = harness.close()

    private fun series(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureSeriesRoutes(createSeriesService(transport), cache) }, block)

    @Test
    fun `series detail caches for five minutes`() = series {
        assertCachedRouteContract(harness, transport, "/api/v1/series/85", 300)
    }

    @Test
    fun `each series id has its own entry`() = series {
        client.get("/api/v1/series/85")
        client.get("/api/v1/series/86")
        client.get("/api/v1/series/85")

        assertEquals(2, transport.calls)
        assertEquals(2, harness.counter(ResponseCacheCounter.MISS))
        assertEquals(1, harness.counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `miss body equals the uncached route body`() {
        val uncached = uncachedBody({ configureSeriesRoutes(createSeriesService(transport)) }, "/api/v1/series/85")
        lateinit var cached: ByteArray
        series { cached = client.get("/api/v1/series/85").bodyAsBytes() }
        assertContentEquals(uncached, cached)
    }

    @Test
    fun `invalid series requests never reach the cache or upstream`() = series {
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

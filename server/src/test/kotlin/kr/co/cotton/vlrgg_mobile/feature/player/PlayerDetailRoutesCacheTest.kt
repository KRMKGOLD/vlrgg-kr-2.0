package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kr.co.cotton.vlrgg_mobile.cache.uncachedBody
import kr.co.cotton.vlrgg_mobile.plugins.ResponseCacheCounter
import kotlin.test.*

class PlayerDetailRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private val transport = CountingTransport {
        checkNotNull(javaClass.classLoader.getResource("fixtures/players/player-detail.html")).readText()
    }

    @AfterTest
    fun close() = harness.close()

    private fun players(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configurePlayerDetailRoutes(createPlayerDetailService(transport), cache) }, block)

    @Test
    fun `player detail caches for five minutes`() = players {
        assertCachedRouteContract(harness, transport, "/api/v1/players/488", 300)
    }

    @Test
    fun `each player id has its own entry`() = players {
        client.get("/api/v1/players/488")
        client.get("/api/v1/players/489")
        client.get("/api/v1/players/488")

        assertEquals(2, transport.calls)
        assertEquals(2, harness.counter(ResponseCacheCounter.MISS))
        assertEquals(1, harness.counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `miss body equals the uncached route body`() {
        val uncached = uncachedBody({ configurePlayerDetailRoutes(createPlayerDetailService(transport)) }, "/api/v1/players/488")
        lateinit var cached: ByteArray
        players { cached = client.get("/api/v1/players/488").bodyAsBytes() }
        assertContentEquals(uncached, cached)
    }

    @Test
    fun `invalid player requests never reach the cache or upstream`() = players {
        assertInvalidRequestsSkipCacheAndUpstream(
            harness,
            transport,
            listOf(
                "/api/v1/players",
                "/api/v1/players/",
                "/api/v1/players/488/",
                "/api/v1/players/488/extra",
                "/api/v1/players/0",
                "/api/v1/players/0488",
                "/api/v1/players/488?view=all",
            ),
        )
        assertEquals(0, transport.calls)
    }
}

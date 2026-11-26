package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kotlin.test.*

class PlayerDetailRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private val transport = CountingTransport {
        checkNotNull(javaClass.classLoader.getResource("fixtures/players/player-detail.html")).readText()
    }

    private fun players(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configurePlayerDetailRoutes(createPlayerDetailService(transport), cache) }, block)

    @Test
    fun `player detail is keyed by player id with the five minute ttl`() = players {
        assertCachedRouteContract(harness, transport, "/api/v1/players/488", harness.key(ResponseCacheRoute.PLAYER_DETAIL, "488"), 300)
        assertEquals("test-sha:player-detail:488", harness.key(ResponseCacheRoute.PLAYER_DETAIL, "488"))
    }

    @Test
    fun `the same numeric id in another route never shares a key`() {
        val ids = listOf(
            ResponseCacheRoute.PLAYER_DETAIL,
            ResponseCacheRoute.TEAM_DETAIL,
            ResponseCacheRoute.SERIES_DETAIL,
            ResponseCacheRoute.EVENT_DETAIL,
            ResponseCacheRoute.MATCH_DETAIL,
        ).map { harness.key(it, "488") }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `invalid player requests never reach the store or upstream`() = players {
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

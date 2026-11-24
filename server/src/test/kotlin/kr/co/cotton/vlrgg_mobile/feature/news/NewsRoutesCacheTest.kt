package kr.co.cotton.vlrgg_mobile.feature.news

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.cache.CountingTransport
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.cache.RouteCacheHarness
import kr.co.cotton.vlrgg_mobile.cache.assertCachedRouteContract
import kr.co.cotton.vlrgg_mobile.cache.assertInvalidRequestsSkipCacheAndUpstream
import kotlin.test.*

class NewsRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private val transport = CountingTransport { url ->
        when (url.encodedPath) {
            "/news", "/news/" -> fixture("news-list-page-1.html")
            "/101/champions-run" -> fixture("news-article.html")
            else -> error(url.encodedPath)
        }
    }

    private fun news(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureNewsRoutes(createDefaultNewsService(transport), cache) }, block)

    @Test
    fun `list is keyed by page and article by id and slug with the five minute ttl`() = news {
        assertCachedRouteContract(harness, transport, "/api/v1/news", harness.key(ResponseCacheRoute.NEWS_LIST, "1"), 300)
        assertCachedRouteContract(harness, transport, "/api/v1/news?page=1", harness.key(ResponseCacheRoute.NEWS_LIST, "1"), 300)
        assertCachedRouteContract(
            harness,
            transport,
            "/api/v1/news/101/champions-run",
            harness.key(ResponseCacheRoute.NEWS_ARTICLE, "101", "champions-run"),
            300,
        )
        assertEquals("test-sha:news-article:101:champions-run", harness.key(ResponseCacheRoute.NEWS_ARTICLE, "101", "champions-run"))
    }

    @Test
    fun `omitted page and page 1 share one cache entry`() = news {
        client.get("/api/v1/news")
        client.get("/api/v1/news?page=1")

        assertEquals(1, transport.calls)
        assertEquals(listOf("test-sha:news-list:1"), harness.store.sets.map { it.key })
    }

    @Test
    fun `a later page uses its own key`() = news {
        client.get("/api/v1/news")
        val second = client.get("/api/v1/news?page=2")

        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(listOf("test-sha:news-list:1", "test-sha:news-list:2"), harness.store.sets.map { it.key })
        assertEquals(300, harness.store.sets.last().ttlSeconds)
    }

    @Test
    fun `list and article keys do not collide`() = news {
        client.get("/api/v1/news")
        client.get("/api/v1/news/101/champions-run")

        val keys = harness.store.sets.map { it.key }
        assertEquals(2, keys.toSet().size)
        assertTrue(harness.store.values.getValue("test-sha:news-article:101:champions-run").contains("Champions run"))
    }

    @Test
    fun `invalid news requests never reach the store or upstream`() = news {
        assertInvalidRequestsSkipCacheAndUpstream(
            harness,
            transport,
            listOf(
                "/api/v1/news?page=0",
                "/api/v1/news?page=01",
                "/api/v1/news?page=1&page=2",
                "/api/v1/news?unexpected=true",
                "/api/v1/news/0101/champions-run",
                "/api/v1/news/101/Champions_Run",
            ),
        )
        assertEquals(0, transport.calls)
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}

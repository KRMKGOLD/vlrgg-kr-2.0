package kr.co.cotton.vlrgg_mobile.feature.news

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

class NewsRoutesCacheTest {
    private val harness = RouteCacheHarness()
    private val transport = CountingTransport { url ->
        when {
            url.encodedPath == "/news" || url.encodedPath == "/news/" -> fixture("news-list-page-1.html")
            url.encodedPath.matches(Regex("/\\d+/[a-z0-9-]+")) -> fixture("news-article.html")
            else -> error(url.encodedPath)
        }
    }

    @AfterTest
    fun close() = harness.close()

    private fun news(block: suspend ApplicationTestBuilder.() -> Unit) =
        harness.run({ cache -> configureNewsRoutes(createDefaultNewsService(transport), cache) }, block)

    @Test
    fun `list and article cache for five minutes`() = news {
        assertCachedRouteContract(harness, transport, "/api/v1/news", 300)
        assertCachedRouteContract(harness, transport, "/api/v1/news?page=2", 300)
        assertCachedRouteContract(harness, transport, "/api/v1/news/101/champions-run", 300)
    }

    @Test
    fun `omitted page and page 1 share one cache entry`() = news {
        val omitted = client.get("/api/v1/news").bodyAsBytes()
        val explicit = client.get("/api/v1/news?page=1").bodyAsBytes()

        assertContentEquals(omitted, explicit)
        assertEquals(1, transport.calls)
        assertEquals(1, harness.counter(ResponseCacheCounter.MISS))
        assertEquals(1, harness.counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `a later page uses its own entry`() = news {
        client.get("/api/v1/news")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/news?page=2").status)
        assertEquals(2, transport.calls)
        assertEquals(2, harness.counter(ResponseCacheCounter.MISS))
        assertEquals(0, harness.counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `an article is keyed by both id and slug`() = news {
        val paths = listOf("/api/v1/news/101/champions-run", "/api/v1/news/101/other-slug", "/api/v1/news/102/champions-run")
        paths.forEach { assertEquals(HttpStatusCode.OK, client.get(it).status, it) }
        assertEquals(3, transport.calls)
        assertEquals(3, harness.counter(ResponseCacheCounter.MISS))

        paths.forEach { assertEquals(HttpStatusCode.OK, client.get(it).status, it) }
        assertEquals(3, transport.calls)
        assertEquals(3, harness.counter(ResponseCacheCounter.HIT))
    }

    @Test
    fun `the longest valid slug is cached like any other`() = news {
        val path = "/api/v1/news/101/${"a".repeat(128)}"
        val miss = client.get(path)
        val hit = client.get(path)

        assertEquals(HttpStatusCode.OK, miss.status)
        assertContentEquals(miss.bodyAsBytes(), hit.bodyAsBytes())
        assertEquals(1, transport.calls)
    }

    @Test
    fun `list and article never share an entry`() = news {
        val list = client.get("/api/v1/news").bodyAsText()
        val article = client.get("/api/v1/news/1/champions-run").bodyAsText()

        assertEquals(2, transport.calls)
        assertContains(article, "Champions run")
        assertNotEquals(list, article)
    }

    @Test
    fun `miss bodies equal the uncached route bodies`() {
        listOf("/api/v1/news", "/api/v1/news?page=2", "/api/v1/news/101/champions-run").forEach { path ->
            val uncached = uncachedBody({ configureNewsRoutes(createDefaultNewsService(transport)) }, path)
            lateinit var cached: ByteArray
            news { cached = client.get(path).bodyAsBytes() }
            assertContentEquals(uncached, cached, path)
        }
    }

    @Test
    fun `invalid news requests never reach the cache or upstream`() = news {
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
                "/api/v1/news/101/${"a".repeat(129)}",
            ),
        )
        assertEquals(0, transport.calls)
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}

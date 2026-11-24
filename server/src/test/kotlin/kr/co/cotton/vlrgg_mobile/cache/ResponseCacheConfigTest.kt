package kr.co.cotton.vlrgg_mobile.cache

import kotlin.test.*

class ResponseCacheConfigTest {
    private val complete = mapOf(
        RESPONSE_CACHE_ENABLED_ENVIRONMENT_VARIABLE to "true",
        UPSTASH_REST_URL_ENVIRONMENT_VARIABLE to TEST_UPSTASH_URL,
        UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE to TEST_UPSTASH_TOKEN,
        DEPLOY_SHA_ENVIRONMENT_VARIABLE to TEST_DEPLOY_SHA,
    )

    @Test
    fun `cache is off without configuration and unless the flag is exactly true`() {
        assertSame(ResponseCacheConfig.Disabled, ResponseCacheConfig.fromEnvironment(emptyMap()))
        listOf("false", "TRUE", "1", "yes", "").forEach { flag ->
            assertSame(ResponseCacheConfig.Disabled, ResponseCacheConfig.fromEnvironment(complete + (RESPONSE_CACHE_ENABLED_ENVIRONMENT_VARIABLE to flag)))
        }
        // Store values alone never turn the cache on.
        assertSame(ResponseCacheConfig.Disabled, ResponseCacheConfig.fromEnvironment(complete - RESPONSE_CACHE_ENABLED_ENVIRONMENT_VARIABLE))
    }

    @Test
    fun `complete configuration enables the cache`() {
        val config = assertIs<ResponseCacheConfig.Enabled>(ResponseCacheConfig.fromEnvironment(complete))
        assertEquals("test-database.upstash.io", config.restUrl.host)
        assertEquals(TEST_DEPLOY_SHA, config.deploySha)
        assertIs<ResponseCacheConfig.Enabled>(ResponseCacheConfig.fromEnvironment(complete + (UPSTASH_REST_URL_ENVIRONMENT_VARIABLE to "$TEST_UPSTASH_URL/")))
        assertIs<ResponseCacheConfig.Enabled>(ResponseCacheConfig.fromEnvironment(complete + (UPSTASH_REST_URL_ENVIRONMENT_VARIABLE to "https://test-database.upstash.io:443")))
    }

    @Test
    fun `enabled cache with a missing value refuses to start`() {
        listOf(UPSTASH_REST_URL_ENVIRONMENT_VARIABLE, UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE, DEPLOY_SHA_ENVIRONMENT_VARIABLE).forEach { name ->
            assertRejected(complete - name, name)
            assertRejected(complete + (name to ""), name)
        }
    }

    @Test
    fun `enabled cache with a malformed url token or sha refuses to start without echoing it`() {
        listOf(
            "http://test-database.upstash.io",
            "https://test-database.upstash.io:8443",
            "https://user:secret-password@test-database.upstash.io",
            "https://test-database.upstash.io/path",
            "https://test-database.upstash.io/?token=leak",
            "https://test-database.upstash.io/#fragment",
            "https://",
            "test-database.upstash.io",
            "https://bad host.upstash.io",
            "https://localhost",
            "https://" + "a".repeat(600) + ".upstash.io",
        ).forEach { assertRejected(complete + (UPSTASH_REST_URL_ENVIRONMENT_VARIABLE to it), UPSTASH_REST_URL_ENVIRONMENT_VARIABLE, it) }
        listOf("token with space", "token\r\nX-Injected: 1", "토큰", "a".repeat(1_025)).forEach {
            assertRejected(complete + (UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE to it), UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE, it)
        }
        listOf("sha:with:colon", "sha/slash", "a".repeat(65), "sha value").forEach {
            assertRejected(complete + (DEPLOY_SHA_ENVIRONMENT_VARIABLE to it), DEPLOY_SHA_ENVIRONMENT_VARIABLE, it)
        }
    }

    @Test
    fun `enabled config never exposes its url or token through string conversion`() {
        val text = ResponseCacheConfig.fromEnvironment(complete).toString()
        assertFalse(text.contains(TEST_UPSTASH_TOKEN))
        assertFalse(text.contains("upstash"))
    }

    private fun assertRejected(environment: Map<String, String>, variable: String, value: String? = null) {
        val failure = assertFailsWith<IllegalArgumentException> { ResponseCacheConfig.fromEnvironment(environment) }
        val chain = generateSequence<Throwable>(failure) { it.cause }.toList()
        assertEquals(1, chain.size, "a parser cause could carry the raw value")
        val message = failure.message.orEmpty()
        assertContains(message, variable)
        assertFalse(message.contains(TEST_UPSTASH_TOKEN))
        assertFalse(message.contains("upstash.io"))
        if (value != null && value.isNotEmpty()) assertFalse(message.contains(value))
    }
}

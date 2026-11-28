package kr.co.cotton.vlrgg_mobile.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicApiProtectionConfigTest {
    @Test
    fun `defaults match the server limits table`() {
        val expected = PublicApiProtectionConfig(
            apiRequestsPerSecond = 10,
            apiBurst = 20,
            maxActiveApiRequests = 8,
            upstreamRequestsPerSecond = 10,
            upstreamBurst = 20,
            maxActiveUpstreamRequests = 8,
            maxInFlightCanonicalUrls = 8,
            requestsPerSecond = 50,
            requestBurst = 100,
        )
        assertEquals(expected, PublicApiProtectionConfig())
        assertEquals(expected, PublicApiProtectionConfig.fromEnvironment(emptyMap()))
    }

    @Test
    fun `whole request limit is configured from its own environment variables`() {
        val config = PublicApiProtectionConfig.fromEnvironment(
            mapOf("VLRGG_REQUEST_RATE_PER_SECOND" to "7", "VLRGG_REQUEST_RATE_BURST" to "9"),
        )
        assertEquals(7, config.requestsPerSecond)
        assertEquals(9, config.requestBurst)
        assertEquals(10, config.apiRequestsPerSecond)
        assertFailsWith<IllegalArgumentException> {
            PublicApiProtectionConfig.fromEnvironment(mapOf("VLRGG_REQUEST_RATE_BURST" to "0"))
        }
    }

    @Test
    fun `malformed environment limit fails during startup configuration`() {
        assertFailsWith<IllegalArgumentException> {
            PublicApiProtectionConfig.fromEnvironment(mapOf("VLRGG_API_RATE_PER_SECOND" to "zero"))
        }
        assertFailsWith<IllegalArgumentException> {
            PublicApiProtectionConfig.fromEnvironment(mapOf("VLRGG_UPSTREAM_MAX_IN_FLIGHT_KEYS" to "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            PublicApiProtectionConfig(maxRequestBodyBytes = Int.MAX_VALUE)
        }
    }
}

package kr.co.cotton.vlrgg_mobile.protection

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublicApiProtectionConfigTest {
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

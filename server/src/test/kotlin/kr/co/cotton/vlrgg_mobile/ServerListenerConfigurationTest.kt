package kr.co.cotton.vlrgg_mobile

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerListenerConfigurationTest {
    @Test fun `listener uses PORT then legacy port fallback on the public host`() {
        assertEquals(ServerListenerConfiguration("0.0.0.0", 9090), ServerListenerConfiguration.fromEnvironment(mapOf("PORT" to "9090", "VLRGG_SERVER_PORT" to "8081")))
        assertEquals(ServerListenerConfiguration("0.0.0.0", 8081), ServerListenerConfiguration.fromEnvironment(mapOf("VLRGG_SERVER_PORT" to "8081")))
        assertEquals(ServerListenerConfiguration("0.0.0.0", 8080), ServerListenerConfiguration.fromEnvironment(emptyMap()))
        assertFailsWith<IllegalArgumentException> { ServerListenerConfiguration.fromEnvironment(mapOf("PORT" to "0")) }
    }
}

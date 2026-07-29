package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kr.co.cotton.vlrgg_mobile.module
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationRoutesTest {
    @Test fun `notification routes are absent by default`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.NotFound, client.put("/api/v1/match-notifications/targets").status)
    }

    @Test fun `local routes enforce body value match revision and target isolation without leaking value`() = testApplication {
        val path = Files.createTempDirectory("vlrgg-route").resolve("store").absolutePathString()
        val config = configuration(path)
        val store = NotificationStore.open(config)
        application { module(notificationConfiguration = config, notificationStore = store, enableApiDocumentation = true) }

        suspend fun request(path: String, body: String) = client.put(path) { contentType(ContentType.Application.Json); setBody(body) }
        val registered = request("/api/v1/match-notifications/targets", "{\"registrationValue\":\"address-a\",\"revision\":\"1\"}")
        assertEquals(HttpStatusCode.OK, registered.status)
        assertFalse(registered.bodyAsText().contains("address-a"))
        assertEquals(HttpStatusCode.BadRequest, request("/api/v1/match-notifications/subscriptions/0", "{\"registrationValue\":\"address-a\",\"revision\":\"2\",\"active\":true}").status)
        assertEquals(HttpStatusCode.OK, request("/api/v1/match-notifications/subscriptions/42", "{\"registrationValue\":\"address-a\",\"revision\":\"2\",\"active\":true}").status)
        assertEquals(HttpStatusCode.Conflict, request("/api/v1/match-notifications/subscriptions/42", "{\"registrationValue\":\"address-a\",\"revision\":\"2\",\"active\":false}").status)
        assertEquals(HttpStatusCode.OK, request("/api/v1/match-notifications/targets", "{\"registrationValue\":\"address-b\",\"revision\":\"1\"}").status)
        assertEquals(HttpStatusCode.OK, request("/api/v1/match-notifications/subscriptions/99", "{\"registrationValue\":\"address-b\",\"revision\":\"2\",\"active\":true}").status)
        assertEquals(HttpStatusCode.OK, request("/api/v1/match-notifications/global-state", "{\"registrationValue\":\"address-a\",\"revision\":\"3\",\"active\":false}").status)
        val second = client.post("/api/v1/match-notifications/state") { contentType(ContentType.Application.Json); setBody("{\"registrationValue\":\"address-b\"}") }
        assertEquals(HttpStatusCode.OK, second.status)
        assertTrue(second.bodyAsText().contains("99"))
        assertFalse(second.bodyAsText().contains("address-b"))
        assertTrue(client.get("/openapi.json").bodyAsText().contains("/api/v1/match-notifications/targets"))
    }

    @Test fun `literal IPv4 and IPv6 are the only enabled API listeners`() {
        listOf("127.0.0.1", "::1").forEach { host ->
            assertTrue(NotificationConfiguration.fromEnvironment(enabledEnvironment() + ("VLRGG_NOTIFICATIONS_API_ENABLED" to "true"), ServerListenerConfiguration(host, 8080)).apiEnabled)
        }
        listOf("localhost", "127.0.0.2", "0.0.0.0", "::").forEach { host ->
            assertEquals(ConfigurationCategory.UNSAFE_LISTENER, kotlin.test.assertFailsWith<NotificationConfigurationException> {
                NotificationConfiguration.fromEnvironment(enabledEnvironment() + ("VLRGG_NOTIFICATIONS_API_ENABLED" to "true"), ServerListenerConfiguration(host, 8080))
            }.category)
        }
    }

    private fun configuration(path: String) = NotificationConfiguration.fromEnvironment(enabledEnvironment() + mapOf(
        "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_API_ENABLED" to "true",
    ), ServerListenerConfiguration("127.0.0.1", 8080))

    private fun enabledEnvironment() = mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to "/tmp/vlrgg-notification-route",
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1", "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    )
}

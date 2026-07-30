package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.*
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
        val missing = client.post("/api/v1/match-notifications/state") { contentType(ContentType.Application.Json); setBody("{\"registrationValue\":\"absent\"}") }
        assertEquals(HttpStatusCode.OK, missing.status)
        assertEquals("{\"acceptedRevision\":\"0\",\"subscriptions\":[]}", missing.bodyAsText())
        val oversizedState = client.post("/api/v1/match-notifications/state") {
            contentType(ContentType.Application.Json)
            setBody("{\"registrationValue\":\"${"a".repeat(config.registrationValueMaxBytes + 1)}\"}")
        }
        assertEquals(HttpStatusCode.BadRequest, oversizedState.status)
        assertFalse(oversizedState.bodyAsText().contains("a".repeat(32)))
        val openApi = client.get("/openapi.json").bodyAsText()
        assertTrue(openApi.contains("/api/v1/match-notifications/targets"))
        val subscriptionOperation = Json.parseToJsonElement(openApi).jsonObject["paths"]!!.jsonObject["/api/v1/match-notifications/subscriptions/{matchId}"]!!.jsonObject["put"]!!.jsonObject
        assertTrue(subscriptionOperation.containsKey("requestBody"))
        assertTrue(subscriptionOperation["parameters"]!!.toString().contains("9223372036854775807"))
        assertFalse(openApi.contains("FID"))
        assertFalse(openApi.contains("LEGACY_TOKEN"))
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

    @Test fun `chunked body is bounded before decoding and does not leak its content`() = testApplication {
        val path = Files.createTempDirectory("vlrgg-route").resolve("store").absolutePathString()
        val config = configuration(path)
        application { module(notificationConfiguration = config, notificationStore = NotificationStore.open(config)) }
        val secretLikePayload = "z".repeat(config.requestBodyBytes + 1)
        val response = client.put("/api/v1/match-notifications/targets") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) { channel.writeFully(secretLikePayload.encodeToByteArray()) }
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertFalse(response.bodyAsText().contains(secretLikePayload.take(32)))
    }

    @Test fun `UTF-8 byte boundary accepts the exact limit and rejects one byte over`() = testApplication {
        val path = Files.createTempDirectory("vlrgg-route").resolve("store").absolutePathString()
        val config = configuration(path)
        application { module(notificationConfiguration = config, notificationStore = NotificationStore.open(config)) }
        val prefix = "{\"registrationValue\":\"a\",\"revision\":\"1\",\"padding\":\""
        val suffix = "\"}"
        val paddingBytes = config.requestBodyBytes - prefix.encodeToByteArray().size - suffix.encodeToByteArray().size
        val exact = prefix + "가".repeat(paddingBytes / 3) + "x".repeat(paddingBytes % 3) + suffix
        check(exact.encodeToByteArray().size == config.requestBodyBytes)
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/match-notifications/targets") { setBody(ChunkedJsonContent(exact)) }.status)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.put("/api/v1/match-notifications/targets") { setBody(ChunkedJsonContent(exact + "x")) }.status)
    }

    private fun configuration(path: String) = NotificationConfiguration.fromEnvironment(enabledEnvironment() + mapOf(
        "VLRGG_NOTIFICATIONS_STORAGE_PATH" to path,
        "VLRGG_NOTIFICATIONS_API_ENABLED" to "true",
    ), ServerListenerConfiguration("127.0.0.1", 8080))

    private fun enabledEnvironment() = mapOf(
        "VLRGG_NOTIFICATIONS_ENABLED" to "true", "VLRGG_NOTIFICATIONS_STORAGE_PATH" to "/tmp/vlrgg-notification-route",
        "VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID" to "vlrgg-stage1", "VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY" to "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    )

    private class ChunkedJsonContent(private val value: String) : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.Json
        override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) { channel.writeFully(value.encodeToByteArray()) }
    }
}

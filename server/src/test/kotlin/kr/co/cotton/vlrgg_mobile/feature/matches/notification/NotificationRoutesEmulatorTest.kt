package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import com.google.cloud.firestore.Firestore
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HTTP contract tests deliberately use the real Firestore adapter and a test-only verifier. */
@Category(FirestoreEmulatorCategory::class)
class NotificationRoutesEmulatorTest {
    private lateinit var firestore: Firestore
    private lateinit var store: FirestoreNotificationStore

    @Before fun setUp() {
        firestore = EmulatorFirestoreClientFactory.create(
            mapOf(
                "FIRESTORE_EMULATOR_HOST" to requireNotNull(System.getenv("FIRESTORE_EMULATOR_HOST")),
                "VLRGG_FIRESTORE_TEST_PROJECT_ID" to "demo-vlrgg-" + UUID.randomUUID().toString().replace("-", "").take(20),
            ),
        )
        store = FirestoreNotificationStore(firestore, Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC))
    }

    @After fun tearDown() { if (this::store.isInitialized) store.close() }

    @Test fun `target routes require exact app check and target capability without leaks`() = testApplication {
        application { configureSerialization(); configureErrorHandling(); configureNotificationTargetRoutes(store, FakeVerifier, setOf(APP_ID)) }
        val missingApp = client.post(NOTIFICATION_TARGETS_PATH) { json("{\"registrationToken\":\"token-a\"}") }
        val wrongApp = client.post(NOTIFICATION_TARGETS_PATH) { header(APP_CHECK, "wrong-app"); json("{\"registrationToken\":\"token-a\"}") }
        assertError(missingApp, HttpStatusCode.Unauthorized, ApiErrorCode.APP_ATTESTATION_FAILED)
        assertEquals(missingApp.bodyAsText(), wrongApp.bodyAsText())

        val targetA = register("token-a")
        val targetB = register("token-b")
        val wrong = client.get("$NOTIFICATION_TARGETS_PATH/${targetA.targetId}") { app(); authorization(targetA.targetId, TargetSecrets.generate()) }
        val missing = client.get("$NOTIFICATION_TARGETS_PATH/${targetA.targetId}") { app() }
        val cross = client.get("$NOTIFICATION_TARGETS_PATH/${targetB.targetId}") { app(); authorization(targetA.targetId, targetA.targetSecret) }
        val nonexistentId = UUID.randomUUID().toString()
        val nonexistent = client.get("$NOTIFICATION_TARGETS_PATH/$nonexistentId") { app(); authorization(nonexistentId, TargetSecrets.generate()) }
        val malformed = client.get("$NOTIFICATION_TARGETS_PATH/not-a-canonical-target") { app() }
        assertError(wrong, HttpStatusCode.Unauthorized, ApiErrorCode.TARGET_AUTHENTICATION_FAILED)
        assertEquals(wrong.bodyAsText(), missing.bodyAsText())
        assertEquals(wrong.bodyAsText(), cross.bodyAsText())
        assertEquals(wrong.bodyAsText(), nonexistent.bodyAsText())
        assertError(malformed, HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)

        val state = client.get("$NOTIFICATION_TARGETS_PATH/${targetA.targetId}") { app(); authorization(targetA.targetId, targetA.targetSecret) }
        assertEquals(HttpStatusCode.OK, state.status)
        assertFalse(state.bodyAsText().contains(targetA.targetSecret))
        assertFalse(state.bodyAsText().contains("token-a"))
    }

    @Test fun `target lifecycle rejects malformed IDs and oversized chunked payloads safely`() = testApplication {
        application { configureSerialization(); configureErrorHandling(); configureNotificationTargetRoutes(store, FakeVerifier, setOf(APP_ID)) }
        val target = register("token-a")
        val auth: HttpRequestBuilder.() -> Unit = { app(); authorization(target.targetId, target.targetSecret) }
        listOf("+1", "01", "0", "-1").forEach { raw ->
            val response = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/match-subscriptions/$raw") {
                auth(); json("{\"enabled\":true,\"expectedRevision\":\"1\"}")
            }
            assertError(response, HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        }
        val malformed = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/registration-token") { auth(); json("{") }
        assertError(malformed, HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        val oversizedToken = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/registration-token") {
            auth(); json("{\"registrationToken\":\"" + "x".repeat(4_097) + "\",\"expectedRevision\":\"1\"}")
        }
        assertError(oversizedToken, HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        val oversized = client.post(NOTIFICATION_TARGETS_PATH) {
            header(APP_CHECK, "good-app")
            setBody(lengthlessJson("{\"registrationToken\":\"" + "x".repeat(8_193) + "\"}"))
        }
        assertError(oversized, HttpStatusCode.PayloadTooLarge, ApiErrorCode.REQUEST_TOO_LARGE)
        assertFalse(oversized.bodyAsText().contains("x".repeat(32)))
    }

    @Test fun `target lifecycle keeps replay conflict global off and revoke contracts`() = testApplication {
        application { configureSerialization(); configureErrorHandling(); configureNotificationTargetRoutes(store, FakeVerifier, setOf(APP_ID)) }
        val target = register("token-a")
        val refresh = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/registration-token") { app(); authorization(target.targetId, target.targetSecret); json("{\"registrationToken\":\"token-next\",\"expectedRevision\":\"1\"}") }
        assertEquals(HttpStatusCode.OK, refresh.status)
        val replay = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/registration-token") { app(); authorization(target.targetId, target.targetSecret); json("{\"registrationToken\":\"token-next\",\"expectedRevision\":\"1\"}") }
        assertEquals(HttpStatusCode.OK, replay.status)
        val conflict = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/registration-token") { app(); authorization(target.targetId, target.targetSecret); json("{\"registrationToken\":\"different\",\"expectedRevision\":\"1\"}") }
        assertError(conflict, HttpStatusCode.Conflict, ApiErrorCode.REVISION_CONFLICT)
        val subscribe = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/match-subscriptions/7") { app(); authorization(target.targetId, target.targetSecret); json("{\"enabled\":true,\"expectedRevision\":\"2\"}") }
        assertEquals(HttpStatusCode.OK, subscribe.status)
        val off = client.put("$NOTIFICATION_TARGETS_PATH/${target.targetId}/match-subscriptions") { app(); authorization(target.targetId, target.targetSecret); json("{\"enabled\":false,\"expectedRevision\":\"3\"}") }
        assertEquals(HttpStatusCode.OK, off.status)
        val revoke = client.post("$NOTIFICATION_TARGETS_PATH/${target.targetId}/revoke") { app(); authorization(target.targetId, target.targetSecret); json("{\"expectedRevision\":\"4\"}") }
        assertEquals(HttpStatusCode.OK, revoke.status)
        val revoked = client.get("$NOTIFICATION_TARGETS_PATH/${target.targetId}") { app(); authorization(target.targetId, target.targetSecret) }
        assertError(revoked, HttpStatusCode.Unauthorized, ApiErrorCode.TARGET_AUTHENTICATION_FAILED)
    }

    private suspend fun ApplicationTestBuilder.register(token: String): RegisterTargetResponse {
        val response = client.post(NOTIFICATION_TARGETS_PATH) { header(APP_CHECK, "good-app"); json("{\"registrationToken\":\"$token\"}") }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }
    private fun HttpRequestBuilder.app() { header(APP_CHECK, "good-app") }
    private fun HttpRequestBuilder.authorization(targetId: String, secret: String) { header(HttpHeaders.Authorization, "Target $targetId.$secret") }
    private fun HttpRequestBuilder.json(value: String) { contentType(ContentType.Application.Json); setBody(value) }
    private fun lengthlessJson(value: String) = object : OutgoingContent.ReadChannelContent() {
        override val contentType = ContentType.Application.Json
        override val contentLength: Long? = null
        override fun readFrom() = ByteReadChannel(value.encodeToByteArray())
    }
    private suspend fun assertError(response: HttpResponse, status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(status, response.status)
        assertEquals(code, Json.decodeFromString<ApiErrorResponse>(response.bodyAsText()).code)
    }

    private object FakeVerifier : AppCheckVerifier {
        override suspend fun verify(evidence: AppCheckEvidence) = when (evidence.rawToken) {
            "good-app" -> VerifiedApp(APP_ID)
            "wrong-app" -> VerifiedApp("not-allowed")
            else -> null
        }
    }
    private companion object { const val APP_ID = "1:123:android:abc"; const val APP_CHECK = "X-Firebase-AppCheck" }
}

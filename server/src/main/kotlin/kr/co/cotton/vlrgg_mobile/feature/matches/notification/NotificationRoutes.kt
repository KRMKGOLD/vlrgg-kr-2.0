package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse

internal const val NOTIFICATION_TARGETS_PATH = "/api/v1/notification-targets"

@Serializable data class RegisterTargetRequest(val registrationToken: String)
@Serializable data class RegisterTargetResponse(val targetId: String, val targetSecret: String, val revision: String)
@Serializable data class RefreshRegistrationTokenRequest(val registrationToken: String, val expectedRevision: String)
@Serializable data class SetMatchSubscriptionRequest(val enabled: Boolean, val expectedRevision: String)
@Serializable data class SetAllMatchSubscriptionsRequest(val enabled: Boolean, val expectedRevision: String)
@Serializable data class RevokeTargetRequest(val expectedRevision: String)
@Serializable data class TargetMutationResponse(val revision: String, val status: String)
@Serializable data class MatchSubscriptionResponse(val matchId: String, val enabled: Boolean, val revision: String)
@Serializable data class TargetSubscriptionResponse(val matchId: String, val enabled: Boolean)
@Serializable data class TargetStateResponse(val targetId: String, val revision: String, val sendable: Boolean, val subscriptions: List<TargetSubscriptionResponse>)

/** Test composition may install this API explicitly. Main runtime never does. */
internal fun Application.configureNotificationTargetRoutes(
    store: FirestoreNotificationStore, verifier: AppCheckVerifier, allowedAppIds: Set<String>, maximumBodyBytes: Int = 8192,
) = routing {
    route(NOTIFICATION_TARGETS_PATH) {
        post {
            if (!call.verifyApp(verifier, allowedAppIds)) return@post
            val request = call.readBody<RegisterTargetRequest>(maximumBodyBytes) ?: return@post
            try { store.register(request.registrationToken).also { call.respond(HttpStatusCode.Created, RegisterTargetResponse(it.targetId, it.targetSecret, it.revision.toString())) } }
            catch (_: IllegalArgumentException) { call.invalid() }
        }
        get("/{targetId}") { call.authorizeAndGet(store, verifier, allowedAppIds)?.let { call.respond(TargetStateResponse(it.targetId, it.revision.toString(), it.sendable, it.subscriptions.map { s -> TargetSubscriptionResponse(s.matchId.toString(), s.enabled) })) } }
        put("/{targetId}/registration-token") {
            val auth = call.authorizeAndGet(store, verifier, allowedAppIds) ?: return@put; val request = call.readBody<RefreshRegistrationTokenRequest>(maximumBodyBytes) ?: return@put
            call.respondMutation(call.mutate { store.refreshRegistrationToken(auth.targetId, call.targetSecret()!!, request.registrationToken, request.expectedRevision.canonicalRevision()) }, "registration_token_refreshed")
        }
        put("/{targetId}/match-subscriptions/{matchId}") {
            val auth = call.authorizeAndGet(store, verifier, allowedAppIds) ?: return@put
            val rawMatch = call.parameters["matchId"]
            val match = rawMatch?.takeIf { Regex("^[1-9][0-9]*$").matches(it) }?.toLongOrNull() ?: return@put call.invalid()
            val request = call.readBody<SetMatchSubscriptionRequest>(maximumBodyBytes) ?: return@put
            val revision = call.mutate { store.setSubscription(auth.targetId, call.targetSecret()!!, match, request.enabled, request.expectedRevision.canonicalRevision()) }
            if (revision != null) call.respond(MatchSubscriptionResponse(match.toString(), request.enabled, revision.toString()))
        }
        put("/{targetId}/match-subscriptions") {
            val auth = call.authorizeAndGet(store, verifier, allowedAppIds) ?: return@put; val request = call.readBody<SetAllMatchSubscriptionsRequest>(maximumBodyBytes) ?: return@put
            if (request.enabled) return@put call.invalid()
            call.respondMutation(call.mutate { store.disableAll(auth.targetId, call.targetSecret()!!, request.expectedRevision.canonicalRevision()) }, "all_subscriptions_disabled")
        }
        post("/{targetId}/revoke") {
            val auth = call.authorizeAndGet(store, verifier, allowedAppIds) ?: return@post; val request = call.readBody<RevokeTargetRequest>(maximumBodyBytes) ?: return@post
            call.respondMutation(call.mutate { store.revoke(auth.targetId, call.targetSecret()!!, request.expectedRevision.canonicalRevision()) }, "revoked")
        }
    }
}

private suspend fun ApplicationCall.verifyApp(verifier: AppCheckVerifier, allowed: Set<String>): Boolean {
    val raw = request.headers["X-Firebase-AppCheck"] ?: return appFailed()
    return if (verifier.verify(AppCheckEvidence(raw))?.firebaseAppId in allowed) true else appFailed()
}
private suspend fun ApplicationCall.authorizeAndGet(store: FirestoreNotificationStore, verifier: AppCheckVerifier, allowed: Set<String>): TargetRecord? {
    if (!verifyApp(verifier, allowed)) return null
    val targetId = parameters["targetId"] ?: return targetFailed()
    if (runCatching { requireCanonicalTargetId(targetId) }.isFailure) {
        invalid()
        return null
    }
    return try { store.readAuthorized(targetId, targetSecret() ?: return targetFailed()) ?: targetFailed() } catch (_: IllegalArgumentException) { targetFailed() }
}
private fun ApplicationCall.targetSecret(): String? {
    val target = parameters["targetId"] ?: return null
    val raw = request.headers[HttpHeaders.Authorization] ?: return null
    return Regex("^Target ${Regex.escape(target)}\\.([A-Za-z0-9_-]{43})$").matchEntire(raw)?.groupValues?.get(1)
}
private suspend fun ApplicationCall.appFailed(): Boolean { respond(HttpStatusCode.Unauthorized, ApiErrorResponse(ApiErrorCode.APP_ATTESTATION_FAILED, "App attestation failed.")); return false }
private suspend fun ApplicationCall.targetFailed(): Nothing? { respond(HttpStatusCode.Unauthorized, ApiErrorResponse(ApiErrorCode.TARGET_AUTHENTICATION_FAILED, "Target authentication failed.")); return null }
private suspend fun ApplicationCall.invalid() { respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiErrorCode.INVALID_REQUEST, "Request input is invalid.")) }
private fun String.canonicalRevision(): Long = takeIf { Regex("^[1-9][0-9]*$").matches(it) }?.toLongOrNull() ?: throw IllegalArgumentException()
private suspend inline fun ApplicationCall.mutate(block: () -> Long): Long? = try { block() } catch (_: SecurityException) { targetFailed(); null } catch (_: RevisionConflictException) { respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.REVISION_CONFLICT, "Revision conflicts with the current target state.")); null } catch (_: RevisionExhaustedException) { respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.REVISION_EXHAUSTED, "Target revision is exhausted.")); null } catch (_: SubscriptionLimitExceededException) { respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.SUBSCRIPTION_LIMIT, "Active subscription limit has been reached.")); null } catch (_: ActiveMatchCapacityExceededException) { respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.ACTIVE_MATCH_CAPACITY_EXCEEDED, "Active Match capacity has been reached.")); null } catch (_: IllegalArgumentException) { invalid(); null }
private suspend fun ApplicationCall.respondMutation(revision: Long?, status: String) { if (revision != null) respond(TargetMutationResponse(revision.toString(), status)) }
private suspend inline fun <reified T> ApplicationCall.readBody(max: Int): T? {
    if ((request.contentLength() ?: 0) > max) { respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(ApiErrorCode.REQUEST_TOO_LARGE, "Request body is too large.")); return null }
    val channel = receiveChannel()
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(minOf(max, 1024))
    var total = 0
    while (true) {
        val read = channel.readAvailable(buffer)
        if (read == -1) break
        if (read == 0) continue
        if (read > max - total) {
            channel.cancel()
            respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(ApiErrorCode.REQUEST_TOO_LARGE, "Request body is too large."))
            return null
        }
        bytes.write(buffer, 0, read)
        total += read
    }
    val body = bytes.toString(Charsets.UTF_8.name())
    return try { Json.decodeFromString<T>(body) } catch (_: Exception) { invalid(); null }
}

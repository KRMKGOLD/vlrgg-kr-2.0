package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readFully
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.routing.describeLocalNotificationOperation
import kr.co.cotton.vlrgg_mobile.routing.notificationMatchIdPath

internal const val MATCH_NOTIFICATION_API_PATH = "/api/v1/match-notifications"

/** Local-only API DTOs intentionally use no provider identity terminology. */
@Serializable data class TargetRequest(val registrationValue: String, val revision: String)
@Serializable data class SubscriptionRequest(val registrationValue: String, val revision: String, val active: Boolean)
@Serializable data class GlobalStateRequest(val registrationValue: String, val revision: String, val active: Boolean)
@Serializable data class TargetStateRequest(val registrationValue: String)
@Serializable data class TargetResponse(val acceptedRevision: String, val result: String)
@Serializable data class SubscriptionStateResponse(val matchId: String, val active: Boolean)
@Serializable data class TargetStateResponse(val acceptedRevision: String, val subscriptions: List<SubscriptionStateResponse>)

/**
 * These endpoints are deliberately installed only by the local-loopback preflight.  A
 * registration value is an address carried to the store, never an identity or a secret.
 */
internal fun Application.configureNotificationRoutes(store: NotificationStore, requestBodyBytes: Int, registrationValueMaxBytes: Int) {
    routing {
        route(MATCH_NOTIFICATION_API_PATH) {
            put("targets") {
                call.handleTarget(store, requestBodyBytes, registrationValueMaxBytes)
            }.describeLocalNotificationOperation<TargetRequest, TargetResponse>("syncNotificationTarget", "Synchronize a local notification target")
            post("state") {
                call.handleState(store, requestBodyBytes, registrationValueMaxBytes)
            }.describeLocalNotificationOperation<TargetStateRequest, TargetStateResponse>("getNotificationState", "Read local notification state")
            put("subscriptions/{matchId}") {
                call.handleSubscription(store, requestBodyBytes, registrationValueMaxBytes)
            }.describeLocalNotificationOperation<SubscriptionRequest, TargetResponse>("setNotificationSubscription", "Set one local Match notification subscription") { notificationMatchIdPath() }
            put("global-state") {
                call.handleGlobalOff(store, requestBodyBytes, registrationValueMaxBytes)
            }.describeLocalNotificationOperation<GlobalStateRequest, TargetResponse>("disableAllNotificationSubscriptions", "Disable all subscriptions for one local target")
        }
    }
}

private suspend fun ApplicationCall.handleTarget(store: NotificationStore, requestBodyBytes: Int, registrationValueMaxBytes: Int) {
    val request = notificationBody<TargetRequest>(requestBodyBytes) ?: return
    val revision = request.revision.positiveLongOrNull() ?: return invalidRequest()
    if (!request.registrationValue.isValidRegistrationValue(registrationValueMaxBytes)) return invalidRequest()
    val result = try { store.findOrRegister(request.registrationValue, "target", revision) } catch (_: IllegalArgumentException) { return invalidRequest() }
    respondTargetResult(store, result)
}

private suspend fun ApplicationCall.handleState(store: NotificationStore, requestBodyBytes: Int, registrationValueMaxBytes: Int) {
    val request = notificationBody<TargetStateRequest>(requestBodyBytes) ?: return
    if (!request.registrationValue.isValidRegistrationValue(registrationValueMaxBytes)) return invalidRequest()
    // API-104: absence and a logically erased target expose the same revision-zero empty projection.
    val state = store.stateForRegistration(request.registrationValue) ?: NotificationStateProjection(0, emptyList())
    respond(TargetStateResponse(state.acceptedRevision.toString(), state.subscriptions.map { SubscriptionStateResponse(it.matchId.toString(), it.active) }))
}

private suspend fun ApplicationCall.handleSubscription(store: NotificationStore, requestBodyBytes: Int, registrationValueMaxBytes: Int) {
    val matchId = parameters["matchId"]?.positiveLongOrNull() ?: return invalidRequest()
    val request = notificationBody<SubscriptionRequest>(requestBodyBytes) ?: return
    val revision = request.revision.positiveLongOrNull() ?: return invalidRequest()
    if (!request.registrationValue.isValidRegistrationValue(registrationValueMaxBytes)) return invalidRequest()
    val result = try { store.reconcileSubscription(request.registrationValue, matchId, request.active, revision) }
    catch (_: SubscriptionLimitExceededException) { return subscriptionLimit() }
    catch (_: IllegalArgumentException) { return invalidRequest() }
    respondTargetResult(store, result)
}

private suspend fun ApplicationCall.handleGlobalOff(store: NotificationStore, requestBodyBytes: Int, registrationValueMaxBytes: Int) {
    val request = notificationBody<GlobalStateRequest>(requestBodyBytes) ?: return
    val revision = request.revision.positiveLongOrNull() ?: return invalidRequest()
    if (request.active || !request.registrationValue.isValidRegistrationValue(registrationValueMaxBytes)) return invalidRequest()
    val result = try { store.reconcileGlobalOff(request.registrationValue, revision) } catch (_: IllegalArgumentException) { return invalidRequest() }
    respondTargetResult(store, result)
}

private suspend inline fun <reified T> ApplicationCall.notificationBody(maximumBytes: Int): T? {
    val declaredLength = request.contentLength()
    if (declaredLength != null && declaredLength > maximumBytes) {
        respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(ApiErrorCode.REQUEST_TOO_LARGE, "Request body is too large."))
        return null
    }
    // Do not allocate an unbounded String for chunked/no-Content-Length bodies.
    val bytes = try {
        receiveChannel().readRemaining(maximumBytes.toLong() + 1).let { packet ->
            val length = packet.remaining.toInt()
            ByteArray(length).also(packet::readFully)
        }
    } catch (error: CancellationException) { throw error } catch (_: Exception) { invalidRequest(); return null }
    if (bytes.size > maximumBytes) {
        respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(ApiErrorCode.REQUEST_TOO_LARGE, "Request body is too large."))
        return null
    }
    return try { Json.decodeFromString<T>(bytes.decodeToString(throwOnInvalidSequence = true)) } catch (error: CancellationException) { throw error } catch (_: Exception) { invalidRequest(); null }
}

private suspend fun ApplicationCall.respondTargetResult(store: NotificationStore, result: TargetLookupResult) {
    when (result.resolution) {
        TargetResolution.TARGET_REFRESH_REQUIRED -> respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.TARGET_REFRESH_REQUIRED, "Target refresh is required."))
        else -> when (result.revision) {
            RevisionResult.CONFLICT -> respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.REVISION_CONFLICT, "Revision conflicts with the current target state."))
            RevisionResult.REVISION_EXHAUSTED -> respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.REVISION_EXHAUSTED, "Target revision is exhausted."))
            null -> respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.TARGET_REFRESH_REQUIRED, "Target refresh is required."))
            else -> {
                val acceptedRevision = result.target?.let(store::targetProjection)?.acceptedRevision
                    ?: return respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.TARGET_REFRESH_REQUIRED, "Target refresh is required."))
                respond(TargetResponse(acceptedRevision.toString(), result.revision.name.lowercase()))
            }
        }
    }
}

private suspend fun ApplicationCall.invalidRequest() = respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiErrorCode.INVALID_REQUEST, "Request input is invalid."))
private suspend fun ApplicationCall.subscriptionLimit() = respond(HttpStatusCode.Conflict, ApiErrorResponse(ApiErrorCode.SUBSCRIPTION_LIMIT, "Active subscription limit has been reached."))
private fun String.positiveLongOrNull(): Long? = if (matches(Regex("^[1-9][0-9]*$"))) toLongOrNull() else null
private fun String.isValidRegistrationValue(maximumBytes: Int): Boolean =
    isNotBlank() && none { it.isISOControl() } && encodeToByteArray().size <= maximumBytes

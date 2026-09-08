package kr.co.cotton.vlrgg_mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.co.cotton.vlrgg_mobile.data.remote.PublicApiBusyException
import kr.co.cotton.vlrgg_mobile.data.remote.PublicApiResponseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend inline fun <reified T> HttpClient.getPublicJson(
    path: String,
    noinline configure: HttpRequestBuilder.() -> Unit = {},
): T = prepareGet(path) {
    expectSuccess = false
    configure()
}.execute { response ->
    if (response.status.value in 200..299) {
        response.body()
    } else {
        val errorBody = response.readErrorBodyAtMostLimit()
        if (!errorBody.isOverflowed && response.isRecognizedBusy(errorBody.content.errorCodeOrNull())) {
            throw PublicApiBusyException(response.retryDelay())
        }
        throw PublicApiResponseException()
    }
}

@PublishedApi
internal suspend fun HttpResponse.readErrorBodyAtMostLimit(): BoundedErrorBody {
    val channel = bodyAsChannel()
    try {
        val bytes = ByteArray(MAX_ERROR_BODY_BYTES + 1)
        var size = 0
        while (size < bytes.size) {
            val read = channel.readAvailable(bytes, size, bytes.size - size)
            if (read == -1) break
            if (read > 0) size += read
        }
        return BoundedErrorBody(
            content = bytes.copyOf(minOf(size, MAX_ERROR_BODY_BYTES)).decodeToString(),
            isOverflowed = size > MAX_ERROR_BODY_BYTES,
        )
    } finally {
        // The response may be larger than the parsing limit; never leave it draining in the client.
        channel.cancel()
    }
}

@PublishedApi
internal fun String.errorCodeOrNull(): String? = try {
    Json.parseToJsonElement(this)
        .jsonObject[ERROR_CODE_KEY]
        ?.jsonPrimitive
        ?.contentOrNull
} catch (_: Exception) {
    null
}

@PublishedApi
internal fun HttpResponse.isRecognizedBusy(errorCode: String?): Boolean =
    (status == HttpStatusCode.TooManyRequests && errorCode == RATE_LIMITED) ||
        (status == HttpStatusCode.ServiceUnavailable && errorCode == SERVER_BUSY)

@PublishedApi
internal fun HttpResponse.retryDelay(): Duration =
    headers[HttpHeaders.RetryAfter]
        ?.takeIf { RETRY_AFTER_DELTA_SECONDS.matches(it) }
        ?.toIntOrNull()
        ?.takeIf { it in MIN_RETRY_DELAY_SECONDS..MAX_RETRY_DELAY_SECONDS }
        ?.seconds
        ?: DEFAULT_RETRY_DELAY

@PublishedApi
internal data class BoundedErrorBody(
    val content: String,
    val isOverflowed: Boolean,
)

@PublishedApi
internal const val MAX_ERROR_BODY_BYTES = 8 * 1024
private const val ERROR_CODE_KEY = "code"
private const val RATE_LIMITED = "RATE_LIMITED"
private const val SERVER_BUSY = "SERVER_BUSY"
private const val MIN_RETRY_DELAY_SECONDS = 1
private const val MAX_RETRY_DELAY_SECONDS = 60
private val RETRY_AFTER_DELTA_SECONDS = Regex("[0-9]+")
private val DEFAULT_RETRY_DELAY = 2.seconds

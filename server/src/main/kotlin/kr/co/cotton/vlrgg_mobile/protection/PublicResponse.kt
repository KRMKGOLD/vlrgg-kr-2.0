package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import kr.co.cotton.vlrgg_mobile.cache.CachedPublicResponse
import kr.co.cotton.vlrgg_mobile.cache.ResponseCache
import kr.co.cotton.vlrgg_mobile.cache.ResponseCacheRoute
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse
import kr.co.cotton.vlrgg_mobile.common.http.PublicResponseTooLargeFailure
import kr.co.cotton.vlrgg_mobile.common.http.SOURCE_PARSING_FAILURE_MESSAGE
import kr.co.cotton.vlrgg_mobile.common.http.SOURCE_PARSING_FAILURE_STATUS
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure

internal const val MAX_PUBLIC_JSON_BYTES = 2 * 1024 * 1024
private val publicJson = Json {
    encodeDefaults = true
    explicitNulls = true
}

/** A finished public JSON body; the miss path and a cache hit both reach the client through [respondPublicJsonBody]. */
internal interface PublicJsonBody {
    suspend fun writeTo(channel: ByteWriteChannel)
}

private class PublicJsonBytes(private val bytes: ByteArray) : PublicJsonBody {
    override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(bytes, 0, bytes.size)
}

internal class CappedOutputStream(private val maxBytes: Int) : OutputStream(), PublicJsonBody {
    private val delegate = DirectByteArrayOutputStream(maxBytes)

    override fun write(value: Int) = write(byteArrayOf(value.toByte()))
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > maxBytes - delegate.size) throw PublicJsonTooLargeException()
        delegate.write(bytes, offset, length)
    }
    override suspend fun writeTo(channel: ByteWriteChannel) = delegate.writeTo(channel)
    fun toUtf8String(): String = delegate.toString(Charsets.UTF_8)
}

private class DirectByteArrayOutputStream(initialSize: Int) : ByteArrayOutputStream(initialSize) {
    val size: Int get() = count

    suspend fun writeTo(channel: ByteWriteChannel) {
        channel.writeFully(buf, 0, count)
    }
}

internal class PublicJsonTooLargeException : RuntimeException()

/** Serializes [value] within the public body cap; an oversized body is a public `RESPONSE_TOO_LARGE`. */
@OptIn(ExperimentalSerializationApi::class)
internal fun <T> encodePublicJson(serializer: SerializationStrategy<T>, value: T): CappedOutputStream {
    val output = CappedOutputStream(MAX_PUBLIC_JSON_BYTES)
    try {
        publicJson.encodeToStream(serializer, value, output)
    } catch (overflow: PublicJsonTooLargeException) {
        throw PublicResponseTooLargeFailure(overflow)
    }
    return output
}

internal suspend inline fun <reified T> ApplicationCall.respondPublicJson(value: T) {
    respondPublicJsonBody(encodePublicJson(serializer<T>(), value))
}

internal suspend fun ApplicationCall.respondPublicJsonBody(body: PublicJsonBody) {
    respond(
        object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType = ContentType.Application.Json
            override val status: HttpStatusCode = HttpStatusCode.OK

            override suspend fun writeTo(channel: ByteWriteChannel) {
                body.writeTo(channel)
            }
        },
    )
}

/**
 * Serves a public GET through [cache]. A hit replays the stored success bytes or the cached parsing failure without
 * calling [load]. On a miss, a success is stored before the response is sent, a [SourceParsingFailure] is cached
 * for 60 seconds and then rethrown unchanged, and every other failure propagates without being stored.
 * [identifier] must be validated and normalized route values.
 */
internal suspend inline fun <reified T> ApplicationCall.respondCachedPublicJson(
    cache: ResponseCache,
    route: ResponseCacheRoute,
    vararg identifier: String,
    noinline ttlSeconds: (T) -> Int,
    noinline load: suspend () -> T,
) {
    respondCachedPublicJson(cache, route, identifier.toList(), serializer<T>(), ttlSeconds, load)
}

internal suspend fun <T> ApplicationCall.respondCachedPublicJson(
    cache: ResponseCache,
    route: ResponseCacheRoute,
    identifier: List<String>,
    serializer: SerializationStrategy<T>,
    ttlSeconds: (T) -> Int,
    load: suspend () -> T,
) {
    if (!cache.isEnabled) {
        respondPublicJsonBody(encodePublicJson(serializer, load()))
        return
    }
    val key = cache.key(route, *identifier.toTypedArray())
    when (val cached = cache.lookup(key)) {
        is CachedPublicResponse.Success -> return respondPublicJsonBody(PublicJsonBytes(cached.body))
        CachedPublicResponse.SourceParsingFailure -> return respondCachedSourceParsingFailure()
        null -> Unit
    }
    val value = try {
        load()
    } catch (failure: SourceParsingFailure) {
        cache.storeSourceParsingFailure(key)
        throw failure
    }
    val body = encodePublicJson(serializer, value)
    cache.store(key, body.toUtf8String(), ttlSeconds(value))
    respondPublicJsonBody(body)
}

/**
 * Same envelope as the StatusPages response for the original failure, sent directly so a replay creates no
 * failure event and no new Error Reporting group.
 */
private suspend fun ApplicationCall.respondCachedSourceParsingFailure() {
    respond(
        SOURCE_PARSING_FAILURE_STATUS,
        ApiErrorResponse(code = ApiErrorCode.SOURCE_PARSING_FAILURE, message = SOURCE_PARSING_FAILURE_MESSAGE),
    )
}

package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.ExperimentalSerializationApi
import kr.co.cotton.vlrgg_mobile.common.http.PublicResponseTooLargeFailure

internal const val MAX_PUBLIC_JSON_BYTES = 2 * 1024 * 1024
private val publicJson = Json {
    encodeDefaults = true
    explicitNulls = true
}

internal class CappedOutputStream(private val maxBytes: Int) : OutputStream() {
    private val delegate = DirectByteArrayOutputStream(maxBytes)

    override fun write(value: Int) = write(byteArrayOf(value.toByte()))
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > maxBytes - delegate.size) throw PublicJsonTooLargeException()
        delegate.write(bytes, offset, length)
    }
    suspend fun writeTo(channel: ByteWriteChannel) = delegate.writeTo(channel)
}

private class DirectByteArrayOutputStream(initialSize: Int) : ByteArrayOutputStream(initialSize) {
    val size: Int get() = count

    suspend fun writeTo(channel: ByteWriteChannel) {
        channel.writeFully(buf, 0, count)
    }
}

internal class PublicJsonTooLargeException : RuntimeException()

@OptIn(ExperimentalSerializationApi::class)
internal suspend inline fun <reified T> ApplicationCall.respondPublicJson(value: T) {
    val output = CappedOutputStream(MAX_PUBLIC_JSON_BYTES)
    try {
        publicJson.encodeToStream(value, output)
    } catch (overflow: PublicJsonTooLargeException) {
        throw PublicResponseTooLargeFailure(overflow)
    }
    respond(
        object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType = ContentType.Application.Json
            override val status: HttpStatusCode = HttpStatusCode.OK

            override suspend fun writeTo(channel: ByteWriteChannel) {
                output.writeTo(channel)
            }
        },
    )
}

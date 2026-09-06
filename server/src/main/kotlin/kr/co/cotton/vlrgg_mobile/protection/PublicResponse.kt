package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
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
    private val delegate = ByteArrayOutputStream()
    private var count = 0

    override fun write(value: Int) = write(byteArrayOf(value.toByte()))
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > maxBytes - count) throw PublicJsonTooLargeException()
        delegate.write(bytes, offset, length)
        count += length
    }
    fun toByteArray(): ByteArray = delegate.toByteArray()
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
    respondBytes(output.toByteArray(), ContentType.Application.Json, HttpStatusCode.OK)
}

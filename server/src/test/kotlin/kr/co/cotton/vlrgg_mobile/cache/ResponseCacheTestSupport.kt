package kr.co.cotton.vlrgg_mobile.cache

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.http.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** In-memory store with call records and injectable delays or failures; honors `NX` like Redis. */
internal class FakeResponseCacheStore : ResponseCacheStore {
    data class SetCall(val key: String, val value: String, val ttlSeconds: Int, val onlyIfAbsent: Boolean)

    val values = ConcurrentHashMap<String, String>()
    val gets = CopyOnWriteArrayList<String>()
    val sets = CopyOnWriteArrayList<SetCall>()

    @Volatile var beforeGet: suspend (String) -> Unit = {}
    @Volatile var beforeSet: suspend (SetCall) -> Unit = {}

    override suspend fun get(key: String): String? {
        gets += key
        beforeGet(key)
        return values[key]
    }

    override suspend fun set(key: String, value: String, ttlSeconds: Int, onlyIfAbsent: Boolean): Boolean {
        val call = SetCall(key, value, ttlSeconds, onlyIfAbsent)
        sets += call
        beforeSet(call)
        if (onlyIfAbsent && values.containsKey(key)) return false
        values[key] = value
        return true
    }

    /** Simulates TTL expiry of one entry. */
    fun expire(key: String) {
        values.remove(key)
    }
}

/**
 * Loopback HTTP server that behaves like the Upstash REST root endpoint for GET and SET [EX] [NX].
 * [reply] can override the answer per request. It never leaves the local machine.
 */
internal class LoopbackUpstash(private val token: String = TEST_UPSTASH_TOKEN) : AutoCloseable {
    data class Received(
        val method: String,
        val path: String,
        val authorization: String?,
        val contentType: String?,
        val command: List<String>,
        val remotePort: Int,
    )

    class Reply(val status: Int, val body: String, val delayMillis: Long = 0, val chunked: Boolean = false)

    /**
     * Sends 200 headers and a partial JSON body at once, signals [headersSent], then holds the rest of the body
     * until [release] opens. Models a store that stalls mid-response.
     */
    class StalledBody(val headersSent: CountDownLatch = CountDownLatch(1), val release: CountDownLatch = CountDownLatch(1))

    @Volatile var stalled: StalledBody? = null

    val values = ConcurrentHashMap<String, String>()
    val received = CopyOnWriteArrayList<Received>()
    @Volatile var reply: ((Received) -> Reply?)? = null

    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { server ->
        server.executor = executor
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    val uri: URI get() = URI("http://${server.address.address.hostAddress}:${server.address.port}/")

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val command = runCatching { Json.parseToJsonElement(body).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
            val received = Received(
                method = exchange.requestMethod,
                path = exchange.requestURI.toString(),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                command = command,
                remotePort = exchange.remoteAddress.port,
            )
            this.received += received
            stalled?.let { stall ->
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("{\"result\":\"".toByteArray(Charsets.UTF_8))
                exchange.responseBody.flush()
                stall.headersSent.countDown()
                stall.release.await()
                runCatching { exchange.responseBody.write("x\"}".toByteArray(Charsets.UTF_8)) }
                return
            }
            val answer = reply?.invoke(received) ?: defaultReply(received)
            if (answer.delayMillis > 0) Thread.sleep(answer.delayMillis)
            val bytes = answer.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(answer.status, if (answer.chunked) 0 else if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
        }
    }

    private fun defaultReply(received: Received): Reply {
        if (received.authorization != "Bearer $token") return Reply(401, "{\"error\":\"Unauthorized\"}")
        val command = received.command
        val result = when (command.firstOrNull()) {
            "GET" -> values[command[1]]?.let(::JsonPrimitive) ?: JsonNull
            "SET" -> if ("NX" in command.drop(3) && values.containsKey(command[1])) {
                JsonNull
            } else {
                values[command[1]] = command[2]
                JsonPrimitive("OK")
            }
            else -> return Reply(400, "{\"error\":\"ERR unknown command\"}")
        }
        return Reply(200, buildJsonObject { put("result", result) }.toString())
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}

internal const val TEST_UPSTASH_URL = "https://test-database.upstash.io"
internal const val TEST_UPSTASH_TOKEN = "AXtestTOKENsentinel0123456789="
internal const val TEST_DEPLOY_SHA = "0123456789abcdef0123456789abcdef01234567"

internal fun enabledTestCacheConfig(): ResponseCacheConfig.Enabled = ResponseCacheConfig.Enabled(
    restUrl = Url(TEST_UPSTASH_URL),
    token = TEST_UPSTASH_TOKEN,
    deploySha = TEST_DEPLOY_SHA,
)

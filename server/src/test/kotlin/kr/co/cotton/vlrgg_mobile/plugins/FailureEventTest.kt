package kr.co.cotton.vlrgg_mobile.plugins

import io.ktor.http.Url
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.co.cotton.vlrgg_mobile.common.http.InternalServerFailure
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FailureEventTest {
    private val environment = mapOf(
        "K_SERVICE" to "vlrgg-validation",
        "K_REVISION" to "vlrgg-validation-00001",
        "GOOGLE_CLOUD_PROJECT" to "vlrgg-test-project",
    )

    @Test
    fun `only reportable categories include error reporting stack data`() {
        val formatter = FailureEventFormatter(environment)
        val cause = IllegalStateException("credential-sentinel").withFrames(4)
        val cases = listOf(
            InvalidInputFailure(cause) to FailureSeverity.WARN,
            UpstreamNetworkFailure(Url("https://vlr.gg/private?credential-sentinel"), cause) to FailureSeverity.WARN,
            InternalServerFailure(cause) to FailureSeverity.ERROR,
            SourceParsingFailure(Url("https://vlr.gg/private?credential-sentinel"), cause) to FailureSeverity.ERROR,
        )

        cases.forEach { (failure, severity) ->
            val event = formatter.format(failure)
            val json = Json.parseToJsonElement(event.json).jsonObject
            assertEquals(severity, event.severity)
            assertEquals(severity.name, json.getValue("severity").jsonPrimitive.content)
            assertEquals(severity == FailureSeverity.ERROR, "@type" in json)
            assertEquals(severity == FailureSeverity.ERROR, json.getValue("message").jsonPrimitive.content.contains("\tat "))
            assertFalse(event.json.contains("credential-sentinel"))
        }
    }

    @Test
    fun `unsafe throwable text and frame metadata never enter valid json`() {
        val cause = HostileException("credential-sentinel")
        cause.stackTrace = arrayOf(
            StackTraceElement("missing.CredentialSentinel", "credential-sentinel", "../credential-sentinel\r\n", 99),
            StackTraceElement(FailureEventFormatter::class.java.name, "format", "credential-sentinel.kt", 10),
            safeFrame(),
        )

        val event = FailureEventFormatter(environment).format(InternalServerFailure(cause))
        val json = Json.parseToJsonElement(event.json).jsonObject
        val message = json.getValue("message").jsonPrimitive.content
        assertFalse(event.json.contains("credential-sentinel"))
        assertFalse(event.json.contains("../"))
        assertContainsOnce(message, "java.lang.Thread.run(Thread.java:")
        assertTrue(json.getValue("truncation").jsonObject.getValue("accessor_failure").jsonPrimitive.boolean)
    }

    @Test
    fun `stack cause candidate and record limits stay bounded`() {
        val fifthCause = Exception("fifth").withFrames(40)
        val fourthCause = IllegalArgumentException("fourth", fifthCause).withFrames(40)
        val thirdCause = IllegalStateException("third", fourthCause).withFrames(40)
        val secondCause = UnsupportedOperationException("second", thirdCause).withFrames(40)
        val firstCause = RuntimeException("first", secondCause).withFrames(140)

        val event = FailureEventFormatter(environment).format(InternalServerFailure(firstCause))
        val json = Json.parseToJsonElement(event.json).jsonObject
        val message = json.getValue("message").jsonPrimitive.content
        val truncation = json.getValue("truncation").jsonObject
        assertTrue(message.lineSequence().count { it.startsWith("\tat ") } <= 32)
        assertTrue(message.lineSequence().count { it.startsWith("Caused by: ") } <= 3)
        assertTrue(truncation.getValue("frames").jsonPrimitive.boolean)
        assertTrue(truncation.getValue("candidates").jsonPrimitive.boolean)
        assertTrue(truncation.getValue("causes").jsonPrimitive.boolean)
        assertTrue((event.json + System.lineSeparator()).toByteArray(StandardCharsets.UTF_8).size <= 16 * 1024)
    }

    @Test
    fun `frame and cause boundaries are exact and cycles terminate`() {
        val formatter = FailureEventFormatter(environment)
        listOf(31, 32, 33).forEach { count ->
            val json = formatter.format(InternalServerFailure(RuntimeException().withFrames(count))).parsed()
            assertEquals(minOf(count, 32), json.message().lineSequence().count { it.startsWith("\tat ") })
            assertEquals(count > 32, json.truncation("frames"))
        }
        listOf(3, 4, 5).forEach { throwableCount ->
            val json = formatter.format(InternalServerFailure(causeChain(throwableCount))).parsed()
            assertEquals(minOf(throwableCount - 1, 3), json.message().lineSequence().count { it.startsWith("Caused by: ") })
            assertEquals(throwableCount > 4, json.truncation("causes"))
        }

        val first = MutableCauseException()
        val second = MutableCauseException()
        first.next = second
        second.next = first
        val cycle = formatter.format(InternalServerFailure(first)).parsed()
        assertTrue(cycle.truncation("cycle"))
        assertTrue(cycle.message().lineSequence().count { it.startsWith("Caused by: ") } <= 1)
    }

    @Test
    fun `oversized validated frames are removed whole before the stdout byte boundary`() {
        val className = "kr.co.cotton.vlrgg_mobile.plugins.OversizedFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrameFrame"
        val methodName = "methodSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegmentSegment"
        val fileName = "${className.substringAfterLast('.')}.java"
        val constructor = Class.forName(className).getConstructor(Throwable::class.java)
        var cause: Throwable? = null
        repeat(4) {
            cause = (constructor.newInstance(cause) as Throwable).apply {
                stackTrace = Array(8) { StackTraceElement(className, methodName, fileName, Int.MAX_VALUE) }
            }
        }

        val maximumMetadata = mapOf(
            "K_SERVICE" to "s".repeat(63),
            "K_REVISION" to "r".repeat(63),
            "GOOGLE_CLOUD_PROJECT" to "p${"1".repeat(61)}z",
        )
        val event = FailureEventFormatter(maximumMetadata).format(
            SourceParsingFailure(Url("https://vlr.gg/"), cause as Exception),
            listOf("a".repeat(32) + "/18446744073709551615;o=1"),
        )
        val json = event.parsed()
        assertTrue(json.truncation("bytes"), "bytes=${event.json.toByteArray(StandardCharsets.UTF_8).size}, frames=${json.message().lineSequence().count { it.startsWith("\tat ") }}")
        assertTrue(json.message().lineSequence().count { it.startsWith("\tat ") } < 32)
        assertTrue((event.json + System.lineSeparator()).toByteArray(StandardCharsets.UTF_8).size <= 16 * 1024)
        Json.parseToJsonElement(event.json)
    }

    @Test
    fun `throwing cause and stack accessors have bounded safe fallbacks`() {
        listOf(ThrowingStackException(), LinkageStackException()).forEach { cause ->
            val event = FailureEventFormatter(environment).format(InternalServerFailure(cause))
            val json = event.parsed()
            assertTrue(json.truncation("accessor_failure"))
            assertNotNull(json["context"])
            assertFalse(event.json.contains("credential-sentinel"))
        }
    }

    @Test
    fun `trace is accepted only from one fully valid header and trusted project`() {
        val formatter = FailureEventFormatter(environment)
        val traceId = "ABCDEF0123456789ABCDEF0123456789"
        val valid = formatter.format(InvalidInputFailure(), listOf("$traceId/18446744073709551615;o=1"))
        val validJson = Json.parseToJsonElement(valid.json).jsonObject
        assertEquals(
            "projects/vlrgg-test-project/traces/${traceId.lowercase()}",
            validJson.getValue("logging.googleapis.com/trace").jsonPrimitive.content,
        )
        assertFalse("spanId" in validJson)
        assertFalse("trace_sampled" in validJson)

        listOf(
            listOf(traceId, traceId),
            listOf("$traceId/18446744073709551616"),
            listOf("$traceId;o=2"),
            listOf("credential-sentinel"),
        ).forEach { headers ->
            val json = Json.parseToJsonElement(formatter.format(InvalidInputFailure(), headers).json).jsonObject
            assertFalse("logging.googleapis.com/trace" in json)
        }
        val invalidProject = FailureEventFormatter(environment + ("GOOGLE_CLOUD_PROJECT" to "credential/sentinel"))
        assertFalse("logging.googleapis.com/trace" in Json.parseToJsonElement(
            invalidProject.format(InvalidInputFailure(), listOf(traceId)).json,
        ).jsonObject)
    }

    @Test
    fun `category admission is exact under concurrency and resets at boundary`() = runBlocking {
        var now = 0L
        val diagnostics = FailureDiagnostics({ now })
        val decisions = ConcurrentLinkedQueue<Boolean>()
        List(100) {
            async(Dispatchers.Default) { decisions += diagnostics.admit(FailureCategory.INTERNAL) }
        }.awaitAll()
        assertEquals(4, decisions.count { it })
        assertEquals(96, decisions.count { !it })
        assertTrue(diagnostics.admit(FailureCategory.EXPECTED))
        val independentApplicationBudget = FailureDiagnostics({ now })
        repeat(4) { assertTrue(independentApplicationBudget.admit(FailureCategory.INTERNAL)) }
        now = 59_999
        assertFalse(diagnostics.admit(FailureCategory.INTERNAL))
        now = 60_000
        assertTrue(diagnostics.admit(FailureCategory.INTERNAL))
    }

    @Test
    fun `metadata fallback is fixed and reflection linkage failures are contained`() {
        val rejectingLoader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == IllegalStateException::class.java.name) throw NoClassDefFoundError("credential-sentinel")
                return super.loadClass(name, resolve)
            }
        }
        val failure = InternalServerFailure(IllegalStateException("credential-sentinel").withFrames(0))
        val json = Json.parseToJsonElement(FailureEventFormatter(emptyMap(), rejectingLoader).format(failure).json).jsonObject
        assertEquals("vlrgg-server-local", json.getValue("serviceContext").jsonObject.getValue("service").jsonPrimitive.content)
        assertEquals("local", json.getValue("serviceContext").jsonObject.getValue("version").jsonPrimitive.content)
        assertTrue(json.getValue("truncation").jsonObject.getValue("accessor_failure").jsonPrimitive.boolean)
        assertFalse(json.toString().contains("credential-sentinel"))
        assertEquals("INTERNAL_ERROR server failure", json.getValue("message").jsonPrimitive.content)
        assertNotNull(json["context"])
    }

    private fun <T : Throwable> T.withFrames(count: Int): T = apply {
        stackTrace = Array(count) { safeFrame() }
    }

    private fun safeFrame(): StackTraceElement = StackTraceElement(
        Thread::class.java.name,
        "run",
        "Thread.java",
        25,
    )

    private fun assertContainsOnce(value: String, expected: String) {
        assertTrue(value.contains(expected))
        assertEquals(value.indexOf(expected), value.lastIndexOf(expected))
    }

    private fun FailureEvent.parsed() = Json.parseToJsonElement(json).jsonObject
    private fun kotlinx.serialization.json.JsonObject.message() = getValue("message").jsonPrimitive.content
    private fun kotlinx.serialization.json.JsonObject.truncation(name: String) =
        getValue("truncation").jsonObject.getValue(name).jsonPrimitive.boolean

    private fun causeChain(throwableCount: Int): Exception {
        var cause: Exception? = null
        repeat(throwableCount) { cause = RuntimeException(null, cause).withFrames(1) }
        return checkNotNull(cause)
    }

    private class HostileException(private val sentinel: String) : Exception() {
        override val message: String get() = error(sentinel)
        override fun getLocalizedMessage(): String = error(sentinel)
        override fun toString(): String = error(sentinel)
        override val cause: Throwable? get() = throw IllegalStateException(sentinel)
    }

    private class MutableCauseException : Exception() {
        var next: Throwable? = null
        override val cause: Throwable? get() = next
        init { stackTrace = arrayOf(safeStaticFrame()) }
    }

    private class ThrowingStackException : Exception() {
        override fun getStackTrace(): Array<StackTraceElement> = throw IllegalStateException("credential-sentinel")
    }

    private class LinkageStackException : Exception() {
        override fun getStackTrace(): Array<StackTraceElement> = throw NoClassDefFoundError("credential-sentinel")
    }

    private companion object {
        fun safeStaticFrame() = StackTraceElement(Thread::class.java.name, "run", "Thread.java", 25)
    }
}

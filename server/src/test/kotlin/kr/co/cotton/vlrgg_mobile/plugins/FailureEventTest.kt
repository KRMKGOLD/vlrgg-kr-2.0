package kr.co.cotton.vlrgg_mobile.plugins

import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
            UpstreamNetworkFailure(cause) to FailureSeverity.WARN,
            InternalServerFailure(cause) to FailureSeverity.ERROR,
            SourceParsingFailure(cause) to FailureSeverity.ERROR,
        )

        cases.forEach { (failure, severity) ->
            val event = formatter.format(failure)
            val json = event.parsed()
            assertEquals(severity, event.severity)
            assertEquals(severity.name, json.getValue("severity").jsonPrimitive.content)
            assertEquals(severity == FailureSeverity.ERROR, "@type" in json)
            assertEquals(severity == FailureSeverity.ERROR, json.message().contains("\tat "))
            assertFalse(event.json.contains("credential-sentinel"))
        }

        val canonicalUpstreams = cases.map { (failure, _) ->
            formatter.format(failure).parsed().getValue("canonical_upstream").jsonPrimitive.content
        }
        assertEquals(listOf("none", "https://www.vlr.gg/", "none", "https://www.vlr.gg/"), canonicalUpstreams)
    }

    @Test
    fun `stack names exception types and frames without any exception message`() {
        val root = IllegalArgumentException("credential-sentinel root").withFrames(1)
        val cause = IllegalStateException("credential-sentinel cause", root).withFrames(2)

        val event = FailureEventFormatter(environment).format(SourceParsingFailure(cause))

        assertEquals(
            listOf(
                "java.lang.IllegalStateException: SOURCE_PARSING_FAILURE",
                "\tat java.lang.Thread.run(Thread.java:25)",
                "\tat java.lang.Thread.run(Thread.java:25)",
                "Caused by: java.lang.IllegalArgumentException: SOURCE_PARSING_FAILURE",
                "\tat java.lang.Thread.run(Thread.java:25)",
            ),
            event.parsed().message().lines(),
        )
        assertFalse(event.json.contains("credential-sentinel"))
    }

    @Test
    fun `frames and causes are bounded and cyclic causes terminate`() {
        val formatter = FailureEventFormatter(environment)
        listOf(31, 32, 33).forEach { count ->
            val json = formatter.format(InternalServerFailure(RuntimeException().withFrames(count))).parsed()
            assertEquals(minOf(count, 32), json.message().lines().count { it.startsWith("\tat ") })
        }
        listOf(3, 4, 5).forEach { throwableCount ->
            val json = formatter.format(InternalServerFailure(causeChain(throwableCount))).parsed()
            assertEquals(minOf(throwableCount - 1, 3), json.message().lines().count { it.startsWith("Caused by: ") })
        }
        val deep = formatter.format(InternalServerFailure(causeChain(5, framesEach = 40))).parsed()
        assertEquals(32, deep.message().lines().count { it.startsWith("\tat ") })

        val first = MutableCauseException()
        val second = MutableCauseException()
        first.next = second
        second.next = first
        val cycle = formatter.format(InternalServerFailure(first)).parsed()
        assertEquals(3, cycle.message().lines().count { it.startsWith("Caused by: ") })
    }

    @Test
    fun `an error without frames reports a fixed location instead of a stack`() {
        val event = FailureEventFormatter(environment).format(
            InternalServerFailure(IllegalStateException("credential-sentinel").withFrames(0)),
        )
        val json = event.parsed()

        assertEquals("INTERNAL_ERROR server failure", json.message())
        assertNotNull(json["context"])
        assertFalse(event.json.contains("credential-sentinel"))
    }

    @Test
    fun `service context comes from the runtime environment with local defaults`() {
        val deployed = FailureEventFormatter(environment).format(InvalidInputFailure()).parsed()
        assertEquals("vlrgg-validation", deployed.serviceContext("service"))
        assertEquals("vlrgg-validation-00001", deployed.serviceContext("version"))

        val local = FailureEventFormatter(emptyMap()).format(InvalidInputFailure()).parsed()
        assertEquals("vlrgg-server-local", local.serviceContext("service"))
        assertEquals("local", local.serviceContext("version"))
    }

    @Test
    fun `trace is accepted only from one fully valid header`() {
        val formatter = FailureEventFormatter(environment)
        val traceId = "ABCDEF0123456789ABCDEF0123456789"
        val valid = formatter.format(InvalidInputFailure(), listOf("$traceId/18446744073709551615;o=1")).parsed()
        assertEquals(
            "projects/vlrgg-test-project/traces/${traceId.lowercase()}",
            valid.getValue("logging.googleapis.com/trace").jsonPrimitive.content,
        )
        assertFalse("spanId" in valid)
        assertFalse("trace_sampled" in valid)

        listOf(
            listOf(traceId, traceId),
            listOf("$traceId/18446744073709551616"),
            listOf("$traceId;o=2"),
            listOf("credential-sentinel"),
        ).forEach { headers ->
            assertFalse("logging.googleapis.com/trace" in formatter.format(InvalidInputFailure(), headers).parsed())
        }
        val withoutProject = FailureEventFormatter(environment - "GOOGLE_CLOUD_PROJECT")
        assertFalse("logging.googleapis.com/trace" in withoutProject.format(InvalidInputFailure(), listOf(traceId)).parsed())
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

    private fun <T : Throwable> T.withFrames(count: Int): T = apply {
        stackTrace = Array(count) { StackTraceElement(Thread::class.java.name, "run", "Thread.java", 25) }
    }

    private fun FailureEvent.parsed() = Json.parseToJsonElement(json).jsonObject
    private fun JsonObject.message() = getValue("message").jsonPrimitive.content
    private fun JsonObject.serviceContext(name: String) =
        getValue("serviceContext").jsonObject.getValue(name).jsonPrimitive.content

    private fun causeChain(throwableCount: Int, framesEach: Int = 1): Exception {
        var cause: Exception? = null
        repeat(throwableCount) { cause = RuntimeException(null, cause).withFrames(framesEach) }
        return checkNotNull(cause)
    }

    private inner class MutableCauseException : Exception() {
        var next: Throwable? = null
        override val cause: Throwable? get() = next
        init { withFrames(1) }
    }
}

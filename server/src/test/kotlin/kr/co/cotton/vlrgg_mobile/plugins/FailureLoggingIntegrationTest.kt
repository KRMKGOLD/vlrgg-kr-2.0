package kr.co.cotton.vlrgg_mobile.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode
import kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig
import kr.co.cotton.vlrgg_mobile.protection.configurePublicRequestProtection
import kr.co.cotton.vlrgg_mobile.protection.createPublicApiProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailureLoggingIntegrationTest {
    @Test
    fun `concurrent requests keep their own trace and exact failure counts`() = testApplication {
        val emitted = ConcurrentLinkedQueue<FailureEvent>()
        val observability = PublicApiObservability()
        application {
            configureSerialization()
            configureErrorHandling(
                observability = observability,
                failureDiagnostics = FailureDiagnostics(maxSamplesPerWindow = 10),
                failureEventFormatter = FailureEventFormatter(mapOf("GOOGLE_CLOUD_PROJECT" to "vlrgg-test-project")),
                failureEventSink = emitted::add,
            )
            configurePublicRequestProtection(
                createPublicApiProtection(PublicApiProtectionConfig(), observability),
                PublicApiProtectionConfig(),
            )
            routing {
                get("/fail/argument") { throw IllegalArgumentException("credential-sentinel") }
                get("/fail/state") { throw IllegalStateException("credential-sentinel") }
                get("/fail/unsupported") { throw UnsupportedOperationException("credential-sentinel") }
                get("/fail/number") { throw NumberFormatException("credential-sentinel") }
                get("/fail/no-trace") { throw EOFException("credential-sentinel") }
                get("/fail/invalid-trace") { throw IOException("credential-sentinel") }
            }
        }
        val traced = listOf(
            "argument" to IllegalArgumentException::class.java.name,
            "state" to IllegalStateException::class.java.name,
            "unsupported" to UnsupportedOperationException::class.java.name,
            "number" to NumberFormatException::class.java.name,
        ).mapIndexed { index, (path, type) -> Triple(path, type, index.toString(16).padStart(32, '0')) }
        coroutineScope {
            traced.map { (path, _, trace) ->
                async {
                    client.get("/fail/$path") { headers.append(CLOUD_TRACE_HEADER, "$trace/1;o=1") }
                }
            }.plus(async { client.get("/fail/no-trace") })
                .plus(async { client.get("/fail/invalid-trace") { headers.append(CLOUD_TRACE_HEADER, "invalid") } })
                .awaitAll()
        }.forEach { assertEquals(HttpStatusCode.InternalServerError, it.status) }

        assertEquals(6, emitted.size)
        val byType = emitted.associate { event ->
            val json = Json.parseToJsonElement(event.json).jsonObject
            json.getValue("message").jsonPrimitive.content.substringBefore(':') to json
        }
        traced.forEach { (_, type, trace) ->
            assertEquals(trace, byType.getValue(type).getValue("logging.googleapis.com/trace").jsonPrimitive.content.substringAfterLast('/'))
        }
        assertFalse("logging.googleapis.com/trace" in byType.getValue(EOFException::class.java.name))
        assertFalse("logging.googleapis.com/trace" in byType.getValue(IOException::class.java.name))
        val snapshot = observability.snapshot()
        assertEquals(6, snapshot.diagnosticEmitted.getValue(FailureCategory.INTERNAL))
        assertEquals(0, snapshot.diagnosticSuppressed.getValue(FailureCategory.INTERNAL))
        assertEquals(0, snapshot.telemetryFailures)
        assertEquals(6, snapshot.requests)
        assertEquals(6, snapshot.statusClasses[5])
        assertEquals(6, snapshot.rejections.getValue(ApiErrorCode.INTERNAL_ERROR))
    }

    @Test
    fun `one hundred failures emit four and suppress ninety six`() = testApplication {
        val emitted = ConcurrentLinkedQueue<FailureEvent>()
        val observability = PublicApiObservability()
        application {
            configureSerialization()
            configureErrorHandling(observability = observability, failureEventSink = emitted::add)
            routing { get("/flood") { error("credential-sentinel") } }
        }
        coroutineScope { List(100) { async { client.get("/flood") } }.awaitAll() }
            .forEach { assertEquals(HttpStatusCode.InternalServerError, it.status) }

        assertEquals(4, emitted.size)
        assertEquals(4, observability.snapshot().diagnosticEmitted.getValue(FailureCategory.INTERNAL))
        assertEquals(96, observability.snapshot().diagnosticSuppressed.getValue(FailureCategory.INTERNAL))
    }

    @Test
    fun `suppressed requests never inspect throwable stack`() = testApplication {
        val reads = AtomicInteger()
        val diagnostics = FailureDiagnostics(maxSamplesPerWindow = 1)
        application {
            configureSerialization()
            configureErrorHandling(failureDiagnostics = diagnostics, failureEventSink = {})
            routing {
                get("/first") { error("fills budget") }
                get("/suppressed") { throw CountingStackException(reads) }
            }
        }
        assertEquals(HttpStatusCode.InternalServerError, client.get("/first").status)
        assertEquals(HttpStatusCode.InternalServerError, client.get("/suppressed").status)
        assertEquals(0, reads.get())
    }

    @Test
    fun `default diagnostic budget is isolated between application instances`() {
        fun emittedByApplication(): Int {
            var emitted = 0
            testApplication {
                application {
                    configureSerialization()
                    configureErrorHandling(failureEventSink = { emitted += 1 })
                    routing { get("/fail") { error("fixture") } }
                }
                repeat(5) { assertEquals(HttpStatusCode.InternalServerError, client.get("/fail").status) }
            }
            return emitted
        }
        assertEquals(4, emittedByApplication())
        assertEquals(4, emittedByApplication())
    }

    @Test
    fun `throwing telemetry sinks do not change public errors`() = testApplication {
        val observability = PublicApiObservability()
        application {
            configureSerialization()
            configureErrorHandling(observability = observability, failureEventSink = { error("sink failure") })
            configurePublicRequestProtection(
                createPublicApiProtection(PublicApiProtectionConfig(), observability),
                PublicApiProtectionConfig(),
            )
            routing {
                get("/fail") { error("credential-sentinel") }
                get("/ok") { call.respondText("ok") }
            }
        }

        assertEquals(HttpStatusCode.InternalServerError, client.get("/fail").status)
        assertEquals(HttpStatusCode.OK, client.get("/ok").status)
        val snapshot = observability.snapshot()
        assertEquals(1, snapshot.telemetryFailures)
        assertEquals(0, snapshot.diagnosticEmitted.getValue(FailureCategory.INTERNAL))
        assertEquals(2, snapshot.requests)
        assertEquals(1, snapshot.statusClasses[2])
        assertEquals(1, snapshot.statusClasses[5])
        assertEquals(1, snapshot.rejections.getValue(ApiErrorCode.INTERNAL_ERROR))
    }

    @Test
    fun `summary sink failures are counted without changing request accounting`() {
        val observability = PublicApiObservability(emit = { error("summary sink failure") }) { 0L }
        observability.requestStarted(PublicRouteClass.API)
        observability.completed(200, 1)

        val snapshot = observability.snapshot()
        assertEquals(1, snapshot.requests)
        assertEquals(1, snapshot.statusClasses[2])
        assertEquals(1, snapshot.telemetryFailures)
        assertTrue(formatPublicApiSummary(snapshot).endsWith("telemetry_failures=1"))
    }

    private class CountingStackException(private val reads: AtomicInteger) : Exception() {
        override fun getStackTrace(): Array<StackTraceElement> {
            reads.incrementAndGet()
            return super.getStackTrace()
        }
    }
}

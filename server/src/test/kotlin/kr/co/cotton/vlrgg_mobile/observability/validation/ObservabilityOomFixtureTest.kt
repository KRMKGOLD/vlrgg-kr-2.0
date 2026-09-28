package kr.co.cotton.vlrgg_mobile.observability.validation

import java.io.IOException
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObservabilityOomFixtureTest {
    @Test
    fun `OOM route requires the private cloud service and dedicated opt-in`() {
        val enabled = mapOf(
            "K_SERVICE" to "vlrgg-query-check",
            "VLRGG_OBSERVABILITY_VALIDATION" to "true",
            "VLRGG_OBSERVABILITY_ALLOW_OOM" to "true",
        )

        assertTrue(oomFixtureEnabled(enabled))
        assertFalse(oomFixtureEnabled(enabled + ("K_SERVICE" to "vlrgg-query")))
        assertFalse(oomFixtureEnabled(enabled - "VLRGG_OBSERVABILITY_VALIDATION"))
        assertFalse(oomFixtureEnabled(enabled - "VLRGG_OBSERVABILITY_ALLOW_OOM"))
        assertFalse(oomFixtureEnabled(enabled - "K_SERVICE" + ("VLRGG_OBSERVABILITY_LOCAL" to "true")))
        assertFalse(oomFixtureEnabled(enabled + ("VLRGG_OBSERVABILITY_LOCAL" to "true")))
        assertFalse(oomFixtureEnabled(enabled + ("VLRGG_OBSERVABILITY_ALLOW_EXIT" to "true")))
    }

    @Test
    fun `OOM writer checks time before every bounded write`() {
        val output = CountingOutputStream()
        val times = ArrayDeque(listOf(0L, 0L, OOM_WRITE_BUDGET_NANOS))

        assertEquals(OomFixtureFailure.TIME_LIMIT, writeForContainerOom(output) { times.removeFirst() })
        assertEquals(1, output.writes)
        assertEquals(OOM_BUFFER_BYTES.toLong(), output.bytes)
    }

    @Test
    fun `OOM writer stops at the fixed byte cap or IO failure`() {
        val output = CountingOutputStream()
        assertEquals(OomFixtureFailure.BYTE_LIMIT, writeForContainerOom(output) { 0L })
        assertEquals(OOM_MAX_WRITES, output.writes)
        assertEquals(OOM_BUFFER_BYTES.toLong() * OOM_MAX_WRITES, output.bytes)

        assertEquals(
            OomFixtureFailure.IO_ERROR,
            writeForContainerOom(object : OutputStream() {
                override fun write(value: Int) = throw IOException("fixture")
                override fun write(bytes: ByteArray, offset: Int, length: Int) = throw IOException("fixture")
            }) { 0L },
        )
    }

    private class CountingOutputStream : OutputStream() {
        var writes = 0
        var bytes = 0L

        override fun write(value: Int) = error("single-byte writes are forbidden")

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            writes++
            this.bytes += length
        }
    }
}

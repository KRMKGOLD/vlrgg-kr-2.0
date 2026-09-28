package kr.co.cotton.vlrgg_mobile.observability.validation

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    fun `OOM allocator checks time before every fixed allocation`() {
        var now = 0L
        val requested = mutableListOf<Int>()
        val result = runContainerOomFixture(
            allocator = {
                requested += it
                now = 30_000_000_000L
                ByteBuffer.allocate(1)
            },
            toucher = { _, _ -> },
            nanoTime = { now },
        )

        assertEquals(OomFixtureFailure.TIME_LIMIT, result.reason)
        assertEquals(listOf(OOM_DIRECT_BUFFER_BYTES), requested)
        assertEquals(OOM_DIRECT_BUFFER_BYTES.toLong(), result.allocatedBytes)
        assertEquals(OOM_MAX_ELAPSED_MILLIS, result.elapsedMillis)
    }

    @Test
    fun `OOM allocator retains the fixed count and reports the byte cap`() {
        val allocated = mutableListOf<ByteBuffer>()
        val touched = mutableListOf<Pair<ByteBuffer, Int>>()
        val result = runContainerOomFixture(
            allocator = {
                assertEquals(OOM_DIRECT_BUFFER_BYTES, it)
                ByteBuffer.allocate(1).also(allocated::add)
            },
            toucher = { buffer, index -> touched += buffer to index },
            nanoTime = { 0L },
        )

        assertEquals(OomFixtureFailure.BYTE_LIMIT, result.reason)
        assertEquals(OOM_MAX_BUFFERS, allocated.size)
        assertEquals(allocated, touched.map { it.first })
        assertEquals((0 until OOM_MAX_BUFFERS).toList(), touched.map { it.second })
        assertEquals(OOM_DIRECT_BUFFER_BYTES.toLong() * OOM_MAX_BUFFERS, result.allocatedBytes)
        assertEquals(0L, result.elapsedMillis)
    }

    @Test
    fun `OOM allocator reports catchable allocation error with saturated elapsed time`() {
        var now = 0L
        val result = runContainerOomFixture(
            allocator = {
                now = 30_000_000_000L
                throw OutOfMemoryError("must not escape")
            },
            toucher = { _, _ -> error("unreachable") },
            nanoTime = { now },
        )

        assertEquals(OomFixtureFailure.ALLOCATION_ERROR, result.reason)
        assertEquals(0L, result.allocatedBytes)
        assertEquals(OOM_MAX_ELAPSED_MILLIS, result.elapsedMillis)
    }

    @Test
    fun `direct buffer toucher writes a distinct nonzero token to every page`() {
        val buffer = ByteBuffer.allocate(OOM_PAGE_BYTES * 2)
        val otherBlock = ByteBuffer.allocate(OOM_PAGE_BYTES)

        touchDirectBuffer(buffer, 1)
        touchDirectBuffer(otherBlock, 2)

        val first = buffer.getLong(0)
        val second = buffer.getLong(OOM_PAGE_BYTES)
        assertNotEquals(0L, first)
        assertNotEquals(0L, second)
        assertNotEquals(first, second)
        assertEquals((2L shl 32) or 1L, first)
        assertEquals((2L shl 32) or 2L, second)
        assertEquals((3L shl 32) or 1L, otherBlock.getLong(0))
        assertNotEquals(first, otherBlock.getLong(0))
    }

    @Test
    fun `survival diagnostics have the exact allowlisted JSON schema`() {
        assertEquals(
            "{\"status\":\"fixture_failed\",\"reason\":\"byte_limit\",\"allocatedBytes\":1073741824,\"elapsedMillis\":25000}",
            OomFixtureResult(OomFixtureFailure.BYTE_LIMIT, 1_073_741_824L, 25_000L).toJson(),
        )
        assertEquals(
            "{\"status\":\"fixture_failed\",\"reason\":\"time_limit\",\"allocatedBytes\":16777216,\"elapsedMillis\":10000}",
            OomFixtureResult(OomFixtureFailure.TIME_LIMIT, 16_777_216L, 10_000L).toJson(),
        )
        assertEquals(
            "{\"status\":\"fixture_failed\",\"reason\":\"allocation_error\",\"allocatedBytes\":0,\"elapsedMillis\":0}",
            OomFixtureResult(OomFixtureFailure.ALLOCATION_ERROR, 0L, 0L).toJson(),
        )
    }

}

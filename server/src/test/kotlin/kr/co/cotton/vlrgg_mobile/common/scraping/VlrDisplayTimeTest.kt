package kr.co.cotton.vlrgg_mobile.common.scraping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlrDisplayTimeTest {

    @Test
    fun `display date keeps real calendar dates and trims surrounding whitespace`() {
        assertEquals("2026-10-27", "Tue, October 27, 2026".toVlrDisplayDateOrNull())
        assertEquals("2028-02-29", " Tue, February 29, 2028 ".toVlrDisplayDateOrNull())
    }

    @Test
    fun `display date rejects dates that do not exist or disagree with the weekday`() {
        listOf(
            "Fri, February 30, 2026",
            "Sun, February 29, 2026",
            "Fri, April 31, 2026",
            "Tue, October 28, 2026",
            "Today",
            "",
        ).forEach { label -> assertNull(label.toVlrDisplayDateOrNull(), label) }
    }

    @Test
    fun `display time keeps valid clock labels`() {
        assertEquals("03:00", "3:00 AM".toVlrDisplayTimeOrNull())
        assertEquals("19:05", "7:05 pm".toVlrDisplayTimeOrNull())
        assertEquals("00:00", "12:00 AM".toVlrDisplayTimeOrNull())
        assertEquals("12:30", "12:30 PM".toVlrDisplayTimeOrNull())
    }

    @Test
    fun `display time rejects clock values that do not exist`() {
        listOf("13:00 PM", "0:30 AM", "12:60 PM", "3:00", "TBD", "").forEach { label ->
            assertNull(label.toVlrDisplayTimeOrNull(), label)
        }
    }
}

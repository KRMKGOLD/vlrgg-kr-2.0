package kr.co.cotton.vlrgg_mobile.ui.feature.search

import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchResultRowLabelTest {
    @Test
    fun metaLineStartsWithTheTypeLabelThenTheMetadata() {
        assertEquals("팀 · 대한민국", TeamSearchResult("1", "DRX", "대한민국").metaLine)
        assertEquals("선수 · KIWOOM DRX", PlayerSearchResult("2", "MaKo", "KIWOOM DRX").metaLine)
        assertEquals("대회 · Jun 15 – Jul 21", EventSearchResult("3", "VCT Pacific", "Jun 15 – Jul 21").metaLine)
        assertEquals("시리즈 · Pacific · 2026", SeriesSearchResult("4", "Champions Tour", "Pacific · 2026").metaLine)
    }

    @Test
    fun missingOrBlankMetadataLeavesOnlyTheTypeLabel() {
        assertEquals("팀", TeamSearchResult("1", "DRX", null).metaLine)
        assertEquals("선수", PlayerSearchResult("2", "MaKo", "  ").metaLine)
        assertEquals("대회", EventSearchResult("3", "VCT Pacific", "").metaLine)
        assertEquals("시리즈", SeriesSearchResult("4", "Champions Tour", null).metaLine)
    }

    @Test
    fun spokenLabelReadsTypeTitleThenMetadata() {
        assertEquals("팀, DRX, 대한민국", TeamSearchResult("1", "DRX", " 대한민국 ").spokenLabel)
        assertEquals("선수, MaKo", PlayerSearchResult("2", "MaKo", null).spokenLabel)
    }
}

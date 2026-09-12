package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kr.co.cotton.vlrgg_mobile.ui.component.StatusChipStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerRecentMatchCardTest {

    @Test
    fun outcomesExposeWrittenLabelsAndDistinctLossStyle() {
        assertEquals(StatusChipStatus.Completed, PlayerRecentMatchOutcome.WIN.chipStatus())
        assertEquals("승리", PlayerRecentMatchOutcome.WIN.displayLabel())
        assertEquals(StatusChipStatus.Loss, PlayerRecentMatchOutcome.LOSS.chipStatus())
        assertEquals("패배", PlayerRecentMatchOutcome.LOSS.displayLabel())
        assertEquals(StatusChipStatus.Partial, PlayerRecentMatchOutcome.UNKNOWN.chipStatus())
        assertEquals("결과 미정", PlayerRecentMatchOutcome.UNKNOWN.displayLabel())
    }

    @Test
    fun cardAndScoreTagsRetainTheExactMatchId() {
        assertEquals("player-match-708427", playerRecentMatchCardTag("708427"))
        assertEquals("player-match-score-708427", playerMatchScoreTag("708427"))
    }
}

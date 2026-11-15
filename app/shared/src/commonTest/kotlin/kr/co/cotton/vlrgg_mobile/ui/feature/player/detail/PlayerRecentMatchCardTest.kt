package kr.co.cotton.vlrgg_mobile.ui.feature.player.detail

import kr.co.cotton.vlrgg_mobile.domain.model.player.PlayerRecentMatchOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kr.co.cotton.vlrgg_mobile.ui.component.TeamMatchOutcome

class PlayerRecentMatchCardTest {

    @Test
    fun outcomesExposeWrittenLabelsInTheResultColumn() {
        assertEquals(TeamMatchOutcome.Win, PlayerRecentMatchOutcome.WIN.rowOutcome())
        assertEquals("승리", PlayerRecentMatchOutcome.WIN.rowOutcome().label)
        assertEquals(TeamMatchOutcome.Loss, PlayerRecentMatchOutcome.LOSS.rowOutcome())
        assertEquals("패배", PlayerRecentMatchOutcome.LOSS.rowOutcome().label)
        assertEquals(TeamMatchOutcome.Unknown, PlayerRecentMatchOutcome.UNKNOWN.rowOutcome())
        assertEquals("결과 미정", PlayerRecentMatchOutcome.UNKNOWN.rowOutcome().label)
    }

    @Test
    fun cardAndScoreTagsRetainTheExactMatchId() {
        assertEquals("player-match-708427", playerRecentMatchCardTag("708427"))
        assertEquals("player-match-score-708427", playerMatchScoreTag("708427"))
    }
}

package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import kotlin.test.Test
import kotlin.test.assertEquals

class KoreanLineBreakAndroidHostTest {
    @Test
    fun koreanWordBreakUsesPhraseWordBreakWithKoreanLocaleOnAndroid() {
        val style = TextStyle().withKoreanWordBreak()

        assertEquals(LineBreak.WordBreak.Phrase, style.lineBreak.wordBreak)
        assertEquals(LineBreak.Strategy.HighQuality, style.lineBreak.strategy)
        assertEquals(LocaleList("ko"), style.localeList)
    }
}

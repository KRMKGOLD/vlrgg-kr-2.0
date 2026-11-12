package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak

/**
 * 문단 줄바꿈에 어절(phrase) 단위 단어 나눔을 더한 설정(`DESIGN.md` 5절).
 * commonMain의 [LineBreak]에는 단어 나눔 옵션이 없어 플랫폼마다 정한다.
 */
internal expect val PhraseLineBreak: LineBreak

/**
 * 한글 문장을 어절 단위로 줄바꿈한다. Android는 13(API 33)부터 적용되고 text locale이 `ko`여야 하므로
 * locale도 함께 정한다. iOS(skiko)는 [LineBreak]를 쓰지 않아 기존 줄바꿈 그대로다.
 */
internal fun TextStyle.withKoreanWordBreak(): TextStyle =
    copy(lineBreak = PhraseLineBreak, localeList = LocaleList("ko"))

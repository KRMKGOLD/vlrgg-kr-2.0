package kr.co.cotton.vlrgg_mobile.feature.player

import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.POSITIVE_DECIMAL_ID_REGEX

/** A VLR.GG player ID accepted only in its canonical positive-decimal form. */
@JvmInline
internal value class PlayerId private constructor(
    val value: String,
) {
    companion object {
        fun fromPath(rawValue: String?): PlayerId = rawValue
            ?.takeIf(PLAYER_ID_PATTERN::matches)
            ?.let(::PlayerId)
            ?: throw InvalidInputFailure()
    }
}

private val PLAYER_ID_PATTERN = Regex(POSITIVE_DECIMAL_ID_REGEX)

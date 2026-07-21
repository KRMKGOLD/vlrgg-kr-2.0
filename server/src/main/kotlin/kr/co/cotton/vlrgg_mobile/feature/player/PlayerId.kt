package kr.co.cotton.vlrgg_mobile.feature.player

import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

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

private val PLAYER_ID_PATTERN = Regex("[1-9][0-9]{0,9}")

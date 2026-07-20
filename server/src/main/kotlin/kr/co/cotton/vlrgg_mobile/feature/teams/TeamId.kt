package kr.co.cotton.vlrgg_mobile.feature.teams

import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure

/** A VLR.GG team ID accepted only in its canonical positive-decimal form. */
@JvmInline
internal value class TeamId private constructor(
    val value: String,
) {
    companion object {
        fun fromPath(rawValue: String?): TeamId = rawValue
            ?.takeIf(TEAM_ID_PATTERN::matches)
            ?.let(::TeamId)
            ?: throw InvalidInputFailure()
    }
}

private val TEAM_ID_PATTERN = Regex("[1-9][0-9]{0,9}")

package kr.co.cotton.vlrgg_mobile.feature.series

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.http.POSITIVE_DECIMAL_ID_REGEX

private val SERIES_ID_PATTERN = Regex(POSITIVE_DECIMAL_ID_REGEX)

/** A canonical VLR series identifier shared directly with Search result references. */
@JvmInline
internal value class SeriesId private constructor(
    val value: String,
) {
    companion object {
        fun fromPath(rawValue: String?): SeriesId = rawValue
            ?.takeIf(SERIES_ID_PATTERN::matches)
            ?.let(::SeriesId)
            ?: throw InvalidInputFailure()
    }
}

/** Raw feature-local material fetched before the parser crosses the HTML boundary. */
internal data class SeriesHtmlPage(
    val upstreamUrl: Url,
    val html: String,
)

/** Server-internal VLR series representation. It is never serialized directly. */
internal data class SeriesSource(
    val id: String,
    val name: String,
    val description: String?,
    val events: List<SeriesEventSource>,
)

internal data class SeriesEventSource(
    val id: String,
    val name: String,
    val status: SeriesEventStatusSource,
    val dateLabel: String?,
    val regionCode: String?,
    val imageUrl: String?,
)

internal enum class SeriesEventStatusSource {
    ONGOING,
    UPCOMING,
    COMPLETED,
    PAUSED,
}

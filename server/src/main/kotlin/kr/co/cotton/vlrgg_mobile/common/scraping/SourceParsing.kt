package kr.co.cotton.vlrgg_mobile.common.scraping

import java.net.URI
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.nodes.Element

internal val WHITESPACE = Regex("\\s+")

/** Fails closed: every non-cancellation parser exception becomes a [SourceParsingFailure]. */
internal inline fun <T> parseSource(block: () -> T): T = try {
    block()
} catch (failure: SourceParsingFailure) {
    throw failure
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    throw SourceParsingFailure(failure)
}

internal fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

internal fun String.normalizedText(): String = replace(WHITESPACE, " ").trim()

internal fun String.normalizedTextOrNull(): String? = normalizedText().ifEmpty { null }

internal fun Element.normalizedText(): String = text().normalizedText()

internal fun Element.normalizedTextOrNull(): String? = text().normalizedTextOrNull()

internal fun Element.ownNormalizedText(): String = ownText().normalizedText()

internal fun Element.ownNormalizedTextOrNull(): String? = ownText().normalizedTextOrNull()

internal fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun Element.requiredText(selector: String, message: String): String =
    selectFirst(selector)?.normalizedTextOrNull() ?: sourceStructureError(message)

// Public image URLs are HTTPS-only: normalize protocol/root-relative sources and require a valid host.
internal fun String?.toPublicImageUrl(): String? = this?.trim()?.takeIf { it.isNotEmpty() }?.let { source ->
    val normalized = when {
        source.startsWith("//") -> "https:$source"
        source.startsWith("https://", ignoreCase = true) ->
            "https://${source.substringAfter("://")}"
        source.startsWith("/") -> "https://www.vlr.gg$source"
        else -> null
    }
    normalized?.takeIf { url ->
        val uri = runCatching { URI(url) }.getOrNull() ?: return@takeIf false
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    }
}

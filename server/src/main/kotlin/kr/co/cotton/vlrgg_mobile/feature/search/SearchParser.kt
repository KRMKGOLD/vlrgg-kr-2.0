package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val SEARCH_RESULT_PATH = Regex(
    "^/search/r/(eventgroup|event|team|player)/([1-9][0-9]{0,9})/idx/?(?:[?#].*)?$",
)
private val SUPPORTED_SEARCH_RESULT_PREFIX = Regex("^/search/r/(eventgroup|event|team|player)/")
private val FOUND_RESULTS_PATTERN = Regex("\\bFound\\s+([0-9]+)\\s+results?\\b", RegexOption.IGNORE_CASE)
private const val CANONICAL_SEARCH_RESULT_PREFIX = "/search/r/"

/** The only search component that knows the VLR.GG DOM shape and CSS class names. */
internal class SearchParser {
    fun parse(html: String, upstreamUrl: Url): SearchSourceModel = try {
        val resultsContainer = Jsoup.parse(html)
            .selectFirst("#wrapper .col.mod-1")
            ?: throw IllegalStateException("Search results container is missing.")

        val canonicalResultElements = resultsContainer.select("a[href^='$CANONICAL_SEARCH_RESULT_PREFIX']")
        val supportedResultElements = canonicalResultElements.filter { result ->
            SUPPORTED_SEARCH_RESULT_PREFIX.containsMatchIn(result.attr("href"))
        }
        val resultElements = resultsContainer.select(".wf-card a.search-item[href]")
        val foundResultCount = FOUND_RESULTS_PATTERN.find(resultsContainer.text())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

        if (
            supportedResultElements.any { it !in resultElements } ||
            foundResultCount != null && foundResultCount != canonicalResultElements.size
        ) {
            throw IllegalStateException("Search result structure is inconsistent.")
        }

        val results = resultElements
            .asSequence()
            .mapNotNull(::parseResult)
            .distinctBy { result -> result.type to result.id }
            .toList()
        if (results.isEmpty() && supportedResultElements.isNotEmpty()) {
            throw IllegalStateException("All supported search results are malformed.")
        }

        SearchSourceModel(results)
    } catch (exception: Exception) {
        throw SourceParsingFailure(upstreamUrl, exception)
    }

    private fun parseResult(card: Element): SearchSourceResult? {
        val match = SEARCH_RESULT_PATH.matchEntire(card.attr("href")) ?: return null
        val type = match.groups[1]?.value?.toSourceType() ?: return null
        val id = match.groups[2]?.value ?: return null
        val name = card.selectFirst(".search-item-title")?.normalizedText().orEmpty()
        if (name.isEmpty()) return null

        val descriptionElement = card.selectFirst(".search-item-desc")
        return SearchSourceResult(
            type = type,
            id = id,
            name = name,
            description = when (type) {
                SearchSourceResultType.EVENT -> descriptionElement?.eventPeriod()
                else -> descriptionElement?.normalizedText()?.ifEmpty { null }
            },
        )
    }

    private fun String.toSourceType(): SearchSourceResultType? = when (this) {
        "eventgroup" -> SearchSourceResultType.SERIES
        "event" -> SearchSourceResultType.EVENT
        "team" -> SearchSourceResultType.TEAM
        "player" -> SearchSourceResultType.PLAYER
        else -> null
    }

    private fun Element.normalizedText(): String = text().replace(Regex("\\s+"), " ").trim()

    private fun Element.eventPeriod(): String? = ownText()
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd { character ->
            character.isWhitespace() || character == '·' || character == '•' || character == '⋅'
        }
        .ifEmpty { null }
}

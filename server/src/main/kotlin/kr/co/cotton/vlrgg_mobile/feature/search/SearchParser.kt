package kr.co.cotton.vlrgg_mobile.feature.search

import io.ktor.http.*
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val SEARCH_RESULT_PATH = Regex(
    "^/search/r/(series|event|team|player)/([1-9][0-9]{0,9})/idx/?(?:[?#].*)?$",
)
private val SUPPORTED_SEARCH_RESULT_PREFIX = Regex("^/search/r/(series|event|team|player)/")

/** The only search component that knows the VLR.GG DOM shape and CSS class names. */
internal class SearchParser {
    fun parse(html: String, upstreamUrl: Url): SearchSourceModel = try {
        val resultsContainer = Jsoup.parse(html)
            .selectFirst("#wrapper .col.mod-1")
            ?: throw IllegalStateException("Search results container is missing.")

        val resultElements = resultsContainer.children()
            .asSequence()
            .filter { it.hasClass("wf-card") }
            .flatMap { card -> card.select("a.search-item").asSequence() }
            .toList()
        val results = resultElements
            .asSequence()
            .mapNotNull(::parseResult)
            .distinctBy { result -> result.type to result.id }
            .toList()
        if (results.isEmpty() && resultElements.any { SUPPORTED_SEARCH_RESULT_PREFIX.containsMatchIn(it.attr("href")) }) {
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

        return SearchSourceResult(
            type = type,
            id = id,
            name = name,
            description = card.selectFirst(".search-item-desc")?.normalizedText()?.ifEmpty { null },
        )
    }

    private fun String.toSourceType(): SearchSourceResultType? = when (this) {
        "series" -> SearchSourceResultType.SERIES
        "event" -> SearchSourceResultType.EVENT
        "team" -> SearchSourceResultType.TEAM
        "player" -> SearchSourceResultType.PLAYER
        else -> null
    }

    private fun Element.normalizedText(): String = text().replace(Regex("\\s+"), " ").trim()
}

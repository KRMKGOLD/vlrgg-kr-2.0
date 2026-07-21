package kr.co.cotton.vlrgg_mobile.feature.series

import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Owns all Jsoup traversal and VLR.GG markup assumptions for the Series feature. */
internal class SeriesParser {
    fun parse(page: SeriesHtmlPage, seriesId: SeriesId): SeriesSource = parseSafely(page) { document ->
        val header = document.selectFirst(SERIES_HEADER_SELECTOR)
            ?: sourceStructureError("Series header is missing.")
        val container = document.selectFirst(SERIES_EVENTS_CONTAINER_SELECTOR)
            ?: sourceStructureError("Series event container is missing.")
        val sections = container.children()
        if (sections.any { !it.hasClass(SERIES_SECTION_CLASS) }) {
            sourceStructureError("Series event container contains an unexpected child.")
        }
        if (sections.isEmpty()) sourceStructureError("Series event sections are missing.")

        SeriesSource(
            id = seriesId.value,
            name = header.requiredText(SERIES_NAME_SELECTOR, "Series name is missing."),
            description = header.selectFirst(SERIES_DESCRIPTION_SELECTOR)?.normalizedTextOrNull(),
            events = sections.flatMap(::parseSection).deduplicateByStableId(),
        )
    }

    private fun parseSection(section: Element): List<SeriesEventSource> {
        val label = section.children().firstOrNull { it.hasClass(SERIES_SECTION_LABEL_CLASS) }
            ?: sourceStructureError("Series event section label is missing.")
        if (!label.hasRecognizedSectionStatus()) {
            sourceStructureError("Series event section is unsupported.")
        }

        val cards = section.select(SERIES_EVENT_CARD_SELECTOR).distinct()
        val eventLinks = section.select(SERIES_EVENT_LINK_SELECTOR).distinct()
        if (cards.size != eventLinks.size) {
            sourceStructureError("Series event cards do not match source links.")
        }
        return cards.map(::parseEvent)
    }

    private fun parseEvent(card: Element): SeriesEventSource = SeriesEventSource(
        id = card.attr("href").matchedId(EVENT_PATH_PATTERN)
            ?: sourceStructureError("Series event identifier is missing."),
        name = card.requiredText(SERIES_EVENT_NAME_SELECTOR, "Series event name is missing."),
        status = card.selectFirst(SERIES_EVENT_STATUS_SELECTOR)?.toSeriesEventStatus()
            ?: sourceStructureError("Series event status is missing."),
        dateLabel = card.selectFirst(SERIES_EVENT_DATE_SELECTOR)?.ownNormalizedTextOrNull(),
        regionCode = card.selectFirst(SERIES_EVENT_REGION_FLAG_SELECTOR)?.regionCode(),
        imageUrl = card.selectFirst(SERIES_EVENT_IMAGE_SELECTOR)?.attr("src")?.toPublicImageUrl(),
    )

    private fun Element.hasRecognizedSectionStatus(): Boolean = classNames().any {
        it == UPCOMING_SECTION_CLASS || it == COMPLETED_SECTION_CLASS
    }

    private fun Element.toSeriesEventStatus(): SeriesEventStatusSource {
        val values = buildList {
            attr("data-event-status").normalizedStringOrNull()?.let(::add)
            classNames()
                .filter { it.startsWith(STATUS_MODIFIER_PREFIX) }
                .map { it.removePrefix(STATUS_MODIFIER_PREFIX) }
                .filter { it.isStatusToken() }
                .forEach(::add)
            normalizedTextOrNull()?.let(::add)
        }
        val statuses = values.map { value -> value.toSeriesEventStatusOrNull() ?: sourceStructureError("Series event status is unsupported.") }
            .distinct()
        return statuses.singleOrNull() ?: sourceStructureError("Series event status is contradictory.")
    }

    private fun String.isStatusToken(): Boolean = normalizedStatusToken() in KNOWN_STATUS_TOKENS

    private fun String.toSeriesEventStatusOrNull(): SeriesEventStatusSource? = when (normalizedStatusToken()) {
        "ongoing", "live" -> SeriesEventStatusSource.ONGOING
        "upcoming", "scheduled" -> SeriesEventStatusSource.UPCOMING
        "completed", "complete", "finished" -> SeriesEventStatusSource.COMPLETED
        "paused", "suspended" -> SeriesEventStatusSource.PAUSED
        else -> null
    }

    private fun String.normalizedStatusToken(): String = lowercase().replace(Regex("[^a-z]"), "")

    private fun List<SeriesEventSource>.deduplicateByStableId(): List<SeriesEventSource> {
        val eventsById = linkedMapOf<String, SeriesEventSource>()
        forEach { event ->
            val previous = eventsById[event.id]
            when {
                previous == null -> eventsById[event.id] = event
                previous.status != event.status -> sourceStructureError("Duplicate Series event statuses conflict.")
            }
        }
        return eventsById.values.toList()
    }

    private fun Element.requiredText(selector: String, message: String): String =
        selectFirst(selector)?.normalizedTextOrNull() ?: sourceStructureError(message)

    private fun Element.normalizedTextOrNull(): String? = text().normalizedStringOrNull()

    private fun Element.ownNormalizedTextOrNull(): String? = ownText().normalizedStringOrNull()

    private fun String.normalizedStringOrNull(): String? = replace(WHITESPACE, " ").trim().ifEmpty { null }

    private fun String.matchedId(pattern: Regex): String? = pattern.matchEntire(trim())?.groupValues?.getOrNull(1)

    private fun Element.regionCode(): String? = classNames()
        .firstOrNull { it.startsWith(STATUS_MODIFIER_PREFIX) && it.length > STATUS_MODIFIER_PREFIX.length }
        ?.removePrefix(STATUS_MODIFIER_PREFIX)
        ?.lowercase()
        ?.normalizedStringOrNull()

    private fun String.toPublicImageUrl(): String? = trim().takeIf { it.isNotEmpty() }?.let { source ->
        when {
            source.startsWith("//") -> "https:$source"
            source.startsWith("https://") -> source
            source.startsWith("/") -> "https://www.vlr.gg$source"
            else -> null
        }
    }

    private inline fun <T> parseSafely(page: SeriesHtmlPage, block: (Document) -> T): T = try {
        block(Jsoup.parse(page.html, VLR_PRIMARY_ORIGIN))
    } catch (failure: SourceParsingFailure) {
        throw failure
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        throw SourceParsingFailure(page.upstreamUrl, failure)
    }

    private fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

    private companion object {
        const val VLR_PRIMARY_ORIGIN = "https://www.vlr.gg/"
        const val SERIES_HEADER_SELECTOR = "#wf-container .event-header"
        const val SERIES_NAME_SELECTOR = ".wf-title"
        const val SERIES_DESCRIPTION_SELECTOR = ".event-header-main-desc, .series-description"
        const val SERIES_EVENTS_CONTAINER_SELECTOR = "#wf-container .events-container"
        const val SERIES_SECTION_CLASS = "events-container-col"
        const val SERIES_SECTION_LABEL_CLASS = "wf-label"
        const val UPCOMING_SECTION_CLASS = "mod-upcoming"
        const val COMPLETED_SECTION_CLASS = "mod-completed"
        const val SERIES_EVENT_CARD_SELECTOR = "a.event-item"
        const val SERIES_EVENT_LINK_SELECTOR = "a[href^='/event/']"
        const val SERIES_EVENT_NAME_SELECTOR = ".event-item-title"
        const val SERIES_EVENT_STATUS_SELECTOR = ".event-item-desc-item-status, [data-event-status]"
        const val SERIES_EVENT_DATE_SELECTOR = ".event-item-desc-item.mod-dates"
        const val SERIES_EVENT_REGION_FLAG_SELECTOR = ".event-item-desc-item.mod-location .flag"
        const val SERIES_EVENT_IMAGE_SELECTOR = ".event-item-thumb img[src]"
        const val STATUS_MODIFIER_PREFIX = "mod-"
        val EVENT_PATH_PATTERN = Regex("^/event/([1-9][0-9]{0,9})(?:/[^?#]*)?(?:[?#].*)?$")
        val WHITESPACE = Regex("\\s+")
        val KNOWN_STATUS_TOKENS = setOf(
            "ongoing", "live", "upcoming", "scheduled", "completed", "complete", "finished", "paused", "suspended",
        )
    }
}

package kr.co.cotton.vlrgg_mobile.feature.events

import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Owns all Jsoup traversal and VLR.GG markup assumptions for the Events feature. */
internal class EventsParser {
    fun parseEventList(page: EventHtmlPage): EventListSource = parseSafely(page) { document ->
        val container = document.selectFirst(EVENTS_CONTAINER_SELECTOR)
            ?: sourceStructureError("Event list container is missing.")
        val cards = container.select(EVENT_CARD_SELECTOR).distinct()
        val eventLinks = container.select(EVENT_LINK_SELECTOR).distinct()
        val hasVerifiedEmptyStructure = container.selectFirst(EVENT_UPCOMING_LABEL_SELECTOR) != null &&
            container.selectFirst(EVENT_COMPLETED_LABEL_SELECTOR) != null &&
            eventLinks.isEmpty()
        if (cards.size != eventLinks.size || (cards.isEmpty() && !hasVerifiedEmptyStructure)) {
            sourceStructureError("Event list cards do not match the source structure.")
        }

        EventListSource(
            events = cards.map(::parseEventSummary),
        )
    }

    fun parseEventDetail(page: EventHtmlPage, eventId: String): EventDetailSource = parseSafely(page) { document ->
        val header = document.selectFirst(EVENT_HEADER_SELECTOR)
            ?: sourceStructureError("Event header is missing.")

        EventDetailSource(
            id = eventId,
            name = header.requiredText(EVENT_NAME_SELECTOR, "Event name is missing."),
            status = header.selectFirst(EVENT_STATUS_SELECTOR)?.statusToken()?.toEventStatus(),
            dateLabel = header.metaValue("Dates"),
            location = header.metaValue("Location", "Region"),
            series = header.selectFirst(EVENT_SERIES_SELECTOR)?.normalizedText().orNullIfBlank(),
            description = header.selectFirst(EVENT_DESCRIPTION_SELECTOR)?.normalizedText().orNullIfBlank(),
            imageUrl = header.selectFirst(EVENT_IMAGE_SELECTOR)?.attr("src")?.toPublicImageUrl(),
        )
    }

    fun parseEventMatches(page: EventHtmlPage, eventId: String): EventMatchesSource = parseSafely(page) { document ->
        val header = requireEventResourceHeader(document)
        val eventName = header.requiredText(EVENT_NAME_SELECTOR, "Event name is missing.")
        val expectedCount = document.selectFirst(EVENT_MATCH_COUNT_SELECTOR)
            ?.normalizedText()
            ?.trim('(', ')')
            ?.toNonNegativeIntOrNull()
            ?: sourceStructureError("Event match count is missing.")
        val matches = document.select(MATCH_CARD_SELECTOR)
            .distinct()
            .map { card -> parseMatch(card, eventId, eventName) }
        if (matches.size != expectedCount) {
            sourceStructureError("Event match count does not match parsed content.")
        }

        EventMatchesSource(
            matches = matches,
        )
    }

    fun parseEventNews(page: EventHtmlPage): EventNewsListSource = parseSafely(page) { document ->
        requireEventResourceHeader(document)
        val items = document.select(NEWS_CARD_SELECTOR).distinct()
        if (items.isEmpty() && !document.hasNoRelatedNewsMarker()) {
            sourceStructureError("Event news content is missing.")
        }

        EventNewsListSource(news = items.map(::parseNews))
    }

    fun parseEventStats(page: EventHtmlPage): EventStatsSource = parseSafely(page) { document ->
        requireEventResourceHeader(document)
        if (document.hasNoStatsAvailableMarker()) {
            return@parseSafely EventStatsSource.NoStatsAvailable
        }

        val table = document.selectFirst(STATS_TABLE_SELECTOR)
            ?: sourceStructureError("Event stats table is missing.")
        val rows = table.select("tbody tr").filter { row -> row.selectFirst(PLAYER_LINK_SELECTOR) != null }
        if (rows.isEmpty()) {
            sourceStructureError("Event stats rows are missing.")
        }

        EventStatsSource.Available(players = rows.map(::parsePlayerStats))
    }

    private fun parseEventSummary(card: Element): EventSummarySource {
        val statusElement = card.selectFirst(EVENT_STATUS_SELECTOR)
            ?: sourceStructureError("Event status is missing.")

        return EventSummarySource(
            id = card.attr("href").extractId(EVENT_PATH_PATTERN)
                ?: sourceStructureError("Event identifier is missing."),
            name = card.requiredText(EVENT_LIST_NAME_SELECTOR, "Event name is missing."),
            status = statusElement.statusToken().toEventStatus()
                ?: sourceStructureError("Event status is unsupported."),
            dateLabel = card.selectFirst(EVENT_DATE_SELECTOR)?.ownNormalizedText().orNullIfBlank(),
            regionCode = card.selectFirst(EVENT_REGION_FLAG_SELECTOR)?.regionCode(),
            imageUrl = card.selectFirst(EVENT_LIST_IMAGE_SELECTOR)?.attr("src")?.toPublicImageUrl(),
        )
    }

    private fun parseMatch(card: Element, eventId: String, eventName: String): EventMatchSource {
        val teams = card.select(MATCH_TEAM_SELECTOR)
        if (teams.size != REQUIRED_TEAM_COUNT) {
            sourceStructureError("Event match must contain exactly two teams.")
        }

        return EventMatchSource(
            id = card.attr("href").extractId(MATCH_PATH_PATTERN)
                ?: sourceStructureError("Match identifier is missing."),
            status = card.requiredText(MATCH_STATUS_SELECTOR, "Match status is missing.").toMatchStatus(),
            timeLabel = card.requiredText(MATCH_TIME_SELECTOR, "Match time is missing."),
            relativeTimeLabel = card.selectFirst(MATCH_RELATIVE_TIME_SELECTOR)?.normalizedText().orNullIfBlank(),
            homeTeam = parseMatchTeam(teams[HOME_TEAM_INDEX]),
            awayTeam = parseMatchTeam(teams[AWAY_TEAM_INDEX]),
            homeScore = teams[HOME_TEAM_INDEX].selectFirst(TEAM_SCORE_SELECTOR)?.normalizedText()?.toNonNegativeIntOrNull(),
            awayScore = teams[AWAY_TEAM_INDEX].selectFirst(TEAM_SCORE_SELECTOR)?.normalizedText()?.toNonNegativeIntOrNull(),
            event = EventMatchEventSource(
                name = eventName,
                series = card.selectFirst(MATCH_EVENT_SELECTOR)?.normalizedText().orNullIfBlank(),
                id = eventId,
            ),
        )
    }

    private fun parseMatchTeam(team: Element): EventMatchTeamSource = EventMatchTeamSource(
        name = team.requiredText(TEAM_NAME_SELECTOR, "Match team name is missing."),
    )

    private fun parseNews(item: Element): EventNewsSource {
        val reference = item.attr("href").extractReference(NEWS_PATH_PATTERN)
            ?: sourceStructureError("News reference is missing.")
        val title = item.attr("title").orNullIfBlank()
            ?: item.children().firstOrNull { child -> !child.hasClass("ge-text-light") }?.normalizedText().orNullIfBlank()
            ?: sourceStructureError("News title is missing.")

        return EventNewsSource(
            reference = reference,
            title = title,
            author = item.selectFirst("[data-news-author]")?.normalizedText().orNullIfBlank(),
            publishedAt = item.requiredText(NEWS_DATE_SELECTOR, "News publication date is missing."),
        )
    }

    private fun parsePlayerStats(row: Element): EventPlayerStatsSource {
        val playerLink = row.selectFirst(PLAYER_LINK_SELECTOR)
            ?: sourceStructureError("Player reference is missing.")
        val playerId = playerLink.attr("href").extractId(PLAYER_PATH_PATTERN)
            ?: sourceStructureError("Player identifier is missing.")
        val metrics = PlayerMetrics(
            roundsPlayed = row.stat("rnd")?.toNonNegativeIntOrNull(),
            rating = row.stat("rating2")?.toStatDoubleOrNull(),
            averageCombatScore = row.stat("acs")?.toNonNegativeIntOrNull(),
            killDeathRatio = row.stat("kd")?.toStatDoubleOrNull(),
            averageDamagePerRound = row.stat("adr")?.toStatDoubleOrNull(),
            killAssistSurvivedTradedPercentage = row.stat("kast")?.toStatDoubleOrNull(),
        )
        if (metrics.allNull()) {
            sourceStructureError("Player row does not contain a basic statistic.")
        }

        return EventPlayerStatsSource(
            playerId = playerId,
            playerName = playerLink.requiredText(PLAYER_NAME_SELECTOR, "Player name is missing."),
            teamAbbreviation = playerLink.selectFirst(PLAYER_TEAM_SELECTOR)?.normalizedText().orNullIfBlank(),
            roundsPlayed = metrics.roundsPlayed,
            rating = metrics.rating,
            averageCombatScore = metrics.averageCombatScore,
            killDeathRatio = metrics.killDeathRatio,
            averageDamagePerRound = metrics.averageDamagePerRound,
            killAssistSurvivedTradedPercentage = metrics.killAssistSurvivedTradedPercentage,
        )
    }

    private fun Element.metaValue(vararg labels: String): String? = select(EVENT_META_ITEM_SELECTOR)
        .firstOrNull { item ->
            item.selectFirst(EVENT_META_LABEL_SELECTOR)?.normalizedText()?.let { label ->
                labels.any { expected -> label.equals(expected, ignoreCase = true) }
            } == true
        }
        ?.selectFirst(EVENT_META_VALUE_SELECTOR)
        ?.normalizedText()
        .orNullIfBlank()

    private fun Element.statusToken(): String = classNames()
        .firstOrNull { it.startsWith(STATUS_MODIFIER_PREFIX) }
        ?.removePrefix(STATUS_MODIFIER_PREFIX)
        ?: normalizedText()

    private fun Element.regionCode(): String? = classNames()
        .firstOrNull { it.startsWith(STATUS_MODIFIER_PREFIX) && it.length > STATUS_MODIFIER_PREFIX.length }
        ?.removePrefix(STATUS_MODIFIER_PREFIX)

    private fun Element.stat(column: String): String? = selectFirst("td[data-col='$column']")
        ?.normalizedText()
        .orNullIfBlank()

    private fun Document.hasNoStatsAvailableMarker(): Boolean = select("div")
        .any { element -> element.ownNormalizedText().equals(NO_STATS_AVAILABLE, ignoreCase = true) }

    private fun Document.hasNoRelatedNewsMarker(): Boolean = select("div")
        .any { element -> element.ownNormalizedText().equals(NO_RELATED_NEWS, ignoreCase = true) }

    private fun requireEventResourceHeader(document: Document): Element =
        document.selectFirst(EVENT_HEADER_SELECTOR) ?: sourceStructureError("Event header is missing.")

    private fun <T> parseSafely(page: EventHtmlPage, parse: (Document) -> T): T = try {
        parse(Jsoup.parse(page.html, VLR_PRIMARY_ORIGIN))
    } catch (failure: SourceParsingFailure) {
        throw failure
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        throw SourceParsingFailure(page.upstreamUrl, failure)
    }

    private data class PlayerMetrics(
        val roundsPlayed: Int?,
        val rating: Double?,
        val averageCombatScore: Int?,
        val killDeathRatio: Double?,
        val averageDamagePerRound: Double?,
        val killAssistSurvivedTradedPercentage: Double?,
    ) {
        fun allNull(): Boolean = listOf(
            roundsPlayed,
            rating,
            averageCombatScore,
            killDeathRatio,
            averageDamagePerRound,
            killAssistSurvivedTradedPercentage,
        ).all { it == null }
    }

    private companion object {
        const val VLR_PRIMARY_ORIGIN = "https://www.vlr.gg/"
        const val EVENTS_CONTAINER_SELECTOR = ".events-container"
        const val EVENT_CARD_SELECTOR = "a.event-item[href]"
        const val EVENT_LINK_SELECTOR = "a[href^='/event/']"
        const val EVENT_UPCOMING_LABEL_SELECTOR = ".wf-label.mod-upcoming"
        const val EVENT_COMPLETED_LABEL_SELECTOR = ".wf-label.mod-completed"
        const val EVENT_HEADER_SELECTOR = ".event-header"
        const val EVENT_LIST_NAME_SELECTOR = ".event-item-title"
        const val EVENT_NAME_SELECTOR = ".event-header-main-title"
        const val EVENT_STATUS_SELECTOR = ".event-item-desc-item-status, [data-event-status]"
        const val EVENT_DATE_SELECTOR = ".event-item-desc-item.mod-dates"
        const val EVENT_REGION_FLAG_SELECTOR = ".event-item-desc-item.mod-location .flag"
        const val EVENT_LIST_IMAGE_SELECTOR = ".event-item-thumb img[src]"
        const val EVENT_IMAGE_SELECTOR = ".event-header-thumb img[src]"
        const val EVENT_DESCRIPTION_SELECTOR = ".event-header-main-desc"
        const val EVENT_SERIES_SELECTOR = ".event-header-main-bc > a"
        const val EVENT_META_ITEM_SELECTOR = ".event-header-main-meta > div"
        const val EVENT_META_LABEL_SELECTOR = ".label"
        const val EVENT_META_VALUE_SELECTOR = ".value"
        const val EVENT_MATCH_COUNT_SELECTOR = "a[href^='/event/matches/'] sup"
        const val MATCH_CARD_SELECTOR = "a.match-item[href]"
        const val MATCH_TEAM_SELECTOR = ".match-item-vs-team"
        const val MATCH_TIME_SELECTOR = ".match-item-time"
        const val MATCH_RELATIVE_TIME_SELECTOR = ".ml-eta"
        const val MATCH_STATUS_SELECTOR = ".ml-status"
        const val MATCH_EVENT_SELECTOR = ".match-item-event"
        const val TEAM_NAME_SELECTOR = ".match-item-vs-team-name .text-of"
        const val TEAM_SCORE_SELECTOR = ".match-item-vs-team-score"
        const val NEWS_CARD_SELECTOR = "a.wf-module-item[href]"
        const val NEWS_DATE_SELECTOR = ".ge-text-light"
        const val STATS_TABLE_SELECTOR = "table.st-table"
        const val PLAYER_LINK_SELECTOR = "td.mod-player a[href^='/player/']"
        const val PLAYER_NAME_SELECTOR = ".st-pl-name"
        const val PLAYER_TEAM_SELECTOR = ".st-pl-country"
        const val NO_STATS_AVAILABLE = "No stats available"
        const val NO_RELATED_NEWS = "No related news posts"
        const val REQUIRED_TEAM_COUNT = 2
        const val HOME_TEAM_INDEX = 0
        const val AWAY_TEAM_INDEX = 1
        const val STATUS_MODIFIER_PREFIX = "mod-"
        val EVENT_PATH_PATTERN = Regex("/event/([1-9][0-9]{0,9})(?:/[^?#]*)?(?:[?#].*)?")
        val MATCH_PATH_PATTERN = Regex("/([1-9][0-9]{0,9})(?:/[^?#]*)?(?:[?#].*)?")
        val NEWS_PATH_PATTERN = Regex("/([1-9][0-9]{0,9})/([a-z0-9][a-z0-9-]{0,127})/?(?:[?#].*)?")
        val PLAYER_PATH_PATTERN = Regex("/player/([1-9][0-9]{0,9})(?:/[^?#]*)?(?:[?#].*)?")
    }
}

private fun Element.requiredText(selector: String, message: String): String =
    selectFirst(selector)?.normalizedText().orNullIfBlank() ?: sourceStructureError(message)

private fun Element.normalizedText(): String = text().replace(WHITESPACE, " ").trim()

private fun Element.ownNormalizedText(): String = ownText().replace(WHITESPACE, " ").trim()

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.extractId(pattern: Regex): String? = pattern.matchEntire(trim())?.groupValues?.getOrNull(1)

private fun String.extractReference(pattern: Regex): String? = pattern.matchEntire(trim())?.groupValues?.let { groups ->
    groups.getOrNull(1)?.let { id -> groups.getOrNull(2)?.let { slug -> "$id/$slug" } }
}

private fun String.toEventStatus(): EventStatusSource? = when (lowercase().replace(Regex("[^a-z]"), "")) {
    "ongoing", "live" -> EventStatusSource.ONGOING
    "upcoming", "scheduled" -> EventStatusSource.UPCOMING
    "completed", "complete", "finished" -> EventStatusSource.COMPLETED
    "paused", "suspended" -> EventStatusSource.PAUSED
    else -> null
}

private fun String.toMatchStatus(): EventMatchStatusSource = when (trim().lowercase()) {
    "scheduled", "upcoming" -> EventMatchStatusSource.UPCOMING
    "live", "in progress" -> EventMatchStatusSource.LIVE
    "completed", "final" -> EventMatchStatusSource.COMPLETED
    "postponed", "delayed" -> EventMatchStatusSource.POSTPONED
    "cancelled", "canceled" -> EventMatchStatusSource.CANCELLED
    else -> EventMatchStatusSource.UNAVAILABLE
}

private fun String.toNonNegativeIntOrNull(): Int? = replace(",", "")
    .takeUnless { it == "-" || it == "–" || it == "—" }
    ?.toIntOrNull()
    ?.takeIf { it >= 0 }

private fun String.toStatDoubleOrNull(): Double? = replace(",", "").removeSuffix("%").toDoubleOrNull()

private fun String.toPublicImageUrl(): String? = trim().takeIf { it.isNotEmpty() }?.let { source ->
    when {
        source.startsWith("//") -> "https:$source"
        source.startsWith("https://") -> source
        source.startsWith("/") -> "https://www.vlr.gg$source"
        else -> null
    }
}

private fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

private val WHITESPACE = Regex("\\s+")

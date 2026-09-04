package kr.co.cotton.vlrgg_mobile.feature.player

import io.ktor.http.*
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Owns all Player-page selectors, Jsoup traversal, and raw HTML interpretation. */
internal class PlayerDetailParser {
    fun parse(content: PlayerDetailUpstreamContent): PlayerDetailSource = parseSafely(content.upstreamUrl) {
        val document = Jsoup.parse(content.html)
        PlayerDetailSource(
            profile = parseProfile(document),
            currentTeam = parseCurrentTeam(document),
            agentStats = parseAgentStats(document),
            recentMatches = parseRecentMatches(document),
        )
    }

    private fun parseProfile(document: Document): PlayerProfileSource {
        val header = document.selectFirst(PLAYER_HEADER_SELECTOR)
            ?: sourceStructureError("Player header is missing.")
        return PlayerProfileSource(
            handle = header.requiredText(PLAYER_HANDLE_SELECTOR, "Player handle is missing."),
            realName = header.selectFirst(PLAYER_REAL_NAME_SELECTOR)?.normalizedTextOrNull(),
            aliases = header.select("span")
                .firstOrNull { it.normalizedText().contains(ALIASES_LABEL, ignoreCase = true) }
                ?.normalizedText()
                ?.substringAfter(':', missingDelimiterValue = "")
                ?.split(',')
                ?.mapNotNull { alias -> alias.normalizedStringOrNull() }
                .orEmpty(),
            countryCode = header.selectFirst(PLAYER_FLAG_SELECTOR)
                ?.classNames()
                ?.firstOrNull { it.startsWith(FLAG_MODIFIER_PREFIX) }
                ?.removePrefix(FLAG_MODIFIER_PREFIX)
                ?.lowercase()
                ?.normalizedStringOrNull(),
            countryName = header.selectFirst(PLAYER_FLAG_SELECTOR)?.parent()?.normalizedTextOrNull(),
            imageUrl = header.selectFirst(PLAYER_AVATAR_SELECTOR)?.attr("src")?.toPublicImageUrl(),
        )
    }

    private fun parseCurrentTeam(document: Document): PlayerTeamSource? {
        val section = document.sectionFollowing(CURRENT_TEAMS_HEADING) ?: return null
        val links = section.matchingElements("a[href]")
        if (links.isEmpty()) return null
        val teamLinks = links.filter { it.attr("href").startsWith(TEAM_PATH_PREFIX) }
        if (teamLinks.isEmpty()) sourceStructureError("Current Team link is missing.")
        val link = teamLinks.first()
        return PlayerTeamSource(
            id = link.attr("href").matchedId(TEAM_PATH_PATTERN)
                ?: sourceStructureError("Current Team identifier is missing."),
            name = link.selectFirst(CURRENT_TEAM_NAME_SELECTOR)?.normalizedTextOrNull()
                ?: link.selectFirst("img[alt]")?.attr("alt")?.normalizedStringOrNull()
                ?: sourceStructureError("Current Team name is missing."),
            imageUrl = link.selectFirst("img[src]")?.attr("src")?.toPublicImageUrl(),
        )
    }

    private fun parseAgentStats(document: Document): List<AgentStatSource> {
        val table = document.selectFirst(AGENT_STATS_TABLE_SELECTOR) ?: return emptyList()
        val body = table.selectFirst("tbody") ?: sourceStructureError("Agent Stats table body is missing.")
        val rows = body.children()
        if (rows.any { it.normalName() != "tr" }) sourceStructureError("Agent Stats table rows are malformed.")
        return rows.map(::parseAgentStat)
    }

    private fun parseAgentStat(row: Element): AgentStatSource {
        val cells = row.children().filter { it.normalName() == "td" }
        if (cells.size != AGENT_STAT_COLUMN_COUNT) sourceStructureError("Agent stat row is malformed.")
        val agentName = cells[0].selectFirst("img[alt]")?.attr("alt")?.normalizedStringOrNull()
            ?: sourceStructureError("Agent name is missing.")
        val usage = MAPS_PLAYED_PATTERN.matchEntire(cells[1].normalizedText())
            ?: sourceStructureError("Agent usage is malformed.")
        return AgentStatSource(
            agentName = agentName,
            mapsPlayed = usage.groupValues[1].toIntOrNull()
                ?: sourceStructureError("Agent maps played is malformed."),
            pickRatePercent = PICK_RATE_PATTERN.matchEntire(cells[1].normalizedText())
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it in MIN_PERCENT..MAX_PERCENT },
            roundsPlayed = cells[2].nonNegativeIntOrNull(),
            rating = cells[3].nonNegativeFiniteDoubleOrNull(),
            averageCombatScore = cells[4].nonNegativeFiniteDoubleOrNull(),
            killDeathRatio = cells[5].nonNegativeFiniteDoubleOrNull(),
            kastPercent = cells[6].percentOrNull(),
            averageDamagePerRound = cells[7].nonNegativeFiniteDoubleOrNull(),
            killsPerRound = cells[8].nonNegativeFiniteDoubleOrNull(),
            assistsPerRound = cells[9].nonNegativeFiniteDoubleOrNull(),
            firstKillDeathRatio = cells[10].nonNegativeFiniteDoubleOrNull(),
            kills = cells[11].nonNegativeIntOrNull(),
            deaths = cells[12].nonNegativeIntOrNull(),
            assists = cells[13].nonNegativeIntOrNull(),
            firstKills = cells[14].nonNegativeIntOrNull(),
            firstDeaths = cells[15].nonNegativeIntOrNull(),
        )
    }

    private fun parseRecentMatches(document: Document): List<PlayerRecentMatchSource> {
        val section = document.sectionFollowing(RECENT_RESULTS_HEADING) ?: return emptyList()
        val candidates = section.matchingElements(MATCH_ITEM_SELECTOR)
        if (candidates.isEmpty()) {
            if (section.matchingElements("a[href]").isNotEmpty()) sourceStructureError("Recent Match structure is inconsistent.")
            return emptyList()
        }
        if (candidates.any { it.normalName() != "a" || !it.hasAttr("href") }) {
            sourceStructureError("Recent Match structure is inconsistent.")
        }
        return candidates.map(::parseRecentMatch)
            .distinctBy(PlayerRecentMatchSource::id)
            .take(MAX_RECENT_MATCHES)
    }

    private fun parseRecentMatch(card: Element): PlayerRecentMatchSource {
        val id = card.attr("href").matchedId(MATCH_PATH_PATTERN)
            ?: sourceStructureError("Recent Match identifier is missing.")
        val event = card.selectFirst(MATCH_EVENT_SELECTOR)
            ?: sourceStructureError("Recent Match event is missing.")
        val eventName = event.children().firstOrNull()?.normalizedTextOrNull()
            ?: sourceStructureError("Recent Match event name is missing.")
        val teams = card.select(MATCH_TEAM_SELECTOR)
        if (teams.size != REQUIRED_TEAM_COUNT) sourceStructureError("Recent Match must contain two teams.")
        val result = card.selectFirst(MATCH_RESULT_SELECTOR)
        val scores = result?.select("span").orEmpty()
        return PlayerRecentMatchSource(
            id = id,
            eventName = eventName,
            eventStage = event.ownNormalizedTextOrNull(),
            teamA = teams[0].toMatchTeam(),
            teamB = teams[1].toMatchTeam(),
            teamAScore = scores.getOrNull(0)?.nonNegativeIntOrNull(),
            teamBScore = scores.getOrNull(1)?.nonNegativeIntOrNull(),
            outcome = (card.classNames() + (result?.classNames().orEmpty())).toMatchOutcome(),
            playedOn = card.selectFirst(MATCH_DATE_SELECTOR)?.children()?.firstOrNull()
                ?.normalizedTextOrNull()?.toIsoDateOrNull(),
        )
    }

    private fun Element.toMatchTeam(): PlayerMatchTeamSource = PlayerMatchTeamSource(
        name = requiredText(MATCH_TEAM_NAME_SELECTOR, "Recent Match team name is missing."),
        tag = selectFirst(MATCH_TEAM_TAG_SELECTOR)?.normalizedTextOrNull(),
    )

    private fun Document.sectionFollowing(heading: String): List<Element>? {
        val sectionHeading = select(SECTION_HEADING_SELECTOR)
            .firstOrNull { it.normalizedText().equals(heading, ignoreCase = true) }
            ?: return null
        return buildList {
            var sibling = sectionHeading.nextElementSibling()
            while (sibling != null && sibling.normalName() != "h2") {
                add(sibling)
                sibling = sibling.nextElementSibling()
            }
        }
    }

    private fun List<Element>.matchingElements(selector: String): List<Element> = flatMap { element ->
        buildList {
            if (element.`is`(selector)) add(element)
            addAll(element.select(selector))
        }
    }.distinct()

    private fun Element.requiredText(selector: String, message: String): String =
        selectFirst(selector)?.normalizedTextOrNull() ?: sourceStructureError(message)

    private fun Element.normalizedText(): String = text().normalizedStringOrNull().orEmpty()
    private fun Element.normalizedTextOrNull(): String? = text().normalizedStringOrNull()
    private fun Element.ownNormalizedTextOrNull(): String? = ownText().normalizedStringOrNull()
    private fun String.normalizedStringOrNull(): String? = replace(WHITESPACE, " ").trim().ifEmpty { null }
    // Public image URLs are HTTPS-only: normalize protocol/root-relative sources and require a valid host.
    private fun String.toPublicImageUrl(): String? = trim().takeIf { it.isNotEmpty() }?.let { source ->
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
    private fun String.matchedId(pattern: Regex): String? = pattern.matchEntire(this)?.groups?.get(1)?.value
    private fun Element.nonNegativeIntOrNull(): Int? =
        normalizedText().replace(",", "").toIntOrNull()?.takeIf { it >= 0 }
    private fun Element.nonNegativeFiniteDoubleOrNull(): Double? =
        normalizedText().replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    private fun Element.percentOrNull(): Int? =
        PERCENT_PATTERN.matchEntire(normalizedText())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it in MIN_PERCENT..MAX_PERCENT }
    private fun Set<String>.toMatchOutcome(): PlayerMatchOutcomeSource = when {
        MATCH_WIN_CLASS in this -> PlayerMatchOutcomeSource.WIN
        MATCH_LOSS_CLASS in this -> PlayerMatchOutcomeSource.LOSS
        else -> PlayerMatchOutcomeSource.UNKNOWN
    }
    private fun String.toIsoDateOrNull(): String? = try {
        LocalDate.parse(this, VLR_DATE_FORMAT).toString()
    } catch (_: Exception) {
        null
    }
    private fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

    private inline fun <T> parseSafely(upstreamUrl: Url, block: () -> T): T = try {
        block()
    } catch (failure: SourceParsingFailure) {
        throw failure
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        throw SourceParsingFailure(upstreamUrl, failure)
    }

    private companion object {
        const val PLAYER_HEADER_SELECTOR = ".player-header"
        const val PLAYER_HANDLE_SELECTOR = "h1.wf-title"
        const val PLAYER_REAL_NAME_SELECTOR = ".player-real-name"
        const val PLAYER_AVATAR_SELECTOR = ".wf-avatar.mod-player img[src]"
        const val PLAYER_FLAG_SELECTOR = "i.flag"
        const val FLAG_MODIFIER_PREFIX = "mod-"
        const val ALIASES_LABEL = "aliases:"
        const val SECTION_HEADING_SELECTOR = "h2.wf-label.mod-large"
        const val CURRENT_TEAMS_HEADING = "Current Teams"
        const val RECENT_RESULTS_HEADING = "Recent Results"
        const val TEAM_PATH_PREFIX = "/team/"
        const val CURRENT_TEAM_NAME_SELECTOR = "div[style*=font-weight]"
        const val AGENT_STATS_TABLE_SELECTOR = "table.st-table.mod-agent-rows"
        const val MATCH_ITEM_SELECTOR = ".m-item"
        const val MATCH_EVENT_SELECTOR = ".m-item-event"
        const val MATCH_TEAM_SELECTOR = ".m-item-team"
        const val MATCH_TEAM_NAME_SELECTOR = ".m-item-team-name"
        const val MATCH_TEAM_TAG_SELECTOR = ".m-item-team-tag"
        const val MATCH_RESULT_SELECTOR = ".m-item-result"
        const val MATCH_DATE_SELECTOR = ".m-item-date"
        const val MATCH_WIN_CLASS = "mod-win"
        const val MATCH_LOSS_CLASS = "mod-loss"
        const val AGENT_STAT_COLUMN_COUNT = 16
        const val REQUIRED_TEAM_COUNT = 2
        const val MAX_RECENT_MATCHES = 5
        val TEAM_PATH_PATTERN = Regex("^/team/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        val MATCH_PATH_PATTERN = Regex("^/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
        val MAPS_PLAYED_PATTERN = Regex("^\\(([0-9]+)\\)(?:\\s+.*)?$")
        val PICK_RATE_PATTERN = Regex("^\\([0-9]+\\)\\s+([0-9]+)%$")
        val PERCENT_PATTERN = Regex("^([0-9]+)%$")
        val VLR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val WHITESPACE = Regex("\\s+")
    }
}

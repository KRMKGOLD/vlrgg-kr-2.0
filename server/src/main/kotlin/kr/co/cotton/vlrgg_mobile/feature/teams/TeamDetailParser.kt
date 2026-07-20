package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import kr.co.cotton.vlrgg_mobile.feature.news.NewsReference
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Keeps all VLR.GG DOM selectors and traversal inside the Team parsing boundary. */
internal class TeamDetailParser {
    fun parse(content: TeamDetailUpstreamContent): TeamDetailSource {
        val overview = parseOverview(content.overviewHtml, content.overviewUrl)
        return TeamDetailSource(
            profile = overview.profile,
            upcomingMatches = overview.upcomingMatches,
            recentMatches = overview.recentMatches,
            players = overview.players,
            staff = overview.staff,
            news = parseNews(content.newsHtml, content.newsUrl),
        )
    }

    private fun parseOverview(html: String, upstreamUrl: Url): ParsedOverview = parseSafely(upstreamUrl) {
        val document = Jsoup.parse(html)
        val profile = parseProfile(document)
        val roster = parseRoster(document)
        ParsedOverview(
            profile = profile,
            upcomingMatches = parseMatchSection(document, UPCOMING_MATCHES_HEADING),
            recentMatches = parseMatchSection(document, RECENT_RESULTS_HEADING),
            players = roster.first,
            staff = roster.second,
        )
    }

    private fun parseNews(html: String, upstreamUrl: Url): List<TeamNewsSource> = parseSafely(upstreamUrl) {
        val document = Jsoup.parse(html)
        val header = requireNotNull(document.selectFirst(TEAM_HEADER_SELECTOR)) { "Team news header is missing." }
        val container = header.nextElementSibling() ?: return@parseSafely emptyList()
        if (!container.hasClass(TEAM_NEWS_CONTAINER_CLASS)) {
            sourceStructureError("Team news container is malformed.")
        }

        val items = container.children().filter { item ->
            item.normalName() == "a" && item.hasClass(NEWS_ITEM_CLASS)
        }
        if (items.isEmpty()) {
            if (container.select("a").isNotEmpty()) sourceStructureError("Team news item structure is inconsistent.")
            return@parseSafely emptyList()
        }

        items.mapNotNull(::parseNewsItem).distinctBy { it.reference.value }
    }

    private fun parseProfile(document: Document): TeamProfileSource {
        val header = requireNotNull(document.selectFirst(TEAM_HEADER_SELECTOR)) { "Team header is missing." }
        return TeamProfileSource(
            name = header.requiredText(TEAM_NAME_SELECTOR, "Team name is missing."),
            tag = header.selectFirst(TEAM_TAG_SELECTOR)?.normalizedTextOrNull(),
            country = header.selectFirst(TEAM_COUNTRY_SELECTOR)?.normalizedTextOrNull(),
        )
    }

    private fun parseMatchSection(document: Document, heading: String): List<TeamMatchSource> {
        val section = document.sectionFollowing(heading) ?: return emptyList()
        val candidates = section.flatMap { it.select(MATCH_ITEM_SELECTOR) }
        if (candidates.isEmpty()) {
            if (section.flatMap { it.select("a") }.isNotEmpty()) {
                sourceStructureError("Match section candidate structure is inconsistent.")
            }
            return emptyList()
        }
        return candidates.mapNotNull(::parseMatchOrExcludeContaminated)
            .distinctBy(TeamMatchSource::id)
    }

    private fun parseMatchOrExcludeContaminated(element: Element): TeamMatchSource? {
        val href = element.attr("href")
        if (href.matches(MATCH_PATH_PATTERN)) return parseMatch(element)
        if (href.isMalformedCanonicalPath()) sourceStructureError("Match reference is malformed.")
        return null
    }

    private fun parseMatch(element: Element): TeamMatchSource {
        val id = element.attr("href").matchedId(MATCH_PATH_PATTERN)
            ?: sourceStructureError("Match identifier is missing.")
        val teamNames = element.select(MATCH_TEAM_NAME_SELECTOR).mapNotNull { it.normalizedTextOrNull() }
        if (teamNames.size != REQUIRED_TEAM_COUNT) sourceStructureError("Match must contain exactly two teams.")

        val event = element.selectFirst(MATCH_EVENT_SELECTOR)
        return TeamMatchSource(
            id = id,
            eventName = event?.children()?.firstOrNull()?.normalizedTextOrNull(),
            eventStage = event?.ownText()?.normalizedTextOrNull(),
            teamName = teamNames[0],
            opponentName = teamNames[1],
            statusText = element.selectFirst(MATCH_RESULT_SELECTOR)?.normalizedTextOrNull(),
            scheduledAtText = element.selectFirst(MATCH_DATE_SELECTOR)?.normalizedTextOrNull(),
        )
    }

    private fun parseRoster(document: Document): Pair<List<TeamRosterMemberSource>, List<TeamRosterMemberSource>> {
        val rosterSection = document.sectionFollowing(CURRENT_ROSTER_HEADING) ?: return emptyList<TeamRosterMemberSource>() to emptyList()
        if (rosterSection.isNotEmpty() && rosterSection.flatMap { it.select(ROSTER_LABEL_SELECTOR) }.isEmpty()) {
            sourceStructureError("Roster section structure is inconsistent.")
        }
        return rosterSection.rosterItemsFor(PLAYERS_LABEL) to rosterSection.rosterItemsFor(STAFF_LABEL)
    }

    private fun List<Element>.rosterItemsFor(label: String): List<TeamRosterMemberSource> {
        val sectionLabel = asSequence()
            .flatMap { it.select(ROSTER_LABEL_SELECTOR).asSequence() }
            .firstOrNull { it.normalizedTextOrNull()?.equals(label, ignoreCase = true) == true }
            ?: return emptyList()
        val content = sectionLabel.nextElementSibling()
            ?: sourceStructureError("Roster group content is missing.")
        val candidates = content.select(ROSTER_ITEM_SELECTOR)
        if (candidates.isEmpty()) {
            if (content.select("a").isNotEmpty()) sourceStructureError("Roster item structure is inconsistent.")
            return emptyList()
        }
        return candidates.mapNotNull(::parseRosterMemberOrExcludeContaminated)
            .distinctBy(TeamRosterMemberSource::id)
    }

    private fun parseRosterMemberOrExcludeContaminated(element: Element): TeamRosterMemberSource? {
        val link = element.selectFirst("a") ?: sourceStructureError("Roster member link is missing.")
        val href = link.attr("href")
        if (href.matches(PLAYER_PATH_PATTERN)) return parseRosterMember(link)
        if (href.isMalformedCanonicalPlayerPath()) sourceStructureError("Player reference is malformed.")
        return null
    }

    private fun parseRosterMember(link: Element): TeamRosterMemberSource {
        val id = link.attr("href").matchedId(PLAYER_PATH_PATTERN)
            ?: sourceStructureError("Player identifier is missing.")
        return TeamRosterMemberSource(
            id = id,
            handle = link.requiredText(ROSTER_HANDLE_SELECTOR, "Player handle is missing."),
            realName = link.selectFirst(ROSTER_REAL_NAME_SELECTOR)?.normalizedTextOrNull(),
            roleLabels = link.select(ROSTER_ROLE_SELECTOR).mapNotNull { it.normalizedTextOrNull() },
        )
    }

    private fun parseNewsItem(element: Element): TeamNewsSource? {
        if (element.isKnownMatchModule()) return null

        val href = element.attr("href")
        if (href.isEmpty()) sourceStructureError("News reference is missing.")
        val reference = NewsReference.fromHref(href)
            ?: if (href.startsWith("/") && !href.startsWith("//")) {
                sourceStructureError("News reference is malformed.")
            } else {
                return null
            }
        val title = element.attr("title").normalizedTextOrNull()
            ?: element.children().firstOrNull { !it.hasClass(NEWS_DATE_CLASS) }?.normalizedTextOrNull()
            ?: sourceStructureError("News title is missing.")
        return TeamNewsSource(
            reference = reference,
            title = title,
            publishedDateText = element.selectFirst(".$NEWS_DATE_CLASS")?.normalizedTextOrNull(),
        )
    }

    private fun Element.isKnownMatchModule(): Boolean = hasClass(MATCH_MODULE_CLASS) ||
        selectFirst(MATCH_MODULE_CONTENT_SELECTOR) != null

    private fun Document.sectionFollowing(heading: String): List<Element>? {
        val sectionHeading = select(SECTION_HEADING_SELECTOR)
            .firstOrNull { it.normalizedTextOrNull()?.equals(heading, ignoreCase = true) == true }
            ?: return null
        return buildList {
            var sibling = sectionHeading.nextElementSibling()
            while (sibling != null && !sibling.isSectionHeading()) {
                add(sibling)
                sibling = sibling.nextElementSibling()
            }
        }
    }

    private fun Element.isSectionHeading(): Boolean = tagName() == "h2" && hasClass("wf-label") && hasClass("mod-large")

    private fun Element.requiredText(selector: String, message: String): String = selectFirst(selector)
        ?.normalizedTextOrNull()
        ?: sourceStructureError(message)

    private fun Element.normalizedTextOrNull(): String? = text().normalizedTextOrNull()

    private fun String.normalizedTextOrNull(): String? = replace(WHITESPACE, " ").trim().ifEmpty { null }

    private fun String.matchedId(pattern: Regex): String? = pattern.matchEntire(this)?.groups?.get(1)?.value

    private fun String.isMalformedCanonicalPath(): Boolean = startsWith("/") &&
        drop(1).firstOrNull()?.isDigit() == true &&
        !contains('?') &&
        !contains('#')

    private fun String.isMalformedCanonicalPlayerPath(): Boolean = startsWith(PLAYER_PATH_PREFIX) &&
        removePrefix(PLAYER_PATH_PREFIX).firstOrNull()?.isDigit() == true &&
        !contains('?') &&
        !contains('#')

    private fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

    private inline fun <T> parseSafely(upstreamUrl: Url, block: () -> T): T = try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        throw SourceParsingFailure(upstreamUrl, exception)
    }

    private data class ParsedOverview(
        val profile: TeamProfileSource,
        val upcomingMatches: List<TeamMatchSource>,
        val recentMatches: List<TeamMatchSource>,
        val players: List<TeamRosterMemberSource>,
        val staff: List<TeamRosterMemberSource>,
    )

    private companion object {
        const val TEAM_HEADER_SELECTOR = ".team-header"
        const val TEAM_NAME_SELECTOR = ".team-header-name h1.wf-title"
        const val TEAM_TAG_SELECTOR = ".team-header-tag"
        const val TEAM_COUNTRY_SELECTOR = ".team-header-country"
        const val SECTION_HEADING_SELECTOR = "h2.wf-label.mod-large"
        const val UPCOMING_MATCHES_HEADING = "Upcoming matches"
        const val RECENT_RESULTS_HEADING = "Recent Results"
        const val CURRENT_ROSTER_HEADING = "Current Roster"
        const val MATCH_ITEM_SELECTOR = "a.m-item[href]"
        const val MATCH_TEAM_NAME_SELECTOR = ".m-item-team-name"
        const val MATCH_EVENT_SELECTOR = ".m-item-event"
        const val MATCH_RESULT_SELECTOR = ".m-item-result"
        const val MATCH_DATE_SELECTOR = ".m-item-date"
        const val ROSTER_LABEL_SELECTOR = ".wf-module-label"
        const val ROSTER_ITEM_SELECTOR = ".team-roster-item"
        const val ROSTER_HANDLE_SELECTOR = ".team-roster-item-name-alias"
        const val ROSTER_REAL_NAME_SELECTOR = ".team-roster-item-name-real"
        const val ROSTER_ROLE_SELECTOR = ".team-roster-item-name-role"
        const val TEAM_NEWS_CONTAINER_CLASS = "wf-card"
        const val NEWS_ITEM_CLASS = "wf-module-item"
        const val NEWS_DATE_CLASS = "ge-text-light"
        const val MATCH_MODULE_CLASS = "match-item"
        const val MATCH_MODULE_CONTENT_SELECTOR = ".match-item-time, .match-item-vs, .match-item-event"
        const val PLAYERS_LABEL = "players"
        const val STAFF_LABEL = "staff"
        const val REQUIRED_TEAM_COUNT = 2

        val MATCH_PATH_PATTERN = Regex("^/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        val PLAYER_PATH_PATTERN = Regex("^/player/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        const val PLAYER_PATH_PREFIX = "/player/"
        val WHITESPACE = Regex("\\s+")
    }
}

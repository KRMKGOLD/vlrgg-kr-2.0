package kr.co.cotton.vlrgg_mobile.feature.teams

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
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
        requireNotNull(document.selectFirst(TEAM_HEADER_SELECTOR)) { "Team news header is missing." }
        document.select(NEWS_ITEM_SELECTOR)
            .filter { it.attr("href").matches(NEWS_PATH_PATTERN) }
            .map(::parseNewsItem)
            .distinctBy(TeamNewsSource::id)
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
        return section.flatMap { it.select(MATCH_ITEM_SELECTOR) }
            .filter { it.attr("href").matches(MATCH_PATH_PATTERN) }
            .map(::parseMatch)
            .distinctBy(TeamMatchSource::id)
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
        return rosterSection.rosterItemsFor(PLAYERS_LABEL) to rosterSection.rosterItemsFor(STAFF_LABEL)
    }

    private fun List<Element>.rosterItemsFor(label: String): List<TeamRosterMemberSource> {
        val sectionLabel = asSequence()
            .flatMap { it.select(ROSTER_LABEL_SELECTOR).asSequence() }
            .firstOrNull { it.normalizedTextOrNull()?.equals(label, ignoreCase = true) == true }
            ?: return emptyList()
        return sectionLabel.nextElementSibling()
            ?.select(ROSTER_ITEM_SELECTOR)
            .orEmpty()
            .mapNotNull { it.selectFirst("a[href]") }
            .filter { it.attr("href").matches(PLAYER_PATH_PATTERN) }
            .map(::parseRosterMember)
            .distinctBy(TeamRosterMemberSource::id)
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

    private fun parseNewsItem(element: Element): TeamNewsSource {
        val id = element.attr("href").matchedId(NEWS_PATH_PATTERN)
            ?: sourceStructureError("News identifier is missing.")
        val title = element.attr("title").normalizedTextOrNull()
            ?: element.children().firstOrNull { !it.hasClass(NEWS_DATE_CLASS) }?.normalizedTextOrNull()
            ?: sourceStructureError("News title is missing.")
        return TeamNewsSource(
            id = id,
            title = title,
            publishedDateText = element.selectFirst(".$NEWS_DATE_CLASS")?.normalizedTextOrNull(),
        )
    }

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
        const val NEWS_ITEM_SELECTOR = "a.wf-module-item[href]"
        const val NEWS_DATE_CLASS = "ge-text-light"
        const val PLAYERS_LABEL = "players"
        const val STAFF_LABEL = "staff"
        const val REQUIRED_TEAM_COUNT = 2

        val MATCH_PATH_PATTERN = Regex("^/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        val PLAYER_PATH_PATTERN = Regex("^/player/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        val NEWS_PATH_PATTERN = Regex("^/([1-9][0-9]{0,9})/[a-z0-9-]+/?$")
        val WHITESPACE = Regex("\\s+")
    }
}

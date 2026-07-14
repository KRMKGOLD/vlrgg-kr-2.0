package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.http.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal class VlrMatchesParser(
    private val htmlParser: (String) -> Document = Jsoup::parse,
) {
    fun parseList(html: String, upstreamUrl: Url): MatchesPageSource = parse(upstreamUrl) {
        val document = htmlParser(html)
        MatchesPageSource(
            groups = document.select("div.wf-label.mod-large").map { dateLabel ->
                val card = dateLabel.nextElementSibling()
                    ?.takeIf { it.hasClass("wf-card") }
                    ?: sourceStructureError("Match date group is missing its match card.")
                val matches = card.children()
                    .filter { it.`is`("a.match-item") }
                    .map(::parseListMatch)
                if (matches.isEmpty()) {
                    sourceStructureError("Match date group does not contain a match.")
                }
                MatchDateGroupSource(
                    dateLabel = dateLabel.requiredText(),
                    matches = matches,
                )
            },
        )
    }

    fun parseDetail(html: String, upstreamUrl: Url, matchId: String): MatchDetailSource = parse(upstreamUrl) {
        val document = htmlParser(html)
        val header = document.selectFirst("div.match-header")
            ?: sourceStructureError("Match header is missing.")
        val versus = header.selectFirst("div.match-header-vs")
            ?: sourceStructureError("Match participants are missing.")
        val teams = versus.children().filter { it.`is`("a.match-header-link") }
        if (teams.size != REQUIRED_TEAM_COUNT) {
            sourceStructureError("Match must contain exactly two participants.")
        }

        val score = versus.selectFirst("div.match-header-vs-score")
            ?: sourceStructureError("Match status is missing.")
        val notes = score.children()
            .filter { it.hasClass("match-header-vs-note") }
            .mapNotNull { it.normalizedText().orNullIfBlank() }
        val status = score.statusFromModifierOrNull()
            ?: notes.firstOrNull()?.toMatchStatus()
            ?: sourceStructureError("Match status is missing.")
        val scoreValues = score.extractScoreValues()
        val event = header.selectFirst("a.match-header-event")
            ?: sourceStructureError("Match event is missing.")
        val eventSeries = event.selectFirst(".match-header-event-series")?.normalizedText().orNullIfBlank()

        val summary = MatchSummarySource(
            id = matchId,
            status = status,
            timeLabel = header.detailTimeLabel()
                ?: status.defaultTimeLabel(),
            relativeTimeLabel = null,
            homeTeam = MatchTeamSource(
                name = teams[HOME_TEAM_INDEX].requiredText(".wf-title-med"),
                id = teams[HOME_TEAM_INDEX].attr("href").extractTeamIdOrNull(),
            ),
            awayTeam = MatchTeamSource(
                name = teams[AWAY_TEAM_INDEX].requiredText(".wf-title-med"),
                id = teams[AWAY_TEAM_INDEX].attr("href").extractTeamIdOrNull(),
            ),
            homeScore = scoreValues.getOrNull(HOME_TEAM_INDEX),
            awayScore = scoreValues.getOrNull(AWAY_TEAM_INDEX),
            event = MatchEventSource(
                name = event.requiredEventName(),
                series = eventSeries,
                id = event.attr("href").extractEventIdOrNull(),
            ),
        )

        MatchDetailSource(
            summary = summary,
            scheduledAt = header.selectFirst(".match-header-date [data-utc-ts]")
                ?.attr("data-utc-ts")
                ?.toUtcIsoInstantOrNull(),
            description = header.selectFirst(".match-header-note")?.normalizedText().orNullIfBlank(),
            seriesFormat = notes.drop(1).firstOrNull()?.orNullIfBlank(),
            maps = document.select(".vm-stats-game[data-game-id]")
                .filter { it.attr("data-game-id") != ALL_MAPS_GAME_ID }
                .mapNotNull(::parseMapOrNull),
            headToHead = document.parseHeadToHead(summary),
            pastMatches = document.parsePastMatches(summary),
        )
    }

    private fun parseListMatch(row: Element): MatchSummarySource {
        val matchId = row.attr("href").extractMatchIdOrNull()
            ?: sourceStructureError("Match ID is missing or invalid.")
        val versus = row.selectFirst(".match-item-vs")
            ?: sourceStructureError("Match participants are missing.")
        val teams = versus.children().filter { it.hasClass("match-item-vs-team") }
        if (teams.size != REQUIRED_TEAM_COUNT) {
            sourceStructureError("Match must contain exactly two participants.")
        }
        val event = row.selectFirst(".match-item-event")
            ?: sourceStructureError("Match event is missing.")

        return MatchSummarySource(
            id = matchId,
            status = row.requiredText(".ml-status").toMatchStatus(),
            timeLabel = row.requiredText(".match-item-time"),
            relativeTimeLabel = row.selectFirst(".ml-eta")?.normalizedText().orNullIfBlank(),
            homeTeam = MatchTeamSource(teams[HOME_TEAM_INDEX].requiredText(".match-item-vs-team-name")),
            awayTeam = MatchTeamSource(teams[AWAY_TEAM_INDEX].requiredText(".match-item-vs-team-name")),
            homeScore = teams[HOME_TEAM_INDEX].selectFirst(".match-item-vs-team-score")?.normalizedText().toScoreOrNull(),
            awayScore = teams[AWAY_TEAM_INDEX].selectFirst(".match-item-vs-team-score")?.normalizedText().toScoreOrNull(),
            event = MatchEventSource(
                name = event.ownText().trimmedOrNull()
                    ?: sourceStructureError("Match event name is missing."),
                series = event.selectFirst(".match-item-event-series")?.normalizedText().orNullIfBlank(),
            ),
        )
    }

    private fun parseMapOrNull(map: Element): MatchMapSource? {
        val header = map.selectFirst(".vm-stats-game-header") ?: return null
        val name = header.selectFirst(".map span")?.ownText().trimmedOrNull() ?: return null
        val scores = header.children()
            .filter { it.hasClass("team") }
            .map { it.selectFirst(".score")?.normalizedText().toScoreOrNull() }
        return MatchMapSource(
            name = name,
            homeScore = scores.getOrNull(HOME_TEAM_INDEX),
            awayScore = scores.getOrNull(AWAY_TEAM_INDEX),
        )
    }

    /**
     * H2H rows do not expose team names of their own. They are therefore usable only when the
     * row itself has the canonical match path; the two participants are the enclosing detail's
     * validated team pair and the score slots preserve the row's left-to-right order.
     */
    private fun Document.parseHeadToHead(summary: MatchSummarySource): List<RelatedMatchSource> =
        select(".match-h2h-matches").flatMap { section ->
            section.children()
                .filter { it.`is`("a.wf-module-item.mod-h2h") }
                .mapNotNull { row -> row.toHeadToHeadOrNull(summary) }
        }

    private fun Element.toHeadToHeadOrNull(summary: MatchSummarySource): RelatedMatchSource? {
        val id = attr("href").extractMatchIdOrNull() ?: return null
        val scores = relatedScorePairOrNull(".match-h2h-matches-score")

        return RelatedMatchSource(
            id = id,
            homeTeamName = summary.homeTeam.name,
            awayTeamName = summary.awayTeam.name,
            homeScore = scores?.get(HOME_TEAM_INDEX),
            awayScore = scores?.get(AWAY_TEAM_INDEX),
        )
    }

    /**
     * The source marks only the first detail team with `mod-first`; section order is not an
     * identity. The available sections must have exactly one marker, otherwise assigning either
     * validated team to a history section would be ambiguous and the optional section is omitted.
     */
    private fun Document.parsePastMatches(summary: MatchSummarySource): List<RelatedMatchSource> {
        val sections = select(".match-histories")
        if (sections.size > REQUIRED_TEAM_COUNT || sections.count { it.hasClass(FIRST_TEAM_CLASS) } != 1) {
            return emptyList()
        }

        return sections.flatMap { section ->
            val teamName = if (section.hasClass(FIRST_TEAM_CLASS)) {
                summary.homeTeam.name
            } else {
                summary.awayTeam.name
            }
            section.children()
                .filter { it.`is`("a.match-histories-item") }
                .mapNotNull { row -> row.toPastMatchOrNull(teamName) }
        }
    }

    private fun Element.toPastMatchOrNull(teamName: String): RelatedMatchSource? {
        val id = attr("href").extractMatchIdOrNull() ?: return null
        val opponentName = selectFirst(".match-histories-item-opponent-name")
            ?.normalizedText()
            .orNullIfBlank()
            ?: return null
        val scores = relatedScorePairOrNull(".match-histories-item-result")

        return RelatedMatchSource(
            id = id,
            homeTeamName = teamName,
            awayTeamName = opponentName,
            homeScore = scores?.get(HOME_TEAM_INDEX),
            awayScore = scores?.get(AWAY_TEAM_INDEX),
        )
    }

    /** A related row remains valid without a trustworthy score pair because scores are optional. */
    private fun Element.relatedScorePairOrNull(selector: String): List<Int>? {
        val slots = selectFirst(selector)
            ?.children()
            ?.takeIf { it.size == REQUIRED_TEAM_COUNT }
            ?: return null
        val homeScore = slots[HOME_TEAM_INDEX].normalizedText().toScoreOrNull() ?: return null
        val awayScore = slots[AWAY_TEAM_INDEX].normalizedText().toScoreOrNull() ?: return null

        return listOf(homeScore, awayScore)
    }

    private fun Element.requiredEventName(): String {
        val directDivs = children().filter { it.`is`("div") }
        return (directDivs + directDivs.flatMap { it.children().filter { child -> child.`is`("div") } })
            .firstOrNull { !it.hasClass("match-header-event-series") && it.ownText().trimmedOrNull() != null }
            ?.ownText()
            .trimmedOrNull()
            ?: sourceStructureError("Match event name is missing.")
    }

    private fun Element.detailTimeLabel(): String? = select(".match-header-date .moment-tz-convert")
        .map { it.normalizedText() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .orNullIfBlank()

    /**
     * VLR's detail state is normally encoded in an `mod-*` class. It must take priority over
     * the adjacent text because upcoming pages can use a countdown there instead of a state word.
     */
    private fun Element.statusFromModifierOrNull(): MatchStatusSource? = getAllElements()
        .asSequence()
        .flatMap { element -> element.classNames().asSequence() }
        .filter { className -> className.startsWith(STATUS_MODIFIER_PREFIX) }
        .mapNotNull { className -> className.removePrefix(STATUS_MODIFIER_PREFIX).toKnownMatchStatusOrNull() }
        .firstOrNull()

    /**
     * A score is only trusted when a single detail score subtree contains exactly
     * `[home score, separator, away score]`. This rejects unrelated numeric descendants and
     * preserves the same left-to-right order as the two validated team links.
     */
    private fun Element.extractScoreValues(): List<Int?> = select("div.js-spoiler, div.match-header-vs-score")
        .asSequence()
        .mapNotNull { scoreMarkup -> scoreMarkup.strictScorePairOrNull() }
        .firstOrNull()
        .orEmpty()

    private fun Element.strictScorePairOrNull(): List<Int>? {
        val slots = children()
        if (slots.isClassifiedScorePair()) {
            return slots.toScorePairOrNull()
        }
        if (slots.size != SCORE_SLOT_COUNT || slots[SCORE_SEPARATOR_INDEX].normalizedText() != SCORE_SEPARATOR) {
            return null
        }
        return slots.toScorePairOrNull()
    }

    private fun List<Element>.toScorePairOrNull(): List<Int>? {
        val homeScore = get(HOME_SCORE_INDEX).normalizedText().toStrictScoreOrNull() ?: return null
        val awayScore = last().normalizedText().toStrictScoreOrNull() ?: return null
        return listOf(homeScore, awayScore)
    }

    private fun List<Element>.isClassifiedScorePair(): Boolean =
        size == CLASSIFIED_SCORE_SLOT_COUNT &&
            ((get(HOME_SCORE_INDEX).hasClass(HOME_SCORE_CLASS) && last().hasClass(AWAY_SCORE_CLASS)) ||
                (get(HOME_SCORE_INDEX).hasClass(AWAY_SCORE_CLASS) && last().hasClass(HOME_SCORE_CLASS)))

    private fun Element.requiredText(selector: String): String = selectFirst(selector)
        ?.normalizedText()
        .orNullIfBlank()
        ?: sourceStructureError("Required source text is missing.")

    private fun Element.requiredText(): String = normalizedText().orNullIfBlank()
        ?: sourceStructureError("Required source text is missing.")

    private fun Element.normalizedText(): String = text().replace(WHITESPACE, " ").trim()

    private fun String?.toScoreOrNull(): Int? = this
        ?.trim()
        ?.takeUnless { it.isEmpty() || it == "-" || it == "–" || it == "—" }
        ?.toIntOrNull()

    private fun String.toMatchStatus(): MatchStatusSource = toKnownMatchStatusOrNull() ?: MatchStatusSource.UNAVAILABLE

    private fun String.toKnownMatchStatusOrNull(): MatchStatusSource? = when (trim().lowercase()) {
        "upcoming" -> MatchStatusSource.UPCOMING
        "live", "in progress" -> MatchStatusSource.LIVE
        "completed", "final" -> MatchStatusSource.COMPLETED
        "postponed", "delayed" -> MatchStatusSource.POSTPONED
        "cancelled", "canceled" -> MatchStatusSource.CANCELLED
        "unavailable" -> MatchStatusSource.UNAVAILABLE
        else -> null
    }

    private fun MatchStatusSource.defaultTimeLabel(): String = when (this) {
        MatchStatusSource.UPCOMING -> "Upcoming"
        MatchStatusSource.LIVE -> "Live"
        MatchStatusSource.COMPLETED -> "Completed"
        MatchStatusSource.POSTPONED -> "Postponed"
        MatchStatusSource.CANCELLED -> "Cancelled"
        MatchStatusSource.UNAVAILABLE -> "Unavailable"
    }

    private fun String.toUtcIsoInstantOrNull(): String? = runCatching {
        LocalDateTime.parse(this, UPSTREAM_UTC_TIMESTAMP_FORMAT)
            .toInstant(ZoneOffset.UTC)
            .toString()
    }.getOrNull()

    private fun String.extractMatchIdOrNull(): String? = canonicalVlrPathOrNull()
        ?.let(MATCH_PATH_REGEX::matchEntire)
        ?.groupValues
        ?.get(MATCH_ID_GROUP)

    private fun String.extractTeamIdOrNull(): String? = canonicalVlrPathOrNull()
        ?.let(TEAM_PATH_REGEX::matchEntire)
        ?.groupValues
        ?.get(MATCH_ID_GROUP)

    private fun String.extractEventIdOrNull(): String? = canonicalVlrPathOrNull()
        ?.let(EVENT_PATH_REGEX::matchEntire)
        ?.groupValues
        ?.get(MATCH_ID_GROUP)

    private fun String.canonicalVlrPathOrNull(): String? {
        val href = trim()
        if (href.startsWith("/")) return href
        return CANONICAL_VLR_ORIGINS
            .firstOrNull(href::startsWith)
            ?.let(href::removePrefix)
    }

    private inline fun <T> parse(upstreamUrl: Url, block: () -> T): T = try {
        block()
    } catch (failure: SourceParsingFailure) {
        throw failure
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        throw SourceParsingFailure(upstreamUrl, failure)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val MATCH_PATH_REGEX = Regex("/([1-9]\\d{0,9})(?:/[^?#]*)?(?:[?#].*)?")
        val TEAM_PATH_REGEX = Regex("/team/([1-9]\\d{0,9})(?:/[^?#]*)?(?:[?#].*)?")
        val EVENT_PATH_REGEX = Regex("/event/([1-9]\\d{0,9})(?:/[^?#]*)?(?:[?#].*)?")
        val CANONICAL_VLR_ORIGINS = listOf("https://www.vlr.gg", "https://vlr.gg")
        val UPSTREAM_UTC_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val REQUIRED_TEAM_COUNT = 2
        const val HOME_TEAM_INDEX = 0
        const val AWAY_TEAM_INDEX = 1
        const val MATCH_ID_GROUP = 1
        const val ALL_MAPS_GAME_ID = "all"
        const val STATUS_MODIFIER_PREFIX = "mod-"
        const val FIRST_TEAM_CLASS = "mod-first"
        const val SCORE_SLOT_COUNT = 3
        const val CLASSIFIED_SCORE_SLOT_COUNT = 2
        const val HOME_SCORE_INDEX = 0
        const val SCORE_SEPARATOR_INDEX = 1
        const val SCORE_SEPARATOR = ":"
        const val HOME_SCORE_CLASS = "match-header-vs-score-winner"
        const val AWAY_SCORE_CLASS = "match-header-vs-score-loser"
    }
}

private fun String?.orNullIfBlank(): String? = this?.trimmedOrNull()

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.toStrictScoreOrNull(): Int? = takeIf { STRICT_SCORE_REGEX.matches(it) }?.toIntOrNull()

private fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

private val STRICT_SCORE_REGEX = Regex("\\d+")

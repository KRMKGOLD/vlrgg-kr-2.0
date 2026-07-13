package kr.co.cotton.vlrgg_mobile.feature.matches

import io.ktor.http.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kr.co.cotton.vlrgg_mobile.common.http.SourceParsingFailure
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class VlrMatchesParser {
    fun parseList(html: String, upstreamUrl: Url): MatchesPageSource = parse(upstreamUrl) {
        val document = Jsoup.parse(html)
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
        val document = Jsoup.parse(html)
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
            .map { it.requiredText() }
        val status = notes.firstOrNull()?.toMatchStatus()
            ?: sourceStructureError("Match status is missing.")
        val scoreValues = score.selectFirst("div.js-spoiler")
            ?.select("span.match-header-vs-score-winner, span.match-header-vs-score-loser")
            ?.map { it.normalizedText().toScoreOrNull() }
            .orEmpty()
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
            // The VLR.GG detail document has no stable head-to-head/past-match block. Do not issue
            // additional speculative requests; the public empty lists distinguish that source limit.
            headToHead = emptyList(),
            pastMatches = emptyList(),
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

    private fun Element.requiredEventName(): String {
        return select("div")
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

    private fun String.toMatchStatus(): MatchStatusSource = when (trim().lowercase()) {
        "upcoming" -> MatchStatusSource.UPCOMING
        "live", "in progress" -> MatchStatusSource.LIVE
        "completed", "final" -> MatchStatusSource.COMPLETED
        "postponed", "delayed" -> MatchStatusSource.POSTPONED
        "cancelled", "canceled" -> MatchStatusSource.CANCELLED
        else -> MatchStatusSource.UNAVAILABLE
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

    private fun String.extractMatchIdOrNull(): String? = MATCH_PATH_REGEX.matchEntire(this)?.groupValues?.get(MATCH_ID_GROUP)

    private fun String.extractTeamIdOrNull(): String? = TEAM_PATH_REGEX.matchEntire(this)?.groupValues?.get(MATCH_ID_GROUP)

    private fun String.extractEventIdOrNull(): String? = EVENT_PATH_REGEX.matchEntire(this)?.groupValues?.get(MATCH_ID_GROUP)

    private inline fun <T> parse(upstreamUrl: Url, block: () -> T): T = try {
        block()
    } catch (failure: SourceParsingFailure) {
        throw failure
    } catch (failure: Exception) {
        throw SourceParsingFailure(upstreamUrl, failure)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val MATCH_PATH_REGEX = Regex("/([1-9]\\d{0,9})(?:/[^?#]+)?")
        val TEAM_PATH_REGEX = Regex("/team/([1-9]\\d{0,9})(?:/[^?#]+)?")
        val EVENT_PATH_REGEX = Regex("/event/([1-9]\\d{0,9})(?:/[^?#]+)?")
        val UPSTREAM_UTC_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val REQUIRED_TEAM_COUNT = 2
        const val HOME_TEAM_INDEX = 0
        const val AWAY_TEAM_INDEX = 1
        const val MATCH_ID_GROUP = 1
        const val ALL_MAPS_GAME_ID = "all"
    }
}

private fun String?.orNullIfBlank(): String? = this?.trimmedOrNull()

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun sourceStructureError(message: String): Nothing = throw IllegalStateException(message)

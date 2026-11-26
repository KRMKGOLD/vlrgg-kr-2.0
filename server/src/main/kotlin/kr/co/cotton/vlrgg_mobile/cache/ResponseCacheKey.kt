package kr.co.cotton.vlrgg_mobile.cache

/** Fixed route identifiers used in keys; a raw request path or query is never part of a key or a log. */
internal enum class ResponseCacheRoute(val label: String) {
    MATCHES_UPCOMING("matches-upcoming"),
    MATCHES_RESULTS("matches-results"),
    MATCH_DETAIL("match-detail"),
    EVENTS_LIST("events-list"),
    EVENT_DETAIL("event-detail"),
    EVENT_MATCHES("event-matches"),
    EVENT_NEWS("event-news"),
    EVENT_STATS("event-stats"),
    TEAM_DETAIL("team-detail"),
    NEWS_LIST("news-list"),
    NEWS_ARTICLE("news-article"),
    PLAYER_DETAIL("player-detail"),
    SERIES_DETAIL("series-detail"),
}

/** TTLs in seconds. The policy cap is five minutes; [ResponseCache] rejects anything longer. */
internal object ResponseCacheTtl {
    const val LIVE_SECONDS = 30
    const val UPCOMING_FIRST_PAGE_SECONDS = 30
    const val RESULTS_FIRST_PAGE_SECONDS = 120
    const val DEFAULT_SECONDS = 300
    const val SOURCE_PARSING_FAILURE_SECONDS = 60
    const val MAX_SECONDS = 300
}

private val IDENTIFIER_PART_REGEX = Regex("[A-Za-z0-9._-]{1,128}")

/**
 * `{route label}:{request identifier}`. Identifier parts are already validated and normalized by the route
 * (an omitted page is page 1), and the separator cannot appear inside any part, so keys of different routes or
 * identifiers never collide. Keys are ASCII, so their length is their byte size.
 */
internal class ResponseCacheKey private constructor(val route: ResponseCacheRoute, val value: String) {
    override fun toString(): String = "ResponseCacheKey(${route.label})"

    companion object {
        fun of(route: ResponseCacheRoute, identifier: List<String>): ResponseCacheKey {
            require(identifier.isNotEmpty() && identifier.all(IDENTIFIER_PART_REGEX::matches)) {
                "Response cache identifiers must be validated route values."
            }
            return ResponseCacheKey(route, (listOf(route.label) + identifier).joinToString(KEY_SEPARATOR))
        }

        private const val KEY_SEPARATOR = ":"
    }
}

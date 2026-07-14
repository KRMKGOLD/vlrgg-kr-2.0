package kr.co.cotton.vlrgg_mobile.feature.matches

internal fun fixtureHtml(name: String): String = checkNotNull(
    MatchesTestFixtures::class.java.getResource("/fixtures/matches/$name"),
) { "Missing fixture: $name" }.readText()

private object MatchesTestFixtures

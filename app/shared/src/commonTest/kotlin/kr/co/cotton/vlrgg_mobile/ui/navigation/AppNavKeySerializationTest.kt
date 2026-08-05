package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppNavKeySerializationTest {
    private val json = Json {
        classDiscriminator = "type"
    }

    private val backStackJson = Json {
        classDiscriminator = "type"
        serializersModule = appNavKeySavedStateConfiguration.serializersModule
    }

    @Test
    fun singletonRootsAndSearchRoundTripWithTheirConcreteTypes() {
        val keys = rootNavKeys + Search

        keys.forEach { key ->
            val decoded = roundTrip(key)

            assertEquals(key, decoded)
            assertEquals(key::class, decoded::class)
        }
    }

    @Test
    fun detailKeysRoundTripWithCanonicalIdentifiers() {
        val keys = listOf<AppNavKey>(
            NewsDetail(articleId = "12345", slug = "champions-seoul-grand-final"),
            MatchDetail(matchId = "67890"),
            EventDetail(eventId = "1189"),
            TeamDetail(teamId = "1001"),
            PlayerDetail(playerId = "2002"),
            SeriesDetail(seriesId = "3003"),
        )

        keys.forEach { key ->
            val decoded = roundTrip(key)

            assertEquals(key, decoded)
            assertEquals(key::class, decoded::class)
        }

        val news = assertIs<NewsDetail>(roundTrip(keys.first()))
        assertEquals("12345", news.articleId)
        assertEquals("champions-seoul-grand-final", news.slug)
    }

    @Test
    fun serializedKeySchemaContainsOnlyStableIdentityFields() {
        val expectedIdentityFields = mapOf<AppNavKey, Set<String>>(
            NewsRoot to emptySet(),
            MatchesRoot to emptySet(),
            MyPageRoot to emptySet(),
            EventsRoot to emptySet(),
            AboutRoot to emptySet(),
            Search to emptySet(),
            NewsDetail("12345", "champions-seoul-grand-final") to setOf("articleId", "slug"),
            MatchDetail("67890") to setOf("matchId"),
            EventDetail("1189") to setOf("eventId"),
            TeamDetail("1001") to setOf("teamId"),
            PlayerDetail("2002") to setOf("playerId"),
            SeriesDetail("3003") to setOf("seriesId"),
        )

        expectedIdentityFields.forEach { (key, expectedFields) ->
            val serializedFields = json
                .parseToJsonElement(json.encodeToString(AppNavKey.serializer(), key))
                .jsonObject
                .keys - "type"

            assertEquals(expectedFields, serializedFields, "Unexpected schema for ${key::class}")
        }
    }

    @Test
    fun rootOrderAndRootMembershipAreExact() {
        val allKeys = listOf<AppNavKey>(
            NewsRoot,
            MatchesRoot,
            MyPageRoot,
            EventsRoot,
            AboutRoot,
            Search,
            NewsDetail("1", "news"),
            MatchDetail("1"),
            EventDetail("1"),
            TeamDetail("1"),
            PlayerDetail("1"),
            SeriesDetail("1"),
        )

        assertEquals(
            listOf(NewsRoot, MatchesRoot, MyPageRoot, EventsRoot, AboutRoot),
            rootNavKeys,
        )
        assertEquals(rootNavKeys, allKeys.filterIsInstance<RootNavKey>())
    }

    @Test
    fun navigation3BackStackRoundTripsWithTheCommonSavedStateSerializersModule() {
        val original = NavBackStack<NavKey>(
            EventsRoot,
            Search,
            TeamDetail(teamId = "1001"),
        )
        val serializer = NavBackStackSerializer<NavKey>()

        val restored = backStackJson.decodeFromString(
            serializer,
            backStackJson.encodeToString(serializer, original),
        )

        assertEquals(original.toList(), restored.toList())
        assertEquals(EventsRoot, AppNavigationState(restored).selectedRoot)
        assertEquals(
            listOf(Search, TeamDetail(teamId = "1001")),
            AppNavigationState(restored).overlay,
        )
    }

    private fun roundTrip(key: AppNavKey): AppNavKey = json.decodeFromString(
        AppNavKey.serializer(),
        json.encodeToString(AppNavKey.serializer(), key),
    )
}

package kr.co.cotton.vlrgg_mobile.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestinationDescriptorTest {
    @Test
    fun rootsExposeFixedUniqueMetadata() {
        assertEquals(listOf(0, 1, 2, 3, 4), rootNavKeys.map { it.destinationDescriptor.rootOrder })

        rootNavKeys.forEach { root ->
            val descriptor = root.destinationDescriptor

            assertTrue(descriptor.isRoot)
            assertTrue(descriptor.showBottomBar)
            assertTrue(descriptor.searchAvailable)
            assertNotNull(descriptor.rootOrder)
        }
    }

    @Test
    fun pushedDestinationsShareTheHostPolicy() {
        pushedKeys.forEach { destination ->
            val descriptor = destination.destinationDescriptor

            assertFalse(descriptor.isRoot)
            assertFalse(descriptor.showBottomBar)
            assertFalse(descriptor.searchAvailable)
            assertNull(descriptor.rootOrder)
        }
    }

    @Test
    fun rootsWithViewModelsRequireAnEntryScope() {
        declaredKeys.forEach { destination ->
            assertEquals(
                destination === NewsRoot ||
                    destination === MatchesRoot ||
                    destination === MyPageRoot,
                destination.destinationDescriptor.requiresEntryScope,
                "Unexpected entry scope policy for ${destination::class}",
            )
        }
    }

    @Test
    fun everyDeclaredKeyHasCompleteUniqueDescriptorMetadataAndContentPolicy() {
        val descriptors = declaredKeys.map { destination ->
            assertNotNull(contentPolicy(destination))
            destination.destinationDescriptor
        }

        assertEquals(12, descriptors.size)
        assertTrue(descriptors.all { it.marker.isNotBlank() && it.title.isNotBlank() })
        assertEquals(descriptors.size, descriptors.map { it.marker }.toSet().size)
    }

    private fun contentPolicy(destination: AppNavKey): ContentPolicy = when (destination) {
        EventsRoot,
        AboutRoot,
        -> ContentPolicy.ROOT_PLACEHOLDER

        NewsRoot -> ContentPolicy.NEWS
        MatchesRoot -> ContentPolicy.MATCHES
        MyPageRoot -> ContentPolicy.MY_PAGE
        Search -> ContentPolicy.SEARCH
        is NewsDetail,
        is MatchDetail,
        is EventDetail,
        is TeamDetail,
        is PlayerDetail,
        is SeriesDetail,
        -> ContentPolicy.DETAIL
    }

    private enum class ContentPolicy {
        ROOT_PLACEHOLDER,
        NEWS,
        MATCHES,
        MY_PAGE,
        SEARCH,
        DETAIL,
    }

    private companion object {
        val pushedKeys = listOf<AppNavKey>(
            Search,
            NewsDetail(articleId = "1", slug = "news"),
            MatchDetail(matchId = "1"),
            EventDetail(eventId = "1"),
            TeamDetail(teamId = "1"),
            PlayerDetail(playerId = "1"),
            SeriesDetail(seriesId = "1"),
        )

        val declaredKeys: List<AppNavKey> = rootNavKeys + pushedKeys
    }
}

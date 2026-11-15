package kr.co.cotton.vlrgg_mobile.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DestinationDescriptorTest {
    @Test
    fun rootsExposeFixedUniqueMetadata() {
        rootNavKeys.forEach { root ->
            val descriptor = root.destinationDescriptor

            assertTrue(descriptor.showBottomBar)
        }
    }

    @Test
    fun pushedDestinationsShareTheHostPolicy() {
        pushedKeys.forEach { destination ->
            val descriptor = destination.destinationDescriptor

            assertFalse(descriptor.showBottomBar)
        }
    }

    @Test
    fun everyDeclaredKeyHasCompleteUniqueDescriptorMetadataAndContentPolicy() {
        val descriptors = declaredKeys.map { destination ->
            assertNotNull(contentPolicy(destination))
            destination.destinationDescriptor
        }

        assertEquals(13, descriptors.size)
        assertTrue(descriptors.all { it.marker.isNotBlank() && it.title.isNotBlank() })
        assertEquals(descriptors.size, descriptors.map { it.marker }.toSet().size)
    }

    private fun contentPolicy(destination: AppNavKey): ContentPolicy = when (destination) {
        AboutRoot,
        -> ContentPolicy.ROOT_PLACEHOLDER

        NewsRoot -> ContentPolicy.NEWS
        MatchesRoot -> ContentPolicy.MATCHES
        MyPageRoot -> ContentPolicy.MY_PAGE
        EventsRoot -> ContentPolicy.EVENTS
        Search -> ContentPolicy.SEARCH
        OpenSourceLicenses,
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
        EVENTS,
        SEARCH,
        DETAIL,
    }

    private companion object {
        val pushedKeys = listOf<AppNavKey>(
            Search,
            OpenSourceLicenses,
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

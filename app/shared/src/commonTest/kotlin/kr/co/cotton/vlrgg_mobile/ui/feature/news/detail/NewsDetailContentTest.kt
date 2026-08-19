package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewsDetailContentTest {

    @Test
    fun routableLinksUseOnlyTheirFirstReferenceSegmentAsDestinationIdentity() {
        val team = NewsArticleInline.Link(
            label = "Sentinels",
            kind = NewsLinkKind.TEAM,
            reference = "2/sentinels",
        )
        val player = NewsArticleInline.Link(
            label = "TenZ",
            kind = NewsLinkKind.PLAYER,
            reference = "/3/tenz/",
        )

        assertEquals(NewsDetailLinkTarget.Team, team.routableTarget())
        assertEquals("2", team.navigationIdOrNull())
        assertEquals(NewsDetailLinkTarget.Player, player.routableTarget())
        assertEquals("3", player.navigationIdOrNull())
    }

    @Test
    fun unsupportedLinksNeverProduceARoutingTarget() {
        NewsLinkKind.entries
            .filterNot { it == NewsLinkKind.TEAM || it == NewsLinkKind.PLAYER }
            .forEach { kind ->
                val link = NewsArticleInline.Link(
                    label = "unsupported",
                    kind = kind,
                    reference = "99/not-routable",
                )

                assertNull(link.routableTarget(), "Unexpected route target for $kind")
            }

        val missingReference = NewsArticleInline.Link(
            label = "missing",
            kind = NewsLinkKind.TEAM,
            reference = " / ",
        )
        assertNull(missingReference.navigationIdOrNull())
    }

    @Test
    fun listMarkersPreserveOrderedAndUnorderedMeaning() {
        assertEquals("1.", newsDetailListMarker(ordered = true, index = 0))
        assertEquals("3.", newsDetailListMarker(ordered = true, index = 2))
        assertEquals("•", newsDetailListMarker(ordered = false, index = 9))
    }
}

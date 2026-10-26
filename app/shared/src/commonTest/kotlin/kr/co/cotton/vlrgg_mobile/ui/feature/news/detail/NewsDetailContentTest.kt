package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticle
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsArticleInline
import kr.co.cotton.vlrgg_mobile.domain.model.news.NewsLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

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

    @Test
    fun publishedLabelUsesDeviceTimeZoneAndFallsBackToSourceText() {
        val article = NewsArticle(
            articleId = "101",
            slug = "champions-run",
            title = "Champions run",
            author = "Reporter",
            publishedAt = "16 hours ago",
            publishedAtUtc = Instant.parse("2026-10-25T15:09:14Z"),
            blocks = emptyList(),
        )

        assertEquals("2026년 10월 26일 오전 12:09", article.publishedLabel(TimeZone.of("Asia/Seoul")))
        assertEquals("2026년 10월 25일 오전 11:09", article.publishedLabel(TimeZone.of("America/New_York")))
        assertEquals("16 hours ago", article.copy(publishedAtUtc = null).publishedLabel(TimeZone.of("Asia/Seoul")))
    }
}

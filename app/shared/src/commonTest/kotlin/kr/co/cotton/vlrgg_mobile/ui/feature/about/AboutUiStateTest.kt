package kr.co.cotton.vlrgg_mobile.ui.feature.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AboutUiStateTest {
    @Test
    fun versionLabelPrefixesOnlyAvailableBuildVersions() {
        assertEquals("v2.0.4", aboutUiState("2.0.4").versionLabel)
        assertEquals(ABOUT_VERSION_UNAVAILABLE, aboutUiState(" ").versionLabel)
        assertEquals(ABOUT_VERSION_UNAVAILABLE, aboutUiState(null).versionLabel)
    }

    @Test
    fun sourceOpenSuccessClearsStaleFeedback() {
        val state = aboutUiState("2.0.4").afterSourceOpen(opened = false)

        assertNull(state.afterSourceOpen(opened = true).feedback)
    }

    @Test
    fun sourceOpenFailureKeepsContentAndCopyRecoveryReplacesFailure() {
        val failed = aboutUiState("2.0.4").afterSourceOpen(opened = false)

        assertEquals(AboutFeedback.SourceLinkError, failed.feedback)
        assertEquals("v2.0.4", failed.versionLabel)
        assertEquals(AboutFeedback.SourceLinkCopied, failed.afterSourceCopy().feedback)
    }

    @Test
    fun dismissIsIdempotentAfterCopyFeedback() {
        val copied = aboutUiState("2.0.4").afterSourceCopy()

        assertNull(copied.dismissFeedback().feedback)
        assertNull(copied.dismissFeedback().dismissFeedback().feedback)
    }
}

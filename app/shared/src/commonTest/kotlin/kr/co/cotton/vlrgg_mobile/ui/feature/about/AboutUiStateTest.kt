package kr.co.cotton.vlrgg_mobile.ui.feature.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AboutUiStateTest {
    @Test
    fun versionLabelPrefixesOnlyAvailableBuildVersions() {
        assertEquals("v2.0.4", aboutUiState("2.0.4").versionLabel)
        assertNull(aboutUiState(" ").versionLabel)
        assertNull(aboutUiState(null).versionLabel)
    }

    @Test
    fun aboutLinksUseExactPublicDestinationsInDisplayOrder() {
        assertEquals(
            listOf(
                "https://github.com/KRMKGOLD/vlrgg-kr-2.0",
                "https://github.com/KRMKGOLD/vlrgg-kr-2.0/blob/main/docs/privacy-policy.md",
                "https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues",
            ),
            AboutLink.entries.map(AboutLink::url),
        )
    }

    @Test
    fun linkOpenSuccessClearsStaleFeedback() {
        val state = aboutUiState("2.0.4").afterLinkOpen(AboutLink.SourceCode, opened = false)

        assertNull(state.afterLinkOpen(AboutLink.Contact, opened = true).failedLink)
    }

    @Test
    fun linkOpenFailureKeepsContentWithoutAddingARecoveryAction() {
        AboutLink.entries.forEach { link ->
            val failed = aboutUiState("2.0.4").afterLinkOpen(link, opened = false)

            assertEquals(link, failed.failedLink)
            assertEquals("v2.0.4", failed.versionLabel)
            assertEquals(1L, failed.linkErrorId)
        }
    }

    @Test
    fun eachLinkOpenFailureGetsANewFeedbackIdentifier() {
        val firstFailure = aboutUiState("2.0.4").afterLinkOpen(AboutLink.SourceCode, opened = false)
        val secondFailure = firstFailure.afterLinkOpen(AboutLink.PrivacyPolicy, opened = false)

        assertEquals(2L, secondFailure.linkErrorId)
        assertEquals(AboutLink.PrivacyPolicy, secondFailure.failedLink)
    }

    @Test
    fun dismissIsIdempotentAfterFailureFeedback() {
        val failed = aboutUiState("2.0.4").afterLinkOpen(AboutLink.SourceCode, opened = false)

        assertNull(failed.dismissFeedback().failedLink)
        assertNull(failed.dismissFeedback().dismissFeedback().failedLink)
    }

    @Test
    fun linkOpenCallbackGateRejectsDisposedAndSupersededAttempts() {
        val gate = AboutLinkOpenCallbackGate()

        val firstAttempt = gate.beginAttempt()
        val secondAttempt = gate.beginAttempt()
        assertEquals(false, gate.accepts(firstAttempt))
        assertEquals(true, gate.accepts(secondAttempt))

        gate.dispose()
        assertEquals(false, gate.accepts(secondAttempt))
    }

    @Test
    fun viewModelOwnsVersionAndLinkOpenFeedbackTransitions() {
        val viewModel = AboutViewModel()

        viewModel.updateBuildVersion("2.0.4")
        viewModel.onLinkOpenResult(AboutLink.SourceCode, opened = false)
        assertEquals("v2.0.4", viewModel.uiState.value.versionLabel)
        assertEquals(AboutLink.SourceCode, viewModel.uiState.value.failedLink)

        viewModel.onLinkOpenResult(AboutLink.SourceCode, opened = true)
        assertNull(viewModel.uiState.value.failedLink)

        viewModel.onLinkOpenResult(AboutLink.Contact, opened = false)
        assertEquals(AboutLink.Contact, viewModel.uiState.value.failedLink)
        viewModel.dismissFeedback()
        assertNull(viewModel.uiState.value.failedLink)
    }
}

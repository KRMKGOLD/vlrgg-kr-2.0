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
    fun sourceOpenSuccessClearsStaleFeedback() {
        val state = aboutUiState("2.0.4").afterSourceOpen(opened = false)

        assertNull(state.afterSourceOpen(opened = true).feedback)
    }

    @Test
    fun sourceOpenFailureKeepsContentWithoutAddingARecoveryAction() {
        val failed = aboutUiState("2.0.4").afterSourceOpen(opened = false)

        assertEquals(AboutFeedback.SourceLinkError, failed.feedback)
        assertEquals("v2.0.4", failed.versionLabel)
        assertEquals(1L, failed.sourceLinkErrorId)
    }

    @Test
    fun eachSourceOpenFailureGetsANewFeedbackIdentifier() {
        val firstFailure = aboutUiState("2.0.4").afterSourceOpen(opened = false)
        val secondFailure = firstFailure.afterSourceOpen(opened = false)

        assertEquals(2L, secondFailure.sourceLinkErrorId)
        assertEquals(AboutFeedback.SourceLinkError, secondFailure.feedback)
    }

    @Test
    fun dismissIsIdempotentAfterFailureFeedback() {
        val failed = aboutUiState("2.0.4").afterSourceOpen(opened = false)

        assertNull(failed.dismissFeedback().feedback)
        assertNull(failed.dismissFeedback().dismissFeedback().feedback)
    }

    @Test
    fun sourceOpenCallbackGateRejectsDisposedAndSupersededAttempts() {
        val gate = AboutSourceOpenCallbackGate()

        val firstAttempt = gate.beginAttempt()
        val secondAttempt = gate.beginAttempt()
        assertEquals(false, gate.accepts(firstAttempt))
        assertEquals(true, gate.accepts(secondAttempt))

        gate.dispose()
        assertEquals(false, gate.accepts(secondAttempt))
    }

    @Test
    fun viewModelOwnsVersionAndSourceOpenFeedbackTransitions() {
        val viewModel = AboutViewModel()

        viewModel.updateBuildVersion("2.0.4")
        viewModel.onSourceOpenResult(opened = false)
        assertEquals("v2.0.4", viewModel.uiState.value.versionLabel)
        assertEquals(AboutFeedback.SourceLinkError, viewModel.uiState.value.feedback)

        viewModel.onSourceOpenResult(opened = true)
        assertNull(viewModel.uiState.value.feedback)

        viewModel.onSourceOpenResult(opened = false)
        assertEquals(AboutFeedback.SourceLinkError, viewModel.uiState.value.feedback)
        viewModel.dismissFeedback()
        assertNull(viewModel.uiState.value.feedback)
    }
}

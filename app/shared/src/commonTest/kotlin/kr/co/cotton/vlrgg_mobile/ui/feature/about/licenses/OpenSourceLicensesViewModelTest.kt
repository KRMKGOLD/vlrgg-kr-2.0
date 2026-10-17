package kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing of generated definitions is covered by the iOS and Android device tests: AboutLibraries
 * parses with `org.json` on Android, which is only a stub in JVM host tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenSourceLicensesViewModelTest {

    @Test
    fun loadReadsDefinitionsOncePerNavigationEntry() = runViewModelTest { dispatcher ->
        var reads = 0
        val viewModel = OpenSourceLicensesViewModel(parseDispatcher = dispatcher)

        assertEquals(OpenSourceLicensesUiState(), viewModel.uiState.value)
        viewModel.load { reads += 1; error("missing resource") }
        viewModel.load { reads += 1; error("missing resource") }
        advanceUntilIdle()

        assertEquals(1, reads)
    }

    @Test
    fun unreadableOrEmptyDefinitionsBecomeALoadFailure() = runViewModelTest { dispatcher ->
        listOf<suspend () -> String>(
            { error("missing resource") },
            { "not json" },
            { """{"libraries":[],"licenses":{}}""" },
        ).forEach { read ->
            val viewModel = OpenSourceLicensesViewModel(parseDispatcher = dispatcher)

            viewModel.load(read)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isLoadFailed)
            assertNull(viewModel.uiState.value.libraries)
        }
    }

    private fun runViewModelTest(
        block: suspend TestScope.(TestDispatcher) -> Unit,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block(dispatcher)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

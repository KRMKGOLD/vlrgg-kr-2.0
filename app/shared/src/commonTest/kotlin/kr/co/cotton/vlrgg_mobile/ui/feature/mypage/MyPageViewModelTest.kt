package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MyPageViewModelTest {
    @Test
    fun newViewModelSynchronouslyExposesNeutralStateFlow() {
        val viewModel = MyPageViewModel()

        assertIs<StateFlow<MyPageUiState>>(viewModel.uiState)
        assertEquals(MyPageUiState(), viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun constructionNeedsNoRepositoryNetworkOrPersistenceDependency() {
        val first = MyPageViewModel()
        val second = MyPageViewModel()

        assertEquals(MyPageUiState(), first.uiState.value)
        assertEquals(MyPageUiState(), second.uiState.value)
    }
}

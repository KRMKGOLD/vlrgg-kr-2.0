package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Inject
class MyPageViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MyPageUiState())

    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()
}

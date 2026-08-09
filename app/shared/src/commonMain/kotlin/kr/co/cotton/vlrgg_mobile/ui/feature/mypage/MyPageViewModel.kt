package kr.co.cotton.vlrgg_mobile.ui.feature.mypage

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MyPageViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MyPageUiState())

    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()
}

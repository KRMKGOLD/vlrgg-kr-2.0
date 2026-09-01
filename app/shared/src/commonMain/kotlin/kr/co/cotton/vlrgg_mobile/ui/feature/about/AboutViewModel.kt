package kr.co.cotton.vlrgg_mobile.ui.feature.about

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class AboutViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(aboutUiState(buildVersion = null))

    internal val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    fun updateBuildVersion(buildVersion: String?) {
        val versionLabel = aboutUiState(buildVersion).versionLabel
        _uiState.update { current -> current.copy(versionLabel = versionLabel) }
    }

    fun onSourceOpenResult(opened: Boolean) {
        _uiState.update { current -> current.afterSourceOpen(opened) }
    }

    fun onSourceCopyResult(copied: Boolean) {
        _uiState.update { current ->
            if (copied) current.afterSourceCopy() else current.afterSourceOpen(opened = false)
        }
    }

    fun dismissFeedback() {
        _uiState.update(AboutUiState::dismissFeedback)
    }
}

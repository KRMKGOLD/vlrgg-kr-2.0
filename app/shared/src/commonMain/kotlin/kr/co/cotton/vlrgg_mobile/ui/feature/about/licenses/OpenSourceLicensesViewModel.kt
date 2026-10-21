package kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class OpenSourceLicensesViewModel(
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OpenSourceLicensesUiState())

    internal val uiState: StateFlow<OpenSourceLicensesUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    /** Loads the build-time license definitions once per navigation entry. */
    fun load(readLicenseDefinitions: suspend () -> String) {
        if (loadJob != null) return
        loadJob = viewModelScope.launch {
            val libraries = try {
                withContext(parseDispatcher) {
                    Libs.Builder().withJson(readLicenseDefinitions()).build()
                }.takeIf { it.libraries.isNotEmpty() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            _uiState.value = OpenSourceLicensesUiState(
                libraries = libraries,
                isLoadFailed = libraries == null,
            )
        }
    }
}

package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository

@AssistedInject
class NewsDetailViewModel(
    private val newsRepository: NewsRepository,
    @Assisted private val articleId: String,
    @Assisted private val slug: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewsDetailUiState())
    val uiState: StateFlow<NewsDetailUiState> = _uiState.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted articleId: String,
            @Assisted slug: String,
        ): NewsDetailViewModel
    }
}

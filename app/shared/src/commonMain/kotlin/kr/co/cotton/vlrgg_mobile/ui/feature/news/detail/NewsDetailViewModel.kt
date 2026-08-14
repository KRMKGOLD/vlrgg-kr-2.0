package kr.co.cotton.vlrgg_mobile.ui.feature.news.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.onFailure
import kr.co.cotton.vlrgg_mobile.domain.onSuccess
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository

@AssistedInject
class NewsDetailViewModel(
    private val newsRepository: NewsRepository,
    @Assisted private val articleId: String,
    @Assisted private val slug: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewsDetailUiState())
    val uiState: StateFlow<NewsDetailUiState> = _uiState.asStateFlow()

    init {
        fetchNewsDetail()
    }

    private fun fetchNewsDetail() = viewModelScope.launch {
        newsRepository.getNewsArticle(articleId, slug).onSuccess { article ->
            _uiState.value = NewsDetailUiState(
                contentState = if (article.blocks.isEmpty()) {
                    NewsDetailContentState.Empty(article)
                } else {
                    NewsDetailContentState.Content(article)
                },
            )
        }.onFailure {
            _uiState.value = NewsDetailUiState(
                contentState = NewsDetailContentState.Error,
            )
        }
    }

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

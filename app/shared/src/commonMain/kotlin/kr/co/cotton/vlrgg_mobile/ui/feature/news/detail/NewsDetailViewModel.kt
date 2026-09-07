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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryState
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@AssistedInject
class NewsDetailViewModel(
    private val newsRepository: NewsRepository,
    @Assisted private val articleId: String,
    @Assisted private val slug: String,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewsDetailUiState())
    val uiState: StateFlow<NewsDetailUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null

    init {
        fetchNewsDetail()
    }

    fun retry() {
        if (requestJob?.isActive == true) return
        if (isBusyCooldown()) return
        if (_uiState.value.contentState != NewsDetailContentState.Error) return
        _uiState.value = _uiState.value.copy(contentState = NewsDetailContentState.Loading, busyRetry = null)
        fetchNewsDetail()
    }

    fun retryBusy() {
        if (requestJob?.isActive == true) return
        val busy = _uiState.value.busyRetry ?: return
        if (busy.operationId != OPERATION_ID || !busy.canRetry()) return
        _uiState.value = _uiState.value.copy(contentState = NewsDetailContentState.Loading, busyRetry = null)
        fetchNewsDetail()
    }

    fun dismissBusy() { _uiState.value = _uiState.value.copy(busyRetry = _uiState.value.busyRetry?.dismiss()) }

    private fun fetchNewsDetail() {
        if (requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            when (val result = newsRepository.getNewsArticle(articleId, slug)) {
                is AppResult.Success -> {
                    val article = result.data
                    _uiState.value = NewsDetailUiState(
                        contentState = if (article.blocks.isEmpty()) {
                            NewsDetailContentState.Empty(article)
                        } else {
                            NewsDetailContentState.Content(article)
                        },
                    )
                }
                AppResult.Failure -> {
                    _uiState.value = NewsDetailUiState(
                        contentState = NewsDetailContentState.Error,
                    )
                }
                is AppResult.Busy -> _uiState.value = _uiState.value.copy(
                    contentState = NewsDetailContentState.Error,
                    busyRetry = busyRetryStateFactory.create(OPERATION_ID, result.retryDelay),
                )
            }
        }
    }

    private fun isBusyCooldown(): Boolean = _uiState.value.busyRetry
        ?.takeIf { it.operationId == OPERATION_ID }
        ?.canRetry() == false

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted articleId: String,
            @Assisted slug: String,
        ): NewsDetailViewModel
    }

    private companion object { const val OPERATION_ID = "news-detail" }
}

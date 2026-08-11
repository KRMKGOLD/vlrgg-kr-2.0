package kr.co.cotton.vlrgg_mobile.ui.feature.news.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.co.cotton.vlrgg_mobile.domain.onFailure
import kr.co.cotton.vlrgg_mobile.domain.onSuccess
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class NewsListViewModel(
    private val newsRepository: NewsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewsListUiState())
    val uiState: StateFlow<NewsListUiState> = _uiState.asStateFlow()

    private var nextPage: Int? = null

    private var firstPageJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        firstPageJob = loadNewsPage(page = 1)
    }

    fun retryInitial() {
        if (_uiState.value.contentState != NewsListContentState.Error) return

        _uiState.value = NewsListUiState()
        nextPage = null
        firstPageJob = loadNewsPage(page = 1)
    }

    private fun loadNewsPage(page: Int) = viewModelScope.launch {
        newsRepository.getNewsPage(page)
            .onSuccess { newsPage ->
                val existingItems = (uiState.value.contentState as? NewsListContentState.Content)
                    ?.items.orEmpty()

                val items = (existingItems + newsPage.items)
                    .distinctBy {
                        it.articleId to it.slug
                    }

                nextPage = newsPage.nextPage
                _uiState.value = NewsListUiState(
                    contentState = if (items.isEmpty()) {
                        NewsListContentState.Empty
                    } else {
                        NewsListContentState.Content(items)
                    },
                )
            }
            .onFailure {
                val state = uiState.value

                _uiState.value = if (state.contentState is NewsListContentState.Content) {
                    state.copy(
                        isLoadingMore = false,
                        hasPaginationError = true,
                    )
                } else {
                    NewsListUiState(
                        contentState = NewsListContentState.Error,
                    )
                }
            }
    }

    fun loadMore() {
        val state = _uiState.value
        val requestedPage = nextPage ?: return

        if (state.contentState !is NewsListContentState.Content) return
        if (state.isLoadingMore || state.isRefreshing) return

        _uiState.value = state.copy(
            isLoadingMore = true,
            hasPaginationError = false,
        )

        loadMoreJob = loadNewsPage(requestedPage)
    }

    fun retryLoadMore() {
        if (uiState.value.hasPaginationError) loadMore()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return

        firstPageJob?.cancel()
        firstPageJob = null
        loadMoreJob?.cancel()
        loadMoreJob = null
        nextPage = null

        _uiState.value = NewsListUiState(
            contentState = NewsListContentState.Loading,
            isRefreshing = true,
        )

        firstPageJob = loadNewsPage(page = 1)
    }
}

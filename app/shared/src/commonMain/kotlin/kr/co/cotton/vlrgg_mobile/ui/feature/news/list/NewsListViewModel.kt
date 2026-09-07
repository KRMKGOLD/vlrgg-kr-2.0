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
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository
import kr.co.cotton.vlrgg_mobile.ui.component.BusyRetryStateFactory

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class NewsListViewModel(
    private val newsRepository: NewsRepository,
    private val busyRetryStateFactory: BusyRetryStateFactory = BusyRetryStateFactory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewsListUiState())
    val uiState: StateFlow<NewsListUiState> = _uiState.asStateFlow()

    private var nextPage: Int? = null
    private var requestGeneration = 0
    private var refreshContent: NewsListContentState? = null

    private var firstPageJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        firstPageJob = loadNewsPage(page = 1)
    }

    fun retryInitial() {
        if (isBusyCooldown(operationId(1))) return
        if (_uiState.value.contentState != NewsListContentState.Error) return

        firstPageJob?.cancel()
        loadMoreJob?.cancel()
        requestGeneration += 1
        _uiState.value = NewsListUiState()
        nextPage = null
        firstPageJob = loadNewsPage(page = 1)
    }

    fun retryBusy() {
        val busy = _uiState.value.busyRetry ?: return
        if (!busy.canRetry()) return
        val page = busy.operationId.removePrefix(OPERATION_PREFIX).toIntOrNull() ?: return
        if (page == 1 && firstPageJob?.isActive == true) return
        if (page != 1 && loadMoreJob?.isActive == true) return
        if (page != 1 && _uiState.value.contentState !is NewsListContentState.Content) return
        _uiState.value = _uiState.value.copy(busyRetry = null)
        if (page == 1) {
            nextPage = null
            if (_uiState.value.contentState == NewsListContentState.Error) {
                _uiState.value = _uiState.value.copy(contentState = NewsListContentState.Loading)
            } else {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
            }
            firstPageJob = loadNewsPage(page)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoadingMore = true,
                hasPaginationError = false,
            )
            loadMoreJob = loadNewsPage(page)
        }
    }

    fun dismissBusy() {
        val busy = _uiState.value.busyRetry ?: return
        val page = busy.operationId.removePrefix(OPERATION_PREFIX).toIntOrNull()
        _uiState.value = _uiState.value.copy(busyRetry = busy.dismiss())
        if (page != null && page != 1) {
            _uiState.value = _uiState.value.copy(hasPaginationError = true)
        }
    }

    private fun loadNewsPage(
        page: Int,
        generation: Int = requestGeneration,
    ) = viewModelScope.launch {
        when (val result = newsRepository.getNewsPage(page)) {
            is AppResult.Success -> {
                if (generation != requestGeneration) return@launch
                val newsPage = result.data
                val existingItems = if (page == 1) emptyList() else {
                    (uiState.value.contentState as? NewsListContentState.Content)?.items.orEmpty()
                }

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
                refreshContent = null
            }
            AppResult.Failure -> {
                if (generation != requestGeneration) return@launch
                val state = uiState.value

                _uiState.value = if (page == 1 && state.contentState is NewsListContentState.Content) {
                    state.copy(
                        isRefreshing = false,
                    )
                } else if (state.contentState is NewsListContentState.Content) {
                    state.copy(
                        isLoadingMore = false,
                        hasPaginationError = true,
                    )
                } else {
                    NewsListUiState(
                        contentState = NewsListContentState.Error,
                    )
                }
                refreshContent = null
            }
            is AppResult.Busy -> {
                if (generation != requestGeneration) return@launch
                val state = _uiState.value
                _uiState.value = if (page == 1 && refreshContent != null) {
                    NewsListUiState(contentState = refreshContent!!, busyRetry = busyRetryStateFactory.create(operationId(page), result.retryDelay))
                } else if (page == 1 && state.contentState is NewsListContentState.Loading) {
                    NewsListUiState(
                        contentState = NewsListContentState.Error,
                        busyRetry = busyRetryStateFactory.create(operationId(page), result.retryDelay),
                    )
                } else {
                    state.copy(isRefreshing = false, isLoadingMore = false, busyRetry = busyRetryStateFactory.create(operationId(page), result.retryDelay))
                }
                refreshContent = null
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val requestedPage = nextPage ?: return

        if (isBusyCooldown(operationId(requestedPage))) return

        if (state.contentState !is NewsListContentState.Content) return
        if (state.isLoadingMore || state.isRefreshing || state.hasPaginationError) return
        if (loadMoreJob?.isActive == true) return

        _uiState.value = state.copy(
            isLoadingMore = true,
            hasPaginationError = false,
        )

        loadMoreJob = loadNewsPage(requestedPage)
    }

    fun retryLoadMore() {
        val state = _uiState.value
        val requestedPage = nextPage ?: return
        if (!state.hasPaginationError || isBusyCooldown(operationId(requestedPage))) return
        _uiState.value = state.copy(hasPaginationError = false, busyRetry = null)
        loadMore()
    }

    fun refresh() {
        if (isBusyCooldown(operationId(1))) return
        if (_uiState.value.isRefreshing) return

        firstPageJob?.cancel()
        firstPageJob = null
        loadMoreJob?.cancel()
        loadMoreJob = null
        nextPage = null
        requestGeneration += 1

        val state = _uiState.value
        refreshContent = state.contentState.takeUnless { it is NewsListContentState.Loading || it is NewsListContentState.Error }
        _uiState.value = NewsListUiState(contentState = NewsListContentState.Loading, isRefreshing = true)

        firstPageJob = loadNewsPage(page = 1)
    }

    private fun isBusyCooldown(operationId: String): Boolean = _uiState.value.busyRetry
        ?.takeIf { it.operationId == operationId }
        ?.canRetry() == false

    private fun operationId(page: Int): String = "$OPERATION_PREFIX$page"

    private companion object { const val OPERATION_PREFIX = "news:page:" }
}

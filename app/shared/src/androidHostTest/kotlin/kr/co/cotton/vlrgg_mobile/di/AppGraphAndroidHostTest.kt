package kr.co.cotton.vlrgg_mobile.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import java.io.File
import kr.co.cotton.vlrgg_mobile.data.local.datastore.createFavoriteDataStore
import kr.co.cotton.vlrgg_mobile.data.repository.FavoriteRepositoryImpl
import kr.co.cotton.vlrgg_mobile.data.repository.PlayerRepositoryImpl
import kr.co.cotton.vlrgg_mobile.data.repository.MatchRepositoryImpl
import kr.co.cotton.vlrgg_mobile.data.repository.TeamRepositoryImpl
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageViewModel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AppGraphAndroidHostTest {
    @Test
    fun appGraphResolvesTeamPlayerAndMatchRepositories() {
        val graph = createTestGraph()

        assertIs<TeamRepositoryImpl>(graph.teamRepository)
        assertIs<PlayerRepositoryImpl>(graph.playerRepository)
        assertIs<MatchRepositoryImpl>(graph.matchRepository)
        assertIs<FavoriteRepositoryImpl>(graph.favoriteRepository)
    }

    @Test
    fun metroGraphResolvesFactoryCreatesMyPageAndRejectsUnknownViewModels() {
        val graph = createTestGraph()
        val factory = graph.metroViewModelFactory

        assertIs<MyPageViewModel>(
            factory.create(MyPageViewModel::class, CreationExtras.Empty),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            factory.create(UnknownViewModel::class, CreationExtras.Empty)
        }
        assertContains(failure.message.orEmpty(), "Unknown model class")
        assertContains(failure.message.orEmpty(), UnknownViewModel::class.simpleName.orEmpty())
    }

    @Test
    fun viewModelProviderScopesMyPageToItsNavigationEntryOwner() {
        val factory = createTestGraph().metroViewModelFactory
        val firstOwner = TestViewModelStoreOwner()
        val secondOwner = TestViewModelStoreOwner()

        val firstEntryInstance = resolveMyPageViewModel(firstOwner, factory)
        assertSame(firstEntryInstance, resolveMyPageViewModel(firstOwner, factory))

        val secondEntryInstance = resolveMyPageViewModel(secondOwner, factory)
        assertNotSame(firstEntryInstance, secondEntryInstance)
        assertSame(secondEntryInstance, resolveMyPageViewModel(secondOwner, factory))

        firstOwner.viewModelStore.clear()
        val reenteredFirstEntryInstance = resolveMyPageViewModel(firstOwner, factory)
        assertNotSame(firstEntryInstance, reenteredFirstEntryInstance)
        assertSame(reenteredFirstEntryInstance, resolveMyPageViewModel(firstOwner, factory))
    }

    private fun resolveMyPageViewModel(
        owner: ViewModelStoreOwner,
        factory: ViewModelProvider.Factory,
    ): MyPageViewModel = ViewModelProvider.create(owner, factory)[MyPageViewModel::class]

    private fun createTestGraph() = createAppGraph(
        apiBaseUrl = TEST_API_BASE_URL,
        favoriteDataStore = createFavoriteDataStore(
            File.createTempFile("favorite-graph-", ".preferences_pb").apply {
                delete()
                deleteOnExit()
            }.absolutePath,
        ),
    )

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class UnknownViewModel : ViewModel()

    private companion object {
        const val TEST_API_BASE_URL = "https://example.invalid"
    }
}

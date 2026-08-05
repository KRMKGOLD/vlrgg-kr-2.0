package kr.co.cotton.vlrgg_mobile.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageViewModel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AppGraphAndroidHostTest {
    @Test
    fun metroGraphResolvesFactoryCreatesMyPageAndRejectsUnknownViewModels() {
        val graph = createAppGraph()
        val factory = graph.appViewModelFactory

        assertIs<MyPageViewModel>(
            factory.create(MyPageViewModel::class, CreationExtras.Empty),
        )

        val failure = assertFailsWith<IllegalStateException> {
            factory.create(UnknownViewModel::class, CreationExtras.Empty)
        }
        assertContains(failure.message.orEmpty(), "Unsupported ViewModel class")
        assertContains(failure.message.orEmpty(), UnknownViewModel::class.qualifiedName.orEmpty())
    }

    @Test
    fun projectResolverScopesMyPageToItsNavigationEntryOwner() {
        val factory = createAppGraph().appViewModelFactory
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

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class UnknownViewModel : ViewModel()
}

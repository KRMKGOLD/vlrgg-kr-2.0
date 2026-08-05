package kr.co.cotton.vlrgg_mobile.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.ui.feature.mypage.MyPageViewModel
import kotlin.reflect.KClass

@Inject
class AppViewModelFactory(
    private val myPageViewModelProvider: () -> MyPageViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T = when (modelClass) {
        MyPageViewModel::class -> myPageViewModelProvider() as T
        else -> error("Unsupported ViewModel class: ${modelClass.qualifiedName}")
    }
}

fun resolveMyPageViewModel(
    owner: ViewModelStoreOwner,
    factory: AppViewModelFactory,
): MyPageViewModel = ViewModelProvider.create(owner, factory)[MyPageViewModel::class]

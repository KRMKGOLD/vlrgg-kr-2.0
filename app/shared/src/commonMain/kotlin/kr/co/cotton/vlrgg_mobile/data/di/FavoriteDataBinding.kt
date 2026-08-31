package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import kr.co.cotton.vlrgg_mobile.data.local.DataStoreFavoriteLocalDataSource
import kr.co.cotton.vlrgg_mobile.data.local.FavoriteLocalDataSource
import kr.co.cotton.vlrgg_mobile.data.repository.FavoriteRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository

@BindingContainer
internal interface FavoriteDataBinding {

    @Binds
    fun bindFavoriteLocalDataSource(
        impl: DataStoreFavoriteLocalDataSource,
    ): FavoriteLocalDataSource

    @Binds
    fun bindFavoriteRepository(
        impl: FavoriteRepositoryImpl,
    ): FavoriteRepository
}

package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteMatchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteNewsDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteMatchDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteNewsDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.repository.MatchRepositoryImpl
import kr.co.cotton.vlrgg_mobile.data.repository.NewsRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.NewsRepository

@BindingContainer
internal interface DataBinding {

    // Matches
    @Binds
    fun bindRemoteMatchDataSource(
        impl: RemoteMatchDataSourceImpl,
    ): RemoteMatchDataSource

    @Binds
    fun bindMatchRepository(
        impl: MatchRepositoryImpl,
    ): MatchRepository

    // News
    @Binds
    fun bindRemoteNewsDataSource(
        impl: RemoteNewsDataSourceImpl,
    ): RemoteNewsDataSource

    @Binds
    fun bindNewsRepository(
        impl: NewsRepositoryImpl,
    ): NewsRepository
}

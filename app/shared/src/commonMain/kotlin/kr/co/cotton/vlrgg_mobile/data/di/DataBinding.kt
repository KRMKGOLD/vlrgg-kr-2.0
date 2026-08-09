package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteNewsDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteNewsDataSourceImpl

@BindingContainer
internal interface DataBinding {

    @Binds
    fun bindRemoteNewsDataSource(
        impl: RemoteNewsDataSourceImpl,
    ): RemoteNewsDataSource
}

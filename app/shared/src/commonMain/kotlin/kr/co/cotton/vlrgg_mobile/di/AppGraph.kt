package kr.co.cotton.vlrgg_mobile.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kr.co.cotton.vlrgg_mobile.data.di.DataBinding
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.di.NetworkBinding

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        NetworkBinding::class,
        DataBinding::class,
    ],
)
interface AppGraph : ViewModelGraph {

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(
            @Provides networkConfig: NetworkConfig,
        ): AppGraph
    }
}

fun createAppGraph(apiBaseUrl: String): AppGraph =
    createGraphFactory<AppGraph.Factory>()
        .create(NetworkConfig(baseUrl = apiBaseUrl))

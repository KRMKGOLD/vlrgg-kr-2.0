package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSeriesDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteSeriesDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.repository.SeriesRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.SeriesRepository
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.di.NetworkBinding
import kotlin.test.Test
import kotlin.test.assertIs

class SeriesDataBindingTest {

    @Test
    fun seriesRemoteAndRepositoryContractsBindIntoMetroGraph() {
        val graph = createGraphFactory<TestSeriesDataGraph.Factory>().create(NetworkConfig("https://example.invalid"))
        try {
            assertIs<RemoteSeriesDataSourceImpl>(graph.remoteSeriesDataSource)
            assertIs<SeriesRepositoryImpl>(graph.seriesRepository)
        } finally {
            graph.httpClient.close()
        }
    }
}

@DependencyGraph(scope = AppScope::class, bindingContainers = [NetworkBinding::class, DataBinding::class])
internal interface TestSeriesDataGraph {
    val remoteSeriesDataSource: RemoteSeriesDataSource
    val seriesRepository: SeriesRepository
    val httpClient: HttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides networkConfig: NetworkConfig): TestSeriesDataGraph
    }
}

package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteSearchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteSearchDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.repository.SearchRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.di.NetworkBinding
import kotlin.test.Test
import kotlin.test.assertIs

class SearchDataBindingTest {
    @Test
    fun searchDataContractsAreBoundToTheirImplementations() {
        val graph = createGraphFactory<TestSearchDataGraph.Factory>().create(NetworkConfig(TEST_BASE_URL))
        try {
            assertIs<RemoteSearchDataSourceImpl>(graph.remoteSearchDataSource)
            assertIs<SearchRepositoryImpl>(graph.searchRepository)
        } finally {
            graph.httpClient.close()
        }
    }

    private companion object {
        const val TEST_BASE_URL = "https://example.invalid"
    }
}

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [NetworkBinding::class, DataBinding::class],
)
internal interface TestSearchDataGraph {
    val remoteSearchDataSource: RemoteSearchDataSource
    val searchRepository: SearchRepository
    val httpClient: HttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides networkConfig: NetworkConfig): TestSearchDataGraph
    }
}

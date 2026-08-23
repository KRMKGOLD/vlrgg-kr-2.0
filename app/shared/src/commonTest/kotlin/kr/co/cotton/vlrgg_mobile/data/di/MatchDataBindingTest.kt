package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteMatchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteMatchDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.repository.MatchRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.di.NetworkBinding
import kotlin.test.Test
import kotlin.test.assertIs

class MatchDataBindingTest {

    @Test
    fun remoteMatchDataSourceIsBoundToImplementation() {
        val graph = createGraphFactory<TestMatchDataGraph.Factory>()
            .create(NetworkConfig(baseUrl = TEST_BASE_URL))

        try {
            assertIs<RemoteMatchDataSourceImpl>(graph.remoteMatchDataSource)
        } finally {
            graph.httpClient.close()
        }
    }

    @Test
    fun matchRepositoryIsBoundToImplementation() {
        val graph = createGraphFactory<TestMatchDataGraph.Factory>()
            .create(NetworkConfig(baseUrl = TEST_BASE_URL))

        try {
            assertIs<MatchRepositoryImpl>(graph.matchRepository)
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
    bindingContainers = [
        NetworkBinding::class,
        DataBinding::class,
    ],
)
internal interface TestMatchDataGraph : ViewModelGraph {
    val remoteMatchDataSource: RemoteMatchDataSource
    val matchRepository: MatchRepository
    val httpClient: HttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides networkConfig: NetworkConfig,
        ): TestMatchDataGraph
    }
}

package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteEventDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteEventDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.repository.EventRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.EventRepository
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.di.NetworkBinding
import kotlin.test.Test
import kotlin.test.assertIs

class EventDataBindingTest {

    @Test
    fun remoteEventDataSourceIsBoundToImplementation() {
        val graph = createGraphFactory<TestEventDataGraph.Factory>()
            .create(NetworkConfig(baseUrl = TEST_BASE_URL))

        try {
            assertIs<RemoteEventDataSourceImpl>(graph.remoteEventDataSource)
        } finally {
            graph.httpClient.close()
        }
    }

    @Test
    fun eventRepositoryIsBoundToImplementation() {
        val graph = createGraphFactory<TestEventDataGraph.Factory>()
            .create(NetworkConfig(baseUrl = TEST_BASE_URL))

        try {
            assertIs<EventRepositoryImpl>(graph.eventRepository)
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
internal interface TestEventDataGraph : ViewModelGraph {
    val remoteEventDataSource: RemoteEventDataSource
    val eventRepository: EventRepository
    val httpClient: HttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides networkConfig: NetworkConfig,
        ): TestEventDataGraph
    }
}

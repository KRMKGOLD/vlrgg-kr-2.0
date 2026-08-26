package kr.co.cotton.vlrgg_mobile.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.ktor.client.HttpClient
import kr.co.cotton.vlrgg_mobile.data.remote.RemotePlayerDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteTeamDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemotePlayerDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.remote.impl.RemoteTeamDataSourceImpl
import kr.co.cotton.vlrgg_mobile.data.repository.PlayerRepositoryImpl
import kr.co.cotton.vlrgg_mobile.data.repository.TeamRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.di.NetworkBinding
import kotlin.test.Test
import kotlin.test.assertIs

class TeamPlayerDataBindingTest {

    @Test
    fun teamAndPlayerDataContractsBindIndependently() {
        val graph = createGraphFactory<TestTeamPlayerDataGraph.Factory>()
            .create(NetworkConfig(baseUrl = TEST_BASE_URL))

        try {
            assertIs<RemoteTeamDataSourceImpl>(graph.remoteTeamDataSource)
            assertIs<TeamRepositoryImpl>(graph.teamRepository)
            assertIs<RemotePlayerDataSourceImpl>(graph.remotePlayerDataSource)
            assertIs<PlayerRepositoryImpl>(graph.playerRepository)
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
internal interface TestTeamPlayerDataGraph : ViewModelGraph {
    val remoteTeamDataSource: RemoteTeamDataSource
    val teamRepository: TeamRepository
    val remotePlayerDataSource: RemotePlayerDataSource
    val playerRepository: PlayerRepository
    val httpClient: HttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides networkConfig: NetworkConfig,
        ): TestTeamPlayerDataGraph
    }
}

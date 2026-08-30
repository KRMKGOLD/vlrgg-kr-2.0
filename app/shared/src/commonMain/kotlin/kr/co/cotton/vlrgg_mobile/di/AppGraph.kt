package kr.co.cotton.vlrgg_mobile.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kr.co.cotton.vlrgg_mobile.data.di.DataBinding
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.FavoriteRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.PlayerRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.SeriesRepository
import kr.co.cotton.vlrgg_mobile.domain.repository.TeamRepository
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

    val teamRepository: TeamRepository
    val playerRepository: PlayerRepository
    val seriesRepository: SeriesRepository
    val matchRepository: MatchRepository
    val favoriteRepository: FavoriteRepository

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(
            @Provides networkConfig: NetworkConfig,
            @Provides favoriteDataStore: DataStore<Preferences>,
        ): AppGraph
    }
}

fun createAppGraph(
    apiBaseUrl: String,
    favoriteDataStore: DataStore<Preferences>,
): AppGraph =
    createGraphFactory<AppGraph.Factory>()
        .create(NetworkConfig(baseUrl = apiBaseUrl), favoriteDataStore)

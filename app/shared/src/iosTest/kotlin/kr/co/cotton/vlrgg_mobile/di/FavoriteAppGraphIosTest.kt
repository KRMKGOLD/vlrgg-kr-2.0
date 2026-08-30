package kr.co.cotton.vlrgg_mobile.di

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.local.datastore.createFavoriteDataStore
import kr.co.cotton.vlrgg_mobile.data.repository.FavoriteRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalForeignApi::class)
class FavoriteAppGraphIosTest {

    @Test
    fun graphResolvesFavoriteRepositoryAndRestoresExistingStorage() = runTest {
        val path = NSTemporaryDirectory() + "favorite-graph-" + NSUUID().UUIDString + ".preferences_pb"
        try {
            val firstScope = CoroutineScope(SupervisorJob())
            val firstGraph = createAppGraph(
                apiBaseUrl = TEST_API_BASE_URL,
                favoriteDataStore = createFavoriteDataStore(path, firstScope),
            )
            assertIs<FavoriteRepositoryImpl>(firstGraph.favoriteRepository)
            val favorite = FavoriteTeam("2", "DRX", null, "")
            assertEquals(AppResult.Success(Unit), firstGraph.favoriteRepository.addFavoriteTeam(favorite))
            firstScope.cancel()

            val recreatedGraph = createAppGraph(
                apiBaseUrl = TEST_API_BASE_URL,
                favoriteDataStore = createFavoriteDataStore(path, CoroutineScope(SupervisorJob())),
            )
            assertEquals(
                AppResult.Success(listOf(favorite)),
                recreatedGraph.favoriteRepository.getFavoriteTeams(),
            )
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    private companion object {
        const val TEST_API_BASE_URL = "https://example.invalid"
    }
}

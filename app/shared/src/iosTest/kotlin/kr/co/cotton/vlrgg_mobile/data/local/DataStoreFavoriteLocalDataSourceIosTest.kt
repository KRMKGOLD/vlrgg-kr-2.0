package kr.co.cotton.vlrgg_mobile.data.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.cinterop.ExperimentalForeignApi
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kr.co.cotton.vlrgg_mobile.data.local.datastore.createFavoriteDataStore
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage
import kr.co.cotton.vlrgg_mobile.data.repository.FavoriteRepositoryImpl
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class DataStoreFavoriteLocalDataSourceIosTest {

    @Test
    fun malformedStoredValueBecomesRepositoryFailure() = runTest {
        val path = NSTemporaryDirectory() + "favorite-malformed-" + NSUUID().UUIDString + ".preferences_pb"
        val scope = CoroutineScope(SupervisorJob())
        try {
            val dataStore = createFavoriteDataStore(path, scope)
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("favorite_teams")] = "{malformed"
            }

            val repository = FavoriteRepositoryImpl(DataStoreFavoriteLocalDataSource(dataStore))
            assertEquals(AppResult.Failure, repository.getFavoriteTeams())
        } finally {
            scope.cancel()
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    @Test
    fun dataStoreRoundTripSurvivesRecreation() = runTest {
        val path = NSTemporaryDirectory() + "favorite-" + NSUUID().UUIDString + ".preferences_pb"
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob)
        val recreatedJob = SupervisorJob()
        val recreatedScope = CoroutineScope(recreatedJob)
        try {
            val first = DataStoreFavoriteLocalDataSource(createFavoriteDataStore(path, firstScope))
            first.upsertFavoriteTeam(FavoriteTeamStorage("2", "DRX", null, ""))
            first.upsertFavoritePlayer(FavoritePlayerStorage("100", "", null, "KR", null))
            firstJob.cancelAndJoin()

            val recreated = DataStoreFavoriteLocalDataSource(
                createFavoriteDataStore(path, recreatedScope),
            )
            assertEquals(listOf(FavoriteTeamStorage("2", "DRX", null, "")), recreated.observeFavoriteTeams().first())
            assertEquals(
                listOf(FavoritePlayerStorage("100", "", null, "KR", null)),
                recreated.observeFavoritePlayers().first(),
            )
        } finally {
            firstJob.cancelAndJoin()
            recreatedJob.cancelAndJoin()
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}

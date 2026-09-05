package kr.co.cotton.vlrgg_mobile.data.local

import java.io.File
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.data.local.datastore.createFavoriteDataStore
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreFavoriteLocalDataSourceAndroidHostTest {

    @Test
    fun legacyTeamJsonWithoutImageUrlDecodesWithANullImageUrl() = runTest {
        val file = File.createTempFile("favorite-legacy-", ".preferences_pb").apply { delete() }
        val job = SupervisorJob()
        val scope = CoroutineScope(job)
        try {
            val dataStore = createFavoriteDataStore(file.absolutePath, scope)
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("favorite_teams")] =
                    "{\"favorites\":[{\"id\":\"2\",\"name\":\"DRX\",\"tag\":\"DRX\",\"country\":\"Korea\"}]}"
            }

            assertEquals(
                listOf(FavoriteTeamStorage("2", "DRX", "DRX", "Korea", null)),
                DataStoreFavoriteLocalDataSource(dataStore).getFavoriteTeams(),
            )
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun legacyPlayerJsonWithoutImageUrlDecodesWithANullImageUrl() = runTest {
        val file = File.createTempFile("favorite-player-legacy-", ".preferences_pb").apply { delete() }
        val job = SupervisorJob()
        val scope = CoroutineScope(job)
        try {
            val dataStore = createFavoriteDataStore(file.absolutePath, scope)
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("favorite_players")] =
                    "{\"favorites\":[{\"id\":\"100\",\"handle\":\"stax\",\"realName\":null,\"countryCode\":\"KR\",\"countryName\":\"Korea\"}]}"
            }

            assertEquals(
                listOf(FavoritePlayerStorage("100", "stax", null, "KR", "Korea", null)),
                DataStoreFavoriteLocalDataSource(dataStore).getFavoritePlayers(),
            )
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun dataStoreRoundTripRecreationKeepsTypesKeysOrderAndExactRemoval() = runTest {
        val file = File.createTempFile("favorite-", ".preferences_pb").apply { delete() }
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob)
        val recreatedJob = SupervisorJob()
        val recreatedScope = CoroutineScope(recreatedJob)
        try {
            val first = DataStoreFavoriteLocalDataSource(
                createFavoriteDataStore(file.absolutePath, firstScope),
            )
            first.upsertFavoriteTeam(
                FavoriteTeamStorage("2", "DRX", null, "", "https://cdn.example.com/old-drx.png"),
            )
            first.upsertFavoriteTeam(FavoriteTeamStorage("1", "Sentinels", "SEN", null))
            first.upsertFavoriteTeam(FavoriteTeamStorage("2", "DRX updated", "", null, null))
            first.upsertFavoritePlayer(
                FavoritePlayerStorage("100", "", null, "", "Korea", "https://cdn.example.com/old-stax.png"),
            )
            first.upsertFavoritePlayer(FavoritePlayerStorage("100", "", null, "", "Korea", null))
            first.removeFavoritePlayer("2")

            firstJob.cancelAndJoin()
            val recreated = DataStoreFavoriteLocalDataSource(
                createFavoriteDataStore(file.absolutePath, recreatedScope),
            )
            assertEquals(
                listOf(
                    FavoriteTeamStorage("2", "DRX updated", "", null),
                    FavoriteTeamStorage("1", "Sentinels", "SEN", null),
                ),
                recreated.observeFavoriteTeams().first(),
            )
            assertEquals(
                listOf(FavoritePlayerStorage("100", "", null, "", "Korea")),
                recreated.observeFavoritePlayers().first(),
            )

            recreated.removeFavoriteTeam("2")
            assertEquals(listOf("1"), recreated.getFavoriteTeams().map { it.id })
        } finally {
            firstJob.cancelAndJoin()
            recreatedJob.cancelAndJoin()
            file.delete()
        }
    }
}

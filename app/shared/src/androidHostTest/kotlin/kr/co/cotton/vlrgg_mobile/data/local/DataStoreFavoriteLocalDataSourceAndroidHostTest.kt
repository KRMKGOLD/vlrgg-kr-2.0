package kr.co.cotton.vlrgg_mobile.data.local

import java.io.File
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
            first.upsertFavoriteTeam(FavoriteTeamStorage("2", "DRX", null, ""))
            first.upsertFavoriteTeam(FavoriteTeamStorage("1", "Sentinels", "SEN", null))
            first.upsertFavoriteTeam(FavoriteTeamStorage("2", "DRX updated", "", null))
            first.upsertFavoritePlayer(FavoritePlayerStorage("100", "", null, "", "Korea"))
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

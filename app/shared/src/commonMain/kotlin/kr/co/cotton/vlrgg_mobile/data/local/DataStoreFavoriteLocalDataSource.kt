package kr.co.cotton.vlrgg_mobile.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayersStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamsStorage

@Inject
internal class DataStoreFavoriteLocalDataSource(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json,
) : FavoriteLocalDataSource {

    override fun observeFavoriteTeams(): Flow<List<FavoriteTeamStorage>> =
        dataStore.data.map { preferences ->
            preferences[TEAM_FAVORITES_KEY]
                ?.let(::decodeFavoriteTeams)
                ?: emptyList()
        }

    override fun observeFavoritePlayers(): Flow<List<FavoritePlayerStorage>> =
        dataStore.data.map { preferences ->
            preferences[PLAYER_FAVORITES_KEY]
                ?.let(::decodeFavoritePlayers)
                ?: emptyList()
        }

    override suspend fun getFavoriteTeams(): List<FavoriteTeamStorage> =
        observeFavoriteTeams().first()

    override suspend fun getFavoritePlayers(): List<FavoritePlayerStorage> =
        observeFavoritePlayers().first()

    override suspend fun upsertFavoriteTeam(favorite: FavoriteTeamStorage) {
        dataStore.edit { preferences ->
            val existing = preferences[TEAM_FAVORITES_KEY]
                ?.let(::decodeFavoriteTeams)
                ?: emptyList()
            preferences[TEAM_FAVORITES_KEY] = json.encodeToString(
                FavoriteTeamsStorage(existing.replaceAtExistingIdOrAppend(favorite) { it.id }),
            )
        }
    }

    override suspend fun upsertFavoritePlayer(favorite: FavoritePlayerStorage) {
        dataStore.edit { preferences ->
            val existing = preferences[PLAYER_FAVORITES_KEY]
                ?.let(::decodeFavoritePlayers)
                ?: emptyList()
            preferences[PLAYER_FAVORITES_KEY] = json.encodeToString(
                FavoritePlayersStorage(existing.replaceAtExistingIdOrAppend(favorite) { it.id }),
            )
        }
    }

    override suspend fun removeFavoriteTeam(teamId: String) {
        dataStore.edit { preferences ->
            val existing = preferences[TEAM_FAVORITES_KEY]
                ?.let(::decodeFavoriteTeams)
                ?: emptyList()
            preferences[TEAM_FAVORITES_KEY] = json.encodeToString(
                FavoriteTeamsStorage(existing.filterNot { it.id == teamId }),
            )
        }
    }

    override suspend fun removeFavoritePlayer(playerId: String) {
        dataStore.edit { preferences ->
            val existing = preferences[PLAYER_FAVORITES_KEY]
                ?.let(::decodeFavoritePlayers)
                ?: emptyList()
            preferences[PLAYER_FAVORITES_KEY] = json.encodeToString(
                FavoritePlayersStorage(existing.filterNot { it.id == playerId }),
            )
        }
    }

    private fun decodeFavoriteTeams(value: String): List<FavoriteTeamStorage> =
        json.decodeFromString<FavoriteTeamsStorage>(value).favorites

    private fun decodeFavoritePlayers(value: String): List<FavoritePlayerStorage> =
        json.decodeFromString<FavoritePlayersStorage>(value).favorites

    private companion object {
        val TEAM_FAVORITES_KEY = stringPreferencesKey("favorite_teams")
        val PLAYER_FAVORITES_KEY = stringPreferencesKey("favorite_players")
    }
}

private fun <T> List<T>.replaceAtExistingIdOrAppend(
    value: T,
    id: (T) -> String,
): List<T> {
    val index = indexOfFirst { id(it) == id(value) }
    return if (index < 0) this + value else toMutableList().also { it[index] = value }
}

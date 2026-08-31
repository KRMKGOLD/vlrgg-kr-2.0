package kr.co.cotton.vlrgg_mobile.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

const val FAVORITE_DATA_STORE_FILE_NAME = "favorites.preferences_pb"

fun createFavoriteDataStore(
    storage: Storage<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): DataStore<Preferences> = DataStoreFactory.create(
    storage = storage,
    scope = scope,
)

expect fun createFavoriteDataStore(
    path: String,
    scope: CoroutineScope? = null,
): DataStore<Preferences>

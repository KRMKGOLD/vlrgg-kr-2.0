package kr.co.cotton.vlrgg_mobile.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun createFavoriteDataStore(
    path: String,
    scope: CoroutineScope?,
): DataStore<Preferences> = createFavoriteDataStore(
    storage = OkioStorage<Preferences>(
        fileSystem = FileSystem.SYSTEM,
        serializer = PreferencesSerializer,
        producePath = { path.toPath() },
    ),
    scope = scope ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
)

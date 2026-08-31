package kr.co.cotton.vlrgg_mobile.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import okio.buffer
import okio.sink
import okio.source

actual fun createFavoriteDataStore(
    path: String,
    scope: CoroutineScope?,
): DataStore<Preferences> = createFavoriteDataStore(
    storage = FileStorage<Preferences>(
        serializer = AndroidPreferencesSerializer,
        produceFile = { File(path) },
    ),
    scope = scope ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
)

private object AndroidPreferencesSerializer : Serializer<Preferences> {
    override val defaultValue: Preferences = PreferencesSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): Preferences =
        PreferencesSerializer.readFrom(input.source().buffer())

    override suspend fun writeTo(
        t: Preferences,
        output: OutputStream,
    ) {
        output.sink().buffer().use { sink ->
            PreferencesSerializer.writeTo(t, sink)
        }
    }
}

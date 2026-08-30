package kr.co.cotton.vlrgg_mobile

import android.app.Application
import kr.co.cotton.vlrgg_mobile.data.local.datastore.FAVORITE_DATA_STORE_FILE_NAME
import kr.co.cotton.vlrgg_mobile.data.local.datastore.createFavoriteDataStore
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.di.createAppGraph

class VlrggApplication : Application() {

    val appGraph: AppGraph by lazy {
        createAppGraph(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            favoriteDataStore = createFavoriteDataStore(
                filesDir.resolve(FAVORITE_DATA_STORE_FILE_NAME).absolutePath,
            ),
        )
    }
}

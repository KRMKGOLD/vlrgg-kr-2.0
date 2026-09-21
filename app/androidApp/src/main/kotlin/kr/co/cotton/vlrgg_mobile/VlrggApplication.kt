package kr.co.cotton.vlrgg_mobile

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kr.co.cotton.vlrgg_mobile.data.local.datastore.FAVORITE_DATA_STORE_FILE_NAME
import kr.co.cotton.vlrgg_mobile.data.local.datastore.createFavoriteDataStore
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.di.createAppGraph
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3

class VlrggApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.CRASHLYTICS_COLLECTION_ENABLED) {
            checkNotNull(FirebaseApp.initializeApp(this)) { "Firebase configuration is missing" }
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        }
        initializeVlrMaterial3()
    }

    val appGraph: AppGraph by lazy {
        createAppGraph(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            favoriteDataStore = createFavoriteDataStore(
                filesDir.resolve(FAVORITE_DATA_STORE_FILE_NAME).absolutePath,
            ),
        )
    }
}

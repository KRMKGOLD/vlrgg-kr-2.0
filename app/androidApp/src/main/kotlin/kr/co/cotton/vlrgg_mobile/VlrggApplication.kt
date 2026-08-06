package kr.co.cotton.vlrgg_mobile

import android.app.Application
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.di.createAppGraph

class VlrggApplication : Application() {

    val appGraph: AppGraph by lazy {
        createAppGraph(
            apiBaseUrl = BuildConfig.API_BASE_URL,
        )
    }
}
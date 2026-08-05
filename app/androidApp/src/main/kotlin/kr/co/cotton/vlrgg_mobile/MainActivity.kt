package kr.co.cotton.vlrgg_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kr.co.cotton.vlrgg_mobile.di.createAppGraph

class MainActivity : ComponentActivity() {
    private val appGraph by lazy(::createAppGraph)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val graph = appGraph

        setContent {
            App(graph)
        }
    }
}

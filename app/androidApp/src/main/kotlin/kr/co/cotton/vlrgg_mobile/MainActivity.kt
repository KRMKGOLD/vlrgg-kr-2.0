package kr.co.cotton.vlrgg_mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import kr.co.cotton.vlrgg_mobile.ui.component.StatusBarIconsApplier

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The app is light-only: the status bar keeps dark icons unless a dark detail band asks otherwise.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))

        val graph = (application as VlrggApplication).appGraph
        val aboutPlatform = AndroidAboutPlatform(this)
        val statusBarIconsApplier = StatusBarIconsApplier { lightIcons ->
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !lightIcons
        }

        setContent {
            App(
                graph = graph,
                aboutPlatform = aboutPlatform,
                readLicenseDefinitions = aboutPlatform::readLicenseDefinitions,
                statusBarIconsApplier = statusBarIconsApplier,
            )
        }
    }
}

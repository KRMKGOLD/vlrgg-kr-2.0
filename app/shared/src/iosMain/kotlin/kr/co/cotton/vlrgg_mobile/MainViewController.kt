package kr.co.cotton.vlrgg_mobile

import androidx.compose.ui.window.ComposeUIViewController
import kr.co.cotton.vlrgg_mobile.di.AppGraph
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses.readIosLicenseDefinitions
import kr.co.cotton.vlrgg_mobile.ui.theme.initializeVlrMaterial3
import platform.UIKit.UIViewController
import kr.co.cotton.vlrgg_mobile.ui.component.StatusBarIconsApplier
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

fun MainViewController(
    graph: AppGraph,
    aboutPlatform: AboutPlatform,
): UIViewController {
    initializeVlrMaterial3()
    return ComposeUIViewController {
        App(
            graph = graph,
            aboutPlatform = aboutPlatform,
            readLicenseDefinitions = ::readIosLicenseDefinitions,
            statusBarIconsApplier = IosStatusBarIconsApplier,
        )
    }
}

/**
 * The SwiftUI host does not forward a child controller's preferred status bar style, so the
 * app uses the application-wide style (`UIViewControllerBasedStatusBarAppearance` is off in
 * Info.plist, which also sets the dark-content launch style).
 */
@Suppress("DEPRECATION")
private val IosStatusBarIconsApplier = StatusBarIconsApplier { lightIcons ->
    UIApplication.sharedApplication.setStatusBarStyle(
        if (lightIcons) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent,
        animated = true,
    )
}

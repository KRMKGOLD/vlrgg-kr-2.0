package kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses

import vlrggmobile.app.shared.generated.resources.Res

/** Reads the iOS definitions that `:app:shared:exportLibraryDefinitions` bundles as an iosMain resource. */
internal suspend fun readIosLicenseDefinitions(): String =
    Res.readBytes("files/aboutlibraries.json").decodeToString()

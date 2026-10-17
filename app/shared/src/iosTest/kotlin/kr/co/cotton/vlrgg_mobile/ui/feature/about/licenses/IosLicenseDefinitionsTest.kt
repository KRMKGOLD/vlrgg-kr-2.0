package kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses

import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosLicenseDefinitionsTest {
    @Test
    fun bundledDefinitionsParseWithTheRuntimeAndListShippedLibraries() = runTest {
        val libraries = Libs.Builder().withJson(readIosLicenseDefinitions()).build().libraries
        val byId = libraries.associateBy { it.uniqueId }

        assertTrue(libraries.size > 50, "Only ${libraries.size} libraries were generated.")
        // Firebase Apple SDK (Crashlytics) comes from Swift Package Manager, so it is a manual entry.
        listOf(
            "github.com/firebase/firebase-ios-sdk",
            "io.ktor:ktor-client-core",
            "io.ktor:ktor-client-darwin-iosarm64",
            "org.jetbrains.compose.ui:ui",
            "org.jetbrains.compose.material3:material3",
            "com.mikepenz:aboutlibraries-compose-m3",
            "com.google.material-symbols",
        ).forEach { id -> assertTrue(id in byId, "$id is missing from the iOS definitions.") }
        assertTrue(libraries.none { it.uniqueId.startsWith("io.ktor:ktor-client-android") })

        val firebase = byId.getValue("github.com/firebase/firebase-ios-sdk")
        assertEquals("12.19.2", firebase.artifactVersion)
        assertEquals(listOf("Apache License 2.0"), firebase.licenses.map { it.name })
        assertTrue(firebase.licenses.single().licenseContent.orEmpty().startsWith("Apache License"))
        assertEquals("zlib License", byId.getValue("github.com/firebase/nanopb").licenses.single().name)
        assertEquals("3.5.0", byId.getValue("io.ktor:ktor-client-core").artifactVersion)
    }
}

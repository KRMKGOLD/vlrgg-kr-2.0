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
        libraries.forEach { library ->
            assertTrue(library.licenses.isNotEmpty(), "${library.uniqueId} has no license.")
            library.licenses.forEach { license ->
                assertTrue(!license.licenseContent.isNullOrBlank(), "${library.uniqueId} has no ${license.name} text.")
            }
        }

        // Crashlytics/LICENSE adds the libunwind dwarf.h notice after the Apache License text.
        val firebase = byId.getValue("github.com/firebase/firebase-ios-sdk")
        assertEquals("12.19.2", firebase.artifactVersion)
        assertEquals(listOf("Firebase Crashlytics LICENSE (12.19.2)"), firebase.licenses.map { it.name })
        val firebaseLicense = firebase.licenses.single().licenseContent.orEmpty()
        assertTrue(firebaseLicense.trimStart().startsWith("Apache License"))
        assertTrue("Version 2.0, January 2004" in firebaseLicense)
        assertTrue("Copyright (c) 2003-2005 Hewlett-Packard Development Company, L.P." in firebaseLicense)
        assertTrue("libunwind - a platform-independent unwind library" in firebaseLicense)
        assertEquals("zlib License", byId.getValue("github.com/firebase/nanopb").licenses.single().name)
        assertEquals("3.5.0", byId.getValue("io.ktor:ktor-client-core").artifactVersion)
    }
}

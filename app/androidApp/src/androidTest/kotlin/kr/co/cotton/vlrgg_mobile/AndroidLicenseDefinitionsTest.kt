package kr.co.cotton.vlrgg_mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs on a device because AboutLibraries parses with the platform `org.json`, which JVM host
 * tests only provide as a stub.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLicenseDefinitionsTest {
    @Test
    fun generatedRuntimeClasspathDefinitionsParseWithTheBundledRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val definitions = runBlocking { AndroidAboutPlatform(context).readLicenseDefinitions() }
        val libraries = Libs.Builder().withJson(definitions).build().libraries
        val byId = libraries.associateBy { it.uniqueId }

        assertTrue(libraries.size > 100, "Only ${libraries.size} libraries were generated.")
        listOf(
            "com.google.firebase:firebase-crashlytics",
            "io.ktor:ktor-client-core",
            "io.ktor:ktor-client-android",
            "org.jetbrains.compose.ui:ui",
            "androidx.activity:activity-compose",
            "androidx.core:core-splashscreen",
            "com.mikepenz:aboutlibraries-compose-m3",
            "com.google.material-symbols",
        ).forEach { id -> assertTrue(id in byId, "$id is missing from the Android definitions.") }
        assertTrue(libraries.none { it.uniqueId.startsWith("github.com/firebase/") })
        // The default license dialog opens only when the first license has its text.
        libraries.forEach { library ->
            assertTrue(library.licenses.isNotEmpty(), "${library.uniqueId} has no license.")
            library.licenses.forEach { license ->
                assertTrue(!license.licenseContent.isNullOrBlank(), "${library.uniqueId} has no ${license.name} text.")
            }
        }

        val crashlytics = byId.getValue("com.google.firebase:firebase-crashlytics")
        assertEquals(listOf("Apache License 2.0"), crashlytics.licenses.map { it.name })
        assertTrue(crashlytics.licenses.single().licenseContent.orEmpty().startsWith("Apache License"))
        listOf(
            "com.google.android.gms:play-services-basement",
            "com.google.android.gms:play-services-tasks",
            "com.google.firebase:firebase-measurement-connector",
        ).forEach { id ->
            val license = byId.getValue(id).licenses.single()
            assertEquals("Android Software Development Kit License", license.name)
            assertTrue(
                license.licenseContent.orEmpty().startsWith("This is the Android Software Development Kit License Agreement"),
                "$id does not carry the Android SDK License Agreement text.",
            )
        }
        assertEquals("3.5.0", byId.getValue("io.ktor:ktor-client-core").artifactVersion)
    }
}

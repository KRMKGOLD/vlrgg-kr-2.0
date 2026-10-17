package kr.co.cotton.vlrgg_mobile.ui.feature.about.licenses

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mikepenz.aboutlibraries.Libs
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OpenSourceLicensesContentUiTest {
    @Test
    fun listShowsNameVersionAndLicenseAndOpensTheFullLicenseText() = runComposeUiTest {
        var backClicks = 0
        val libraries = Libs.Builder().withJson(LICENSE_DEFINITIONS_FIXTURE).build()
        setContent {
            VlrTheme {
                OpenSourceLicensesContent(
                    uiState = OpenSourceLicensesUiState(libraries = libraries),
                    onBack = { backClicks += 1 },
                )
            }
        }

        onNodeWithText(OPEN_SOURCE_LICENSES_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        onNodeWithText("Ktor Client Core").assertExists()
        onNodeWithText("3.5.0").assertExists()
        onNodeWithText("Firebase Crashlytics").assertExists()
        onNodeWithText("20.1.1").assertExists()
        onAllNodesWithText("Apache License 2.0").assertCountEquals(2)

        onNodeWithText("Ktor Client Core").performClick()
        onNodeWithText("Version 2.0, January 2004", substring = true).assertExists()
        onNodeWithText("OK").performClick()
        onNodeWithText("Version 2.0, January 2004", substring = true).assertDoesNotExist()

        onNodeWithContentDescription("뒤로 가기").performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun loadFailureKeepsTheBarAndShowsOnlyAMessage() = runComposeUiTest {
        setContent {
            VlrTheme {
                OpenSourceLicensesContent(
                    uiState = OpenSourceLicensesUiState(isLoadFailed = true),
                    onBack = {},
                )
            }
        }

        onNodeWithText(OPEN_SOURCE_LICENSES_TITLE).assertExists()
        onNodeWithContentDescription("뒤로 가기").assertExists()
        onNodeWithText("오픈소스 라이선스 정보를 불러올 수 없습니다.").assertExists()
    }
}

/** A trimmed `aboutlibraries.json` in the shape the Gradle plugin generates. */
private const val LICENSE_DEFINITIONS_FIXTURE = """
{
  "libraries": [
    {
      "uniqueId": "io.ktor:ktor-client-core",
      "artifactVersion": "3.5.0",
      "name": "Ktor Client Core",
      "developers": [{ "name": "Jetbrains Team" }],
      "licenses": ["Apache-2.0"],
      "funding": []
    },
    {
      "uniqueId": "com.google.firebase:firebase-crashlytics",
      "artifactVersion": "20.1.1",
      "name": "Firebase Crashlytics",
      "developers": [],
      "licenses": ["Apache-2.0"],
      "funding": []
    }
  ],
  "licenses": {
    "Apache-2.0": {
      "name": "Apache License 2.0",
      "url": "https://spdx.org/licenses/Apache-2.0.html",
      "content": "Apache License\nVersion 2.0, January 2004",
      "internalHash": "Apache-2.0",
      "spdxId": "Apache-2.0",
      "hash": "Apache-2.0"
    }
  }
}
"""

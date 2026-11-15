package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_arrow_back
import vlrggmobile.app.shared.generated.resources.ic_arrow_downward
import vlrggmobile.app.shared.generated.resources.ic_arrow_outward
import vlrggmobile.app.shared.generated.resources.ic_chevron_right
import vlrggmobile.app.shared.generated.resources.ic_search
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Directional Material Symbols (`arrow_back`, `chevron_right`, `arrow_outward`) mirror in RTL through
 * the vector's `android:autoMirrored`; non-directional ones keep their drawing. Compared on rendered pixels.
 */
@OptIn(ExperimentalTestApi::class)
class VlrIconMirroringUiTest {
    @Test
    fun directionalIconsDrawMirroredInRtl() {
        listOf(
            Res.drawable.ic_arrow_back to "arrow_back",
            Res.drawable.ic_chevron_right to "chevron_right",
            Res.drawable.ic_arrow_outward to "arrow_outward",
        ).forEach { (icon, name) ->
            val ltr = renderIcon(icon, LayoutDirection.Ltr)
            val rtl = renderIcon(icon, LayoutDirection.Rtl)
            assertTrue(!ltr.sameAs(ltr.flipped()), "$name is horizontally symmetric, so the check cannot see a mirror")
            assertTrue(rtl.sameAs(ltr.flipped()), "$name is not mirrored in RTL")
        }
    }

    @Test
    fun nonDirectionalIconsKeepTheirDrawingInRtl() {
        listOf(
            Res.drawable.ic_search to "search",
            Res.drawable.ic_arrow_downward to "arrow_downward",
        ).forEach { (icon, name) ->
            assertTrue(renderIcon(icon, LayoutDirection.Rtl).sameAs(renderIcon(icon, LayoutDirection.Ltr)), "$name changed in RTL")
        }
    }

    @Test
    fun detailHeaderBackPointsTowardTheStartInRtl() {
        val ltr = renderNode(LayoutDirection.Ltr) { DetailHeader(onBack = {}, title = "뉴스") }
        val rtl = renderNode(LayoutDirection.Rtl) { DetailHeader(onBack = {}, title = "뉴스") }
        assertTrue(rtl.sameAs(ltr.flipped()), "Back arrow is not mirrored in RTL")
    }

    private fun renderIcon(icon: DrawableResource, direction: LayoutDirection): Pixels {
        var pixels: Pixels? = null
        runSkikoComposeUiTest(size = Size(48f, 48f)) {
            setContent {
                Themed(direction) {
                    Box(Modifier.size(24.dp).background(Color.White).testTag(ICON_TAG)) {
                        Icon(vectorResource(icon), contentDescription = null, tint = Color.Black)
                    }
                }
            }
            pixels = capture { onNodeWithTag(ICON_TAG).captureToImage().toPixelMap() }
        }
        return requireNotNull(pixels)
    }

    private fun renderNode(direction: LayoutDirection, content: @Composable () -> Unit): Pixels {
        var pixels: Pixels? = null
        runSkikoComposeUiTest(size = Size(360f, 120f)) {
            setContent { Themed(direction, content) }
            pixels = capture { onNodeWithContentDescription(DetailHeaderBackLabel).captureToImage().toPixelMap() }
        }
        return requireNotNull(pixels)
    }

    @Composable
    private fun Themed(direction: LayoutDirection, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides direction) {
            VlrTheme(content = content)
        }
    }

    private fun ComposeUiTest.capture(read: ComposeUiTest.() -> PixelMap): Pixels {
        waitForIdle()
        val map = read()
        return Pixels(map.width, map.height) { x, y -> map[x, y] }
    }

    /** Opaque-ish pixel grid; channels compared with a small tolerance for anti-aliasing. */
    private class Pixels(val width: Int, val height: Int, at: (Int, Int) -> Color) {
        private val values = List(width * height) { at(it % width, it / width) }

        operator fun get(x: Int, y: Int): Color = values[y * width + x]

        fun flipped(): Pixels = Pixels(width, height) { x, y -> this[width - 1 - x, y] }

        fun sameAs(other: Pixels): Boolean =
            width == other.width && height == other.height && values.indices.all { index ->
                val a = values[index]
                val b = other.values[index]
                kotlin.math.abs(a.red - b.red) < 0.1f &&
                    kotlin.math.abs(a.green - b.green) < 0.1f &&
                    kotlin.math.abs(a.blue - b.blue) < 0.1f &&
                    kotlin.math.abs(a.alpha - b.alpha) < 0.1f
            }
    }

    private companion object {
        const val ICON_TAG = "mirror-icon"
    }
}

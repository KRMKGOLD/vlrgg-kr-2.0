package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.getenv

/** Optional local evidence from the same native fixtures used by the assertions. */
@OptIn(ExperimentalTestApi::class, ExperimentalForeignApi::class)
internal fun SemanticsNodeInteraction.captureStatsEvidence(name: String) {
    val directory = getenv("ISSUE74_SCREENSHOT_DIR")?.toKString() ?: return
    val image = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
    val bytes = try {
        val encoded = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
        try {
            encoded.bytes
        } finally {
            encoded.close()
        }
    } finally {
        image.close()
    }
    val file = checkNotNull(fopen("$directory/$name.png", "wb"))
    try {
        bytes.usePinned {
            check(fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt() == bytes.size)
        }
    } finally {
        fclose(file)
    }
}

package kr.co.cotton.vlrgg_mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kotlin.coroutines.cancellation.CancellationException

class AndroidAboutPlatform(
    private val context: Context,
) : AboutPlatform {
    override val buildVersion: String? = BuildConfig.VERSION_NAME.takeIf(String::isNotBlank)

    override fun openUrl(url: String, onResult: (Boolean) -> Unit) {
        val uri = Uri.parse(url)
        // ACTION_SENDTO limits a mailto: target to mail apps; startActivity needs no package visibility.
        val action = if (uri.scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_VIEW
        val opened = try {
            context.startActivity(
                Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
        onResult(opened)
    }

    /** Reads the definitions AboutLibraries generates from this variant's runtime classpath. */
    suspend fun readLicenseDefinitions(): String = withContext(Dispatchers.IO) {
        context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
    }
}

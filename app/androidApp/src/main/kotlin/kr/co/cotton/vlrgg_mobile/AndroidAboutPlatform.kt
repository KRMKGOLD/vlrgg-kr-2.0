package kr.co.cotton.vlrgg_mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import kr.co.cotton.vlrgg_mobile.ui.feature.about.AboutPlatform
import kotlin.coroutines.cancellation.CancellationException

class AndroidAboutPlatform(
    private val context: Context,
) : AboutPlatform {
    override val buildVersion: String? = BuildConfig.VERSION_NAME.takeIf(String::isNotBlank)

    override fun openUrl(url: String, onResult: (Boolean) -> Unit) {
        val opened = try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
        onResult(opened)
    }
}

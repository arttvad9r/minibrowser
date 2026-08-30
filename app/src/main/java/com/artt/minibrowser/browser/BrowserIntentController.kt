package com.artt.minibrowser.browser

import android.content.Intent
import androidx.activity.ComponentActivity
import com.artt.minibrowser.R
import com.artt.minibrowser.engine.createSafeExternalIntent
import com.artt.minibrowser.engine.safeExternalFallbackUrl

/** Owns Android Intent side effects for browser navigation and sharing. */
internal class BrowserIntentController(
    private val activity: ComponentActivity,
    private val loadFallback: (String) -> Unit,
) {
    fun openExternalUri(value: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        val external = createSafeExternalIntent(value)
        val launched = external != null &&
            external.resolveActivity(activity.packageManager) != null &&
            runCatching {
                activity.startActivity(
                    Intent.createChooser(external, activity.getString(R.string.external_chooser_title)),
                )
                true
            }.getOrDefault(false)
        if (launched || activity.isFinishing || activity.isDestroyed) return

        safeExternalFallbackUrl(value)?.let(loadFallback)
    }

    fun shareUrl(value: String?) {
        val url = value ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        runCatching {
            activity.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    },
                    activity.getString(R.string.share_chooser_title),
                ),
            )
        }
    }
}

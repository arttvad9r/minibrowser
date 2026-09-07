package com.artt.minibrowser.browser

import android.content.Intent
import androidx.activity.ComponentActivity
import com.artt.minibrowser.R
import com.artt.minibrowser.engine.createSafeExternalIntent
import com.artt.minibrowser.engine.safeExternalFallbackUrl
import com.artt.minibrowser.net.sanitizeWebUriForPersistence

internal fun shareableBrowserUrl(value: String?): String? =
    value?.let(::sanitizeWebUriForPersistence)

/** Owns Android Intent side effects for browser navigation and sharing. */
internal class BrowserIntentController(
    private val activity: ComponentActivity,
    private val loadFallback: (String) -> Unit,
) {
    fun openExternalUri(value: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        val external = createSafeExternalIntent(value)
        val launched = external != null &&
            runCatching {
                activity.startActivity(external)
                true
            }.getOrDefault(false)
        if (launched || activity.isFinishing || activity.isDestroyed) return

        safeExternalFallbackUrl(value)?.let(loadFallback)
    }

    /**
     * Kept temporarily for the existing menu contract. Generic HTTP(S) URLs must never expose an
     * "external app" action because Android would legitimately route them to another browser.
     * User-clicked App Links are handled automatically by ExternalAppNavigationDelegate instead.
     */
    @Suppress("UNUSED_PARAMETER")
    fun canOpenInExternalApp(value: String?): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun openInExternalApp(value: String?) = Unit

    fun shareUrl(value: String?) {
        val url = shareableBrowserUrl(value) ?: return
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

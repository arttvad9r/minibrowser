package com.artt.minibrowser.browser

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

    fun canOpenInExternalApp(value: String?): Boolean {
        val intent = webViewIntent(value) ?: return false
        return activity.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName != activity.packageName }
    }

    fun openInExternalApp(value: String?) {
        if (activity.isFinishing || activity.isDestroyed) return
        val intent = webViewIntent(value) ?: return
        if (!canOpenInExternalApp(value)) return

        runCatching {
            val chooser = Intent.createChooser(intent, activity.getString(R.string.external_chooser_title)).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(activity, activity::class.java)),
                )
            }
            activity.startActivity(chooser)
        }
    }

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

    private fun webViewIntent(value: String?): Intent? {
        val url = shareableBrowserUrl(value) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    }
}

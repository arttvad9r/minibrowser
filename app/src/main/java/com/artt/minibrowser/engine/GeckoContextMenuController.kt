package com.artt.minibrowser.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import com.artt.minibrowser.R
import org.mozilla.geckoview.GeckoSession

/** Implemented by the browser Activity so context actions can create a tab without selecting it. */
internal interface BackgroundTabHost {
    fun openBackgroundTab(uri: String, private: Boolean)
}

/** Browser context menu for long-pressed links and media. Text selection uses Gecko's selection delegate. */
class GeckoContextMenuController(
    private val activity: Activity,
    private val openTab: (String, Boolean) -> Unit,
) {
    fun show(element: GeckoSession.ContentDelegate.ContextElement, private: Boolean) {
        val link = element.linkUri?.takeIf { it.isNotBlank() }
        val media = element.srcUri?.takeIf { it.isNotBlank() && it != link }
        if (link == null && media == null) return

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            val labels = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()

            if (link != null) {
                if (isAllowedWebUri(link)) {
                    labels += activity.getString(R.string.context_open_background_tab)
                    actions += { openBackground(link, private) }
                    if (!private) {
                        labels += activity.getString(R.string.context_open_private_tab)
                        actions += { openTab(link, true) }
                    }
                }
                labels += activity.getString(R.string.context_copy_link)
                actions += { copy(link, R.string.clipboard_label_link) }
                labels += activity.getString(R.string.context_share_link)
                actions += { share(link) }
            }

            if (media != null) {
                if (isAllowedWebUri(media)) {
                    labels += activity.getString(
                        if (link == null) R.string.context_open_background_tab else R.string.context_open_media_background_tab,
                    )
                    actions += { openBackground(media, private) }
                    if (!private) {
                        labels += activity.getString(R.string.context_open_media_private_tab)
                        actions += { openTab(media, true) }
                    }
                }
                labels += activity.getString(
                    if (link == null) R.string.context_copy_address else R.string.context_copy_media_address,
                )
                actions += { copy(media, R.string.clipboard_label_address) }
                labels += activity.getString(R.string.context_share_media)
                actions += { share(media) }
            }

            if (labels.isNotEmpty()) {
                runCatching {
                    AlertDialog.Builder(activity)
                        .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
                        .show()
                }.onFailure { error ->
                    Log.w("MinibrowserContext", "Failed to show context menu", error)
                }
            }
        }
    }

    private fun openBackground(value: String, private: Boolean) {
        val host = activity as? BackgroundTabHost
        if (host != null) {
            host.openBackgroundTab(value, private)
        } else {
            openTab(value, private)
        }
    }

    private fun copy(value: String, @StringRes labelRes: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(labelRes), value))
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun share(value: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, value)
        runCatching {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_chooser_title)))
        }.onFailure { error ->
            Log.w("MinibrowserContext", "Failed to share context target", error)
        }
    }
}

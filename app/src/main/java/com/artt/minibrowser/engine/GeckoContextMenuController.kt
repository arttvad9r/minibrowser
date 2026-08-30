package com.artt.minibrowser.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import com.artt.minibrowser.R
import org.mozilla.geckoview.GeckoSession

/** Minimal browser context menu for long-pressed links and media. */
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
                    labels += activity.getString(R.string.context_open_new_tab)
                    actions += { openTab(link, private) }
                }
                labels += activity.getString(R.string.context_copy_link)
                actions += { copy(link, R.string.clipboard_label_link) }
            }

            if (media != null) {
                if (isAllowedWebUri(media)) {
                    labels += activity.getString(
                        if (link == null) R.string.context_open_new_tab else R.string.context_open_media_new_tab,
                    )
                    actions += { openTab(media, private) }
                }
                labels += activity.getString(
                    if (link == null) R.string.context_copy_address else R.string.context_copy_media_address,
                )
                actions += { copy(media, R.string.clipboard_label_address) }
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

    private fun copy(value: String, @StringRes labelRes: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(labelRes), value))
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
}

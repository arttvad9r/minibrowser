package com.artt.minibrowser.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
            val labels = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()

            if (link != null) {
                if (isAllowedWebUri(link)) {
                    labels += "Открыть в новой вкладке"
                    actions += { openTab(link, private) }
                }
                labels += "Копировать ссылку"
                actions += { copy(link, "Ссылка") }
            }

            if (media != null) {
                if (isAllowedWebUri(media)) {
                    labels += if (link == null) "Открыть в новой вкладке" else "Открыть медиа в новой вкладке"
                    actions += { openTab(media, private) }
                }
                labels += if (link == null) "Копировать адрес" else "Копировать адрес медиа"
                actions += { copy(media, "Адрес") }
            }

            if (labels.isNotEmpty()) {
                AlertDialog.Builder(activity)
                    .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
                    .show()
            }
        }
    }

    private fun copy(value: String, label: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(activity, "Скопировано", Toast.LENGTH_SHORT).show()
    }
}

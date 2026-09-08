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
interface BackgroundTabHost {
    fun openBackgroundTab(uri: String, private: Boolean)
}

/** Browser context menu for long-pressed links and media. Text selection uses Gecko's selection delegate. */
class GeckoContextMenuController(
    private val activity: Activity,
    private val openTab: (String, Boolean) -> Unit,
) {
    fun show(element: GeckoSession.ContentDelegate.ContextElement, private: Boolean) {
        val items = contextMenuPolicyItems(
            linkUri = element.linkUri,
            mediaUri = element.srcUri,
            isPrivate = private,
        )
        if (items.isEmpty()) return

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            val labels = items.map { item -> activity.getString(item.labelRes()) }
            runCatching {
                AlertDialog.Builder(activity)
                    .setItems(labels.toTypedArray()) { _, which -> execute(items[which]) }
                    .show()
            }.onFailure { error ->
                Log.w("MinibrowserContext", "Failed to show context menu", error)
            }
        }
    }

    private fun execute(item: ContextMenuPolicyItem) {
        when (item.operation) {
            ContextMenuOperation.OPEN_BACKGROUND -> openBackground(
                item.uri,
                requireNotNull(item.privateMode),
            )
            ContextMenuOperation.OPEN_PRIVATE -> openTab(item.uri, requireNotNull(item.privateMode))
            ContextMenuOperation.COPY -> copy(
                item.uri,
                if (item.targetKind == ContextMenuTargetKind.LINK) {
                    R.string.clipboard_label_link
                } else {
                    R.string.clipboard_label_address
                },
            )
            ContextMenuOperation.SHARE -> share(item.uri)
        }
    }

    @StringRes
    private fun ContextMenuPolicyItem.labelRes(): Int = when (targetKind) {
        ContextMenuTargetKind.LINK -> when (operation) {
            ContextMenuOperation.OPEN_BACKGROUND -> R.string.context_open_background_tab
            ContextMenuOperation.OPEN_PRIVATE -> R.string.context_open_private_tab
            ContextMenuOperation.COPY -> R.string.context_copy_link
            ContextMenuOperation.SHARE -> R.string.context_share_link
        }
        ContextMenuTargetKind.MEDIA -> when (operation) {
            ContextMenuOperation.OPEN_BACKGROUND -> if (pairedWithLink) {
                R.string.context_open_media_background_tab
            } else {
                R.string.context_open_background_tab
            }
            ContextMenuOperation.OPEN_PRIVATE -> R.string.context_open_media_private_tab
            ContextMenuOperation.COPY -> if (pairedWithLink) {
                R.string.context_copy_media_address
            } else {
                R.string.context_copy_address
            }
            ContextMenuOperation.SHARE -> R.string.context_share_media
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

package com.artt.minibrowser.engine

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

// id — фактические из manifest.json ассетов (см. scripts/fetch-extensions.sh).
object ExtensionLoader {
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    const val VOT_ID = "vot-ext@firefox"

    fun installAll(runtime: GeckoRuntime, adblockEnabled: Boolean) {
        val c = runtime.webExtensionController
        c.ensureBuiltIn("resource://android/assets/extensions/ublock/", UBLOCK_ID)
            .accept { ext -> ext?.let { setEnabled(c, it, adblockEnabled) } }
        c.ensureBuiltIn("resource://android/assets/extensions/vot/", VOT_ID)
            .accept { _: org.mozilla.geckoview.WebExtension? -> }
    }

    fun setAdblock(runtime: GeckoRuntime, enabled: Boolean) {
        runtime.webExtensionController.list().accept { list ->
            list?.firstOrNull { it.id == UBLOCK_ID }?.let { setEnabled(runtime.webExtensionController, it, enabled) }
        }
    }

    private fun setEnabled(c: WebExtensionController, ext: org.mozilla.geckoview.WebExtension, enabled: Boolean) {
        val src = WebExtensionController.EnableSource.APP
        if (enabled) c.enable(ext, src) else c.disable(ext, src)
    }
}

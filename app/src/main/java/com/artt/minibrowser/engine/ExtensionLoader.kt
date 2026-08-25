package com.artt.minibrowser.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

// id — фактические из manifest.json ассетов (см. scripts/fetch-extensions.sh).
object ExtensionLoader {
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    const val VOT_ID = "vot-ext@firefox"
    enum class Status { Installing, Enabled, Disabled, Error }
    data class ExtensionState(val status: Status, val error: String? = null)
    private val _state = MutableStateFlow<Map<String, ExtensionState>>(emptyMap())
    val state: StateFlow<Map<String, ExtensionState>> = _state.asStateFlow()

    fun installAll(runtime: GeckoRuntime, adblockEnabled: Boolean) {
        val c = runtime.webExtensionController
        setState(UBLOCK_ID, Status.Installing)
        c.ensureBuiltIn("resource://android/assets/extensions/ublock/", UBLOCK_ID)
            .accept(
                { ext ->
                    if (ext == null) setState(UBLOCK_ID, Status.Error, "uBlock installation returned no extension")
                    else setEnabled(c, ext, adblockEnabled)
                },
                { error -> setState(UBLOCK_ID, Status.Error, error?.message ?: "uBlock installation failed") },
            )
        setState(VOT_ID, Status.Installing)
        c.ensureBuiltIn("resource://android/assets/extensions/vot/", VOT_ID)
            .accept(
                { ext -> if (ext == null) setState(VOT_ID, Status.Error, "VOT installation returned no extension") else setState(VOT_ID, Status.Enabled) },
                { error -> setState(VOT_ID, Status.Error, error?.message ?: "VOT installation failed") },
            )
    }

    fun setAdblock(runtime: GeckoRuntime, enabled: Boolean) {
        runtime.webExtensionController.list().accept { list ->
            list?.firstOrNull { it.id == UBLOCK_ID }?.let { setEnabled(runtime.webExtensionController, it, enabled) }
                ?: setState(UBLOCK_ID, Status.Error, "uBlock is not installed")
        }
    }

    private fun setEnabled(c: WebExtensionController, ext: org.mozilla.geckoview.WebExtension, enabled: Boolean) {
        val src = WebExtensionController.EnableSource.APP
        val result = if (enabled) c.enable(ext, src) else c.disable(ext, src)
        result.accept(
            { setState(UBLOCK_ID, if (enabled) Status.Enabled else Status.Disabled) },
            { error -> setState(UBLOCK_ID, Status.Error, error?.message ?: "uBlock state change failed") },
        )
    }

    private fun setState(id: String, status: Status, error: String? = null) {
        if (error != null) Log.e("MinibrowserExtension", "$id: $error")
        _state.value = _state.value + (id to ExtensionState(status, error))
    }
}

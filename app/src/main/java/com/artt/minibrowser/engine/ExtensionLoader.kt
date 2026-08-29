package com.artt.minibrowser.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

// id — фактические из manifest.json ассетов (см. scripts/fetch-extensions.sh).
object ExtensionLoader {
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    const val VOT_ID = "vot-ext@firefox"
    const val CHATGPT_VIEWPORT_ID = "chatgpt-viewport@minibrowser"
    enum class Status { Installing, Enabled, Disabled, Error }
    data class ExtensionState(val status: Status, val error: String? = null)
    private val _state = MutableStateFlow<Map<String, ExtensionState>>(emptyMap())
    val state: StateFlow<Map<String, ExtensionState>> = _state.asStateFlow()

    fun installAll(runtime: GeckoRuntime, adblockEnabled: Boolean, votEnabled: Boolean) {
        installBuiltIn(runtime, UBLOCK_ID, "resource://android/assets/extensions/ublock/", adblockEnabled)
        installBuiltIn(runtime, VOT_ID, "resource://android/assets/extensions/vot/", votEnabled)
        installBuiltIn(
            runtime,
            CHATGPT_VIEWPORT_ID,
            "resource://android/assets/extensions/chatgpt-viewport/",
            true,
        )
    }

    fun setAdblock(runtime: GeckoRuntime, enabled: Boolean) {
        setInstalled(runtime, UBLOCK_ID, enabled)
    }

    fun setVot(runtime: GeckoRuntime, enabled: Boolean) {
        setInstalled(runtime, VOT_ID, enabled)
    }

    fun retryAdblock(runtime: GeckoRuntime, desiredEnabled: Boolean) {
        installBuiltIn(runtime, UBLOCK_ID, "resource://android/assets/extensions/ublock/", desiredEnabled)
    }

    fun retryVot(runtime: GeckoRuntime, desiredEnabled: Boolean) {
        installBuiltIn(runtime, VOT_ID, "resource://android/assets/extensions/vot/", desiredEnabled)
    }

    internal fun privateAllowedInPrivate(id: String): Boolean =
        id == UBLOCK_ID || id == CHATGPT_VIEWPORT_ID

    private fun installBuiltIn(runtime: GeckoRuntime, id: String, resource: String, enabled: Boolean) {
        val c = runtime.webExtensionController
        setState(id, Status.Installing)
        c.ensureBuiltIn(resource, id)
            .accept(
                { ext ->
                    if (ext == null) {
                        setState(id, Status.Error, "$id installation returned no extension")
                    } else {
                        applyExtensionPolicy(c, ext, id, enabled)
                    }
                },
                { error -> setState(id, Status.Error, error?.message ?: "$id installation failed") },
            )
    }

    private fun setInstalled(runtime: GeckoRuntime, id: String, enabled: Boolean) {
        val c = runtime.webExtensionController
        c.list().accept(
            { list ->
                list?.firstOrNull { it.id == id }?.let { applyExtensionPolicy(c, it, id, enabled) }
                    ?: setState(id, Status.Error, "$id is not installed")
            },
            { error -> setState(id, Status.Error, error?.message ?: "$id extension list failed") },
        )
    }

    private fun applyExtensionPolicy(c: WebExtensionController, ext: org.mozilla.geckoview.WebExtension, id: String, enabled: Boolean) {
        c.setAllowedInPrivateBrowsing(ext, privateAllowedInPrivate(id)).accept(
            { updated ->
                updated?.let { setEnabled(c, it, id, enabled) }
                    ?: setState(id, Status.Error, "$id private browsing policy returned no extension")
            },
            { error -> setState(id, Status.Error, error?.message ?: "$id private browsing policy failed") },
        )
    }

    private fun setEnabled(c: WebExtensionController, ext: org.mozilla.geckoview.WebExtension, id: String, enabled: Boolean) {
        val src = WebExtensionController.EnableSource.APP
        val result = if (enabled) c.enable(ext, src) else c.disable(ext, src)
        result.accept(
            { setState(id, if (enabled) Status.Enabled else Status.Disabled) },
            { error -> setState(id, Status.Error, error?.message ?: "$id state change failed") },
        )
    }

    private fun setState(id: String, status: Status, error: String? = null) {
        if (error != null) Log.e("MinibrowserExtension", "$id: $error")
        _state.update { it + (id to ExtensionState(status, error)) }
    }
}

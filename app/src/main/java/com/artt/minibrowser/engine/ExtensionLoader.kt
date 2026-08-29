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

    private data class DesiredState(val enabled: Boolean, val generation: Long)

    private val _state = MutableStateFlow<Map<String, ExtensionState>>(emptyMap())
    val state: StateFlow<Map<String, ExtensionState>> = _state.asStateFlow()

    private val policyLock = Any()
    private val desired = mutableMapOf<String, DesiredState>()
    private val activeInstalls = mutableMapOf<String, Long>()
    private var nextPolicyGeneration = 1L
    private var nextInstallGeneration = 1L

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
        val controller = runtime.webExtensionController
        val desiredState = recordDesired(id, enabled)
        val installGeneration = beginInstall(id)
        setStateIfCurrent(id, desiredState.generation, Status.Installing)

        controller.ensureBuiltIn(resource, id).accept(
            { extension ->
                if (!finishInstall(id, installGeneration)) return@accept
                val latest = latestDesired(id) ?: return@accept
                if (extension == null) {
                    setStateIfCurrent(
                        id,
                        latest.generation,
                        Status.Error,
                        "$id installation returned no extension",
                    )
                } else {
                    applyExtensionPolicy(controller, extension, id, latest.generation)
                }
            },
            { error ->
                if (!finishInstall(id, installGeneration)) return@accept
                val latest = latestDesired(id) ?: return@accept
                setStateIfCurrent(
                    id,
                    latest.generation,
                    Status.Error,
                    error?.message ?: "$id installation failed",
                )
            },
        )
    }

    private fun setInstalled(runtime: GeckoRuntime, id: String, enabled: Boolean) {
        val controller = runtime.webExtensionController
        val desiredState = recordDesired(id, enabled)
        setStateIfCurrent(id, desiredState.generation, Status.Installing)
        reconcileInstalled(controller, id, desiredState.generation)
    }

    private fun reconcileInstalled(
        controller: WebExtensionController,
        id: String,
        generation: Long = latestDesired(id)?.generation ?: return,
    ) {
        if (!isCurrent(id, generation)) return
        controller.list().accept(
            { list ->
                if (!isCurrent(id, generation)) return@accept
                val extension = list?.firstOrNull { it.id == id }
                when {
                    extension != null -> applyExtensionPolicy(controller, extension, id, generation)
                    isInstallActive(id) -> setStateIfCurrent(id, generation, Status.Installing)
                    else -> setStateIfCurrent(id, generation, Status.Error, "$id is not installed")
                }
            },
            { error ->
                setStateIfCurrent(
                    id,
                    generation,
                    Status.Error,
                    error?.message ?: "$id extension list failed",
                )
            },
        )
    }

    private fun applyExtensionPolicy(
        controller: WebExtensionController,
        extension: org.mozilla.geckoview.WebExtension,
        id: String,
        generation: Long,
    ) {
        if (!isCurrent(id, generation)) return
        controller.setAllowedInPrivateBrowsing(extension, privateAllowedInPrivate(id)).accept(
            { updated ->
                if (!isCurrent(id, generation)) return@accept
                if (updated == null) {
                    setStateIfCurrent(
                        id,
                        generation,
                        Status.Error,
                        "$id private browsing policy returned no extension",
                    )
                } else {
                    setEnabled(controller, updated, id, generation)
                }
            },
            { error ->
                setStateIfCurrent(
                    id,
                    generation,
                    Status.Error,
                    error?.message ?: "$id private browsing policy failed",
                )
            },
        )
    }

    private fun setEnabled(
        controller: WebExtensionController,
        extension: org.mozilla.geckoview.WebExtension,
        id: String,
        generation: Long,
    ) {
        val desiredState = desiredForGeneration(id, generation) ?: return
        val source = WebExtensionController.EnableSource.APP
        val result = if (desiredState.enabled) {
            controller.enable(extension, source)
        } else {
            controller.disable(extension, source)
        }
        result.accept(
            {
                if (isCurrent(id, generation)) {
                    setState(
                        id,
                        if (desiredState.enabled) Status.Enabled else Status.Disabled,
                    )
                } else {
                    // An older Gecko operation may have completed after a newer toggle. Re-apply
                    // the latest desired state so the actual extension state converges as well.
                    reconcileInstalled(controller, id)
                }
            },
            { error ->
                if (isCurrent(id, generation)) {
                    setState(
                        id,
                        Status.Error,
                        error?.message ?: "$id state change failed",
                    )
                } else {
                    reconcileInstalled(controller, id)
                }
            },
        )
    }

    private fun recordDesired(id: String, enabled: Boolean): DesiredState = synchronized(policyLock) {
        DesiredState(enabled, nextPolicyGeneration++).also { desired[id] = it }
    }

    private fun latestDesired(id: String): DesiredState? = synchronized(policyLock) { desired[id] }

    private fun desiredForGeneration(id: String, generation: Long): DesiredState? = synchronized(policyLock) {
        desired[id]?.takeIf { it.generation == generation }
    }

    private fun isCurrent(id: String, generation: Long): Boolean = synchronized(policyLock) {
        desired[id]?.generation == generation
    }

    private fun beginInstall(id: String): Long = synchronized(policyLock) {
        nextInstallGeneration++.also { activeInstalls[id] = it }
    }

    private fun finishInstall(id: String, generation: Long): Boolean = synchronized(policyLock) {
        if (activeInstalls[id] != generation) {
            false
        } else {
            activeInstalls.remove(id)
            true
        }
    }

    private fun isInstallActive(id: String): Boolean = synchronized(policyLock) {
        activeInstalls.containsKey(id)
    }

    private fun setStateIfCurrent(
        id: String,
        generation: Long,
        status: Status,
        error: String? = null,
    ) {
        if (isCurrent(id, generation)) setState(id, status, error)
    }

    private fun setState(id: String, status: Status, error: String? = null) {
        if (error != null) Log.e("MinibrowserExtension", "$id: $error")
        _state.update { it + (id to ExtensionState(status, error)) }
    }
}

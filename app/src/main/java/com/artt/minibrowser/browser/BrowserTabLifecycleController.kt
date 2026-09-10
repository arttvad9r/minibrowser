package com.artt.minibrowser.browser

import android.app.Activity
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.ExternalAppRequestHandler
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.bindTabManagerToBrowserStore
import com.artt.minibrowser.engine.closeBrowserSessionsForFinalActivityDestroy
import com.artt.minibrowser.engine.requestPersistForAndroidComponentsSessionStateChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.AppLifecycleAction

/** Keeps Gecko tab visibility, persistence, and background trimming aligned with host lifecycle. */
internal class BrowserTabLifecycleController(
    owner: LifecycleOwner,
    private val tabManager: TabManager,
) : DefaultLifecycleObserver {
    private val lifecycle: Lifecycle = owner.lifecycle
    private val activity = owner as? Activity
    private val app = activity?.application as? BrowserApp
    private val externalAppRequestHandler = activity?.let(::ExternalAppRequestHandler)
    private val androidComponentsBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        app?.let { browserApp ->
            androidComponentsBridgeScope.bindTabManagerToBrowserStore(
                tabManager = tabManager,
                store = browserApp.browserStore,
                engine = browserApp.engine,
            )
            androidComponentsBridgeScope.launch {
                // The current value may have been seeded from the cold-process restore snapshot.
                // Only later A-C callback updates/invalidation should wake the debounced disk writer.
                browserApp.sessionStatePersistence.snapshots
                    .drop(1)
                    .collect { tabManager.requestPersistForAndroidComponentsSessionStateChange() }
            }
        }
        // Lifecycle.addObserver() brings this observer up to the owner's current state, so an
        // asynchronously-created controller still receives any required onStart/onResume callbacks.
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        bindExternalNavigationPolicy()
    }

    override fun onResume(owner: LifecycleOwner) {
        val browserApp = app
        browserApp?.browserStore?.dispatch(AppLifecycleAction.ResumeAction)
        tabManager.setAppVisible(true)
        if (browserApp != null) transferCurrentRawTabWhenMirrored(browserApp)
    }

    override fun onPause(owner: LifecycleOwner) {
        app?.browserStore?.dispatch(AppLifecycleAction.PauseAction)
        val inPictureInPicture = (owner as? Activity)?.isInPictureInPictureMode == true
        tabManager.setAppVisible(inPictureInPicture)
        tabManager.persist()
    }

    override fun onStop(owner: LifecycleOwner) {
        unbindExternalNavigationPolicy()
        val inPictureInPicture = (owner as? Activity)?.isInPictureInPictureMode == true
        if (!inPictureInPicture) {
            // onPause can still observe PiP=true while the user dismisses the PiP window. onStop is
            // the final signal that the browser is actually backgrounded, so deactivate Gecko here.
            tabManager.setAppVisible(false)
            tabManager.trimForBackground()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unbindExternalNavigationPolicy()
        androidComponentsBridgeScope.cancel()

        // Configuration change retains exact Tab/GeckoSession ownership through the process-local
        // handoff. A real final destroy instead closes both raw and linked owners after TabManager's
        // final persistence snapshot. Calling TabManager.close() here is safe even if its own
        // lifecycle observer already ran: close() is idempotent, while linked cleanup still proceeds.
        if (activity?.isChangingConfigurations != true) {
            val browserApp = app
            if (browserApp != null) {
                closeBrowserSessionsForFinalActivityDestroy(tabManager, browserApp.browserStore)
            } else {
                tabManager.close()
            }
        }

        lifecycle.removeObserver(this)
    }

    private fun transferCurrentRawTabWhenMirrored(browserApp: BrowserApp) {
        androidComponentsBridgeScope.launch {
            browserApp.browserStore.stateFlow.first { state ->
                val currentId = tabManager.currentId.value?.toString()
                currentId != null &&
                    state.selectedTabId == currentId &&
                    state.tabs.any { it.id == currentId }
            }

            val tab = tabManager.current() ?: return@launch
            if (!tab.hasRawSessionAuthority) return@launch
            val rawSession = tab.rawSessionOrNull ?: return@launch
            if (!rawSession.isOpen) return@launch

            val storeTab = browserApp.browserStore.state.tabs
                .firstOrNull { it.id == tab.id.toString() }
                ?: return@launch
            if (storeTab.engineState.engineSession != null) return@launch

            runCatching {
                browserApp.transferExistingTabToAndroidComponents(tabManager, tab)
            }.onFailure { error ->
                Log.e("MinibrowserTabs", "Failed to transfer selected tab to Android Components", error)
            }
        }
    }

    private fun bindExternalNavigationPolicy() {
        val handler = externalAppRequestHandler ?: return
        app?.externalAppNavigationPolicyRegistry?.bind(handler)
    }

    private fun unbindExternalNavigationPolicy() {
        val handler = externalAppRequestHandler ?: return
        app?.externalAppNavigationPolicyRegistry?.unbind(handler)
    }
}

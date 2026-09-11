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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.lib.state.ext.flow
import org.mozilla.geckoview.GeckoSession

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
            androidComponentsBridgeScope.launch {
                // SessionFeature can create a previously-suspended selected EngineSession after
                // TabManager.select() has returned. Re-run the shared budget when linked-session
                // topology or loading eligibility changes so that rewarm cannot escape the limit.
                browserApp.browserStore.stateFlow
                    .map { state ->
                        state.tabs.mapNotNull { tab ->
                            if (tab.engineState.engineSession == null) {
                                null
                            } else {
                                tab.id to tab.content.loading
                            }
                        }
                    }
                    .distinctUntilChanged()
                    .collect { tabManager.enforceHotTabBudget() }
            }
            androidComponentsBridgeScope.launch {
                // Selected raw tabs are transferred only at an idle page boundary. This state signal
                // supplies deterministic retries for cases where active navigation must keep its raw
                // delegates through PageStop. Once an EngineSession is linked this maps to null, so
                // ordinary A-C progress/content churn does not keep retrying the transfer path.
                browserApp.browserStore.stateFlow
                    .map { state ->
                        val selected = state.tabs.firstOrNull { it.id == state.selectedTabId }
                        if (selected == null || selected.engineState.engineSession != null) {
                            null
                        } else {
                            Triple(selected.id, selected.content.url, selected.content.loading)
                        }
                    }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { rawSelected ->
                        if (
                            rawSelected != null &&
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        ) {
                            transferCurrentRawTabWhenMirrored(browserApp)
                        }
                    }
            }
            androidComponentsBridgeScope.launch {
                // The initial current tab is handled by onResume. Later selections cross ownership
                // only while the Activity is resumed; a paused selection is handled on next resume.
                tabManager.currentId
                    .drop(1)
                    .collect {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            transferCurrentRawTabWhenMirrored(browserApp)
                        }
                    }
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

    internal fun transferOpenedWindowSession(session: GeckoSession) {
        val browserApp = app ?: return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val current = tabManager.current() ?: return
        if (!current.ownsRawSession(session)) return
        transferCurrentRawTabWhenMirrored(browserApp, expectedSession = session)
    }

    private fun transferCurrentRawTabWhenMirrored(
        browserApp: BrowserApp,
        expectedSession: GeckoSession? = null,
    ) {
        androidComponentsBridgeScope.launch {
            browserApp.browserStore.flow().first { state ->
                val currentId = tabManager.currentId.value?.toString()
                currentId != null &&
                    state.selectedTabId == currentId &&
                    state.tabs.any { it.id == currentId }
            }

            val tab = tabManager.current() ?: return@launch
            if (!tab.hasRawSessionAuthority) return@launch
            if (expectedSession != null && !tab.ownsRawSession(expectedSession)) return@launch
            val rawSession = tab.rawSessionOrNull ?: return@launch
            if (!rawSession.isOpen) return@launch
            // GeckoSession.open() can finish a synthetic about:blank page after a real initial
            // loadUri() has already been accepted. Do not mistake that startup PageStop for the
            // target page's idle boundary. Once the first real PageStart arrives, progress keeps
            // raw delegates installed until the matching PageStop as before.
            if (tab.awaitingInitialNonBlankPageStart || tab.progress >= 0f) return@launch

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
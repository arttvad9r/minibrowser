package com.artt.minibrowser.browser

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.ExternalAppRequestHandler
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.bindTabManagerToBrowserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        }
        // Lifecycle.addObserver() brings this observer up to the owner's current state, so an
        // asynchronously-created controller still receives any required onStart/onResume callbacks.
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        bindExternalNavigationPolicy()
    }

    override fun onResume(owner: LifecycleOwner) {
        app?.browserStore?.dispatch(AppLifecycleAction.ResumeAction)
        tabManager.setAppVisible(true)
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
        // TabManager still owns its final persistence/session shutdown. This observer owns only
        // lifecycle signals plus the temporary BrowserStore shadow-state bridge.
        unbindExternalNavigationPolicy()
        androidComponentsBridgeScope.cancel()
        lifecycle.removeObserver(this)
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

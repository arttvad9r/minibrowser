package com.artt.minibrowser.browser

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.bindTabManagerToBrowserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Keeps Gecko tab visibility, persistence, and background trimming aligned with host lifecycle. */
internal class BrowserTabLifecycleController(
    owner: LifecycleOwner,
    private val tabManager: TabManager,
) : DefaultLifecycleObserver {
    private val lifecycle: Lifecycle = owner.lifecycle
    private val androidComponentsBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        lifecycle.addObserver(this)
        ((owner as? Activity)?.application as? BrowserApp)?.let { app ->
            androidComponentsBridgeScope.bindTabManagerToBrowserStore(tabManager, app.browserStore)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        tabManager.setAppVisible(true)
    }

    override fun onPause(owner: LifecycleOwner) {
        val inPictureInPicture = (owner as? Activity)?.isInPictureInPictureMode == true
        tabManager.setAppVisible(inPictureInPicture)
        tabManager.persist()
    }

    override fun onStop(owner: LifecycleOwner) {
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
        androidComponentsBridgeScope.cancel()
        lifecycle.removeObserver(this)
    }
}

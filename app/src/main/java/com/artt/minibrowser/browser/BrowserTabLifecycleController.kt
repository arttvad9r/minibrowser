package com.artt.minibrowser.browser

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.engine.TabManager

/** Keeps Gecko tab visibility, persistence, and background trimming aligned with host lifecycle. */
internal class BrowserTabLifecycleController(
    owner: LifecycleOwner,
    private val tabManager: TabManager,
) : DefaultLifecycleObserver {
    private val lifecycle: Lifecycle = owner.lifecycle

    init {
        lifecycle.addObserver(this)
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
        // TabManager still owns its final persistence/session shutdown. This observer only owns the
        // visibility/background lifecycle signals and therefore unregisters itself here.
        lifecycle.removeObserver(this)
    }
}

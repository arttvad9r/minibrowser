package com.artt.minibrowser.browser

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.engine.TabManager

/** Keeps Gecko tab visibility, persistence, and background trimming aligned with the host lifecycle. */
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
        tabManager.setAppVisible(false)
        tabManager.persist()
    }

    override fun onStop(owner: LifecycleOwner) {
        tabManager.trimForBackground()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        lifecycle.removeObserver(this)
    }
}

package com.artt.minibrowser.browser

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.artt.minibrowser.engine.TabManager

/** Keeps Gecko tab visibility, persistence, and background trimming aligned with the host lifecycle. */
internal class BrowserTabLifecycleController(
    owner: LifecycleOwner,
    private val tabManager: TabManager,
) : DefaultLifecycleObserver {
    init {
        owner.lifecycle.addObserver(this)
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
}

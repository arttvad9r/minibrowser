package com.artt.minibrowser.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import mozilla.components.support.base.feature.LifecycleAwareFeature

/**
 * Attaches an A-C LifecycleAwareFeature to the current View-tree lifecycle.
 *
 * Lifecycle normally drives start/stop. The explicit stop on detach covers the View-specific case
 * where an observer is removed while its owner is still STARTED; Lifecycle does not synthesize an
 * onStop callback for removed observers.
 */
internal class LinkedSessionFeatureLifecycleBinding(
    private val feature: LifecycleAwareFeature,
) {
    private var owner: LifecycleOwner? = null

    fun attach(nextOwner: LifecycleOwner?) {
        if (owner === nextOwner) return
        detach()
        if (nextOwner == null) return

        owner = nextOwner
        nextOwner.lifecycle.addObserver(feature)
    }

    fun detach() {
        val previousOwner = owner ?: return
        val wasStarted = previousOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        previousOwner.lifecycle.removeObserver(feature)
        owner = null
        if (wasStarted) {
            feature.stop()
        }
    }
}

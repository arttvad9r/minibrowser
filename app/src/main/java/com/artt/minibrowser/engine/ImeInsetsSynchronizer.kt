/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.artt.minibrowser.engine

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars

/**
 * Firefox Android's IME/window-insets synchronization strategy, vendored from
 * Mozilla Android Components and kept intentionally close to upstream.
 *
 * Persistent system-bar insets are expected to be handled separately. This helper only handles
 * dynamic IME insets and avoids resizing the browser on every animation frame unless requested.
 */
class ImeInsetsSynchronizer private constructor(
    private val targetView: View,
    private val insetsSource: View,
    private val synchronizeViewWithIME: Boolean,
    private val onIMEAnimationStarted: (Boolean, Int) -> Unit,
    private val onIMEAnimationFinished: (Boolean, Int) -> Unit,
) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE), OnApplyWindowInsetsListener {

    init {
        ViewCompat.setWindowInsetsAnimationCallback(insetsSource, this)
        ViewCompat.setOnApplyWindowInsetsListener(insetsSource, this)
    }

    private lateinit var lastWindowInsets: WindowInsetsCompat
    private var areKeyboardInsetsDeferred = false
    private var isKeyboardShowingUp = true
    private var keyboardAnimationInProgress = false
    private var keyboardHeight = 0

    override fun onApplyWindowInsets(view: View, windowInsets: WindowInsetsCompat): WindowInsetsCompat {
        lastWindowInsets = windowInsets
        isKeyboardShowingUp = windowInsets.isVisible(ime())

        if (!areKeyboardInsetsDeferred) {
            val bottomMargin = calculateBottomMargin(
                windowInsets.getInsets(ime()).bottom,
                getNavbarHeight(),
            )
            updateTargetBottomMargin(bottomMargin)
            onIMEAnimationFinished(isKeyboardShowingUp, bottomMargin)
        }

        return windowInsets
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and ime() != 0) {
            areKeyboardInsetsDeferred = true
        }
    }

    override fun onStart(
        animation: WindowInsetsAnimationCompat,
        bounds: WindowInsetsAnimationCompat.BoundsCompat,
    ): WindowInsetsAnimationCompat.BoundsCompat {
        if (animation.typeMask and ime() != 0) {
            keyboardAnimationInProgress = true

            // Workaround for Android issue 361027506: use animation bounds for IME height.
            keyboardHeight = bounds.upperBound.bottom - bounds.lowerBound.bottom

            // Workaround for Android issue 369223558: an IME must be taller than the nav bar.
            if (keyboardHeight <= getNavbarHeight()) {
                keyboardHeight = 0
            }

            onIMEAnimationStarted(
                isKeyboardShowingUp,
                calculateBottomMargin(keyboardHeight, getNavbarHeight()),
            )
        }

        return super.onStart(animation, bounds)
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: List<WindowInsetsAnimationCompat>,
    ): WindowInsetsCompat {
        if (!keyboardAnimationInProgress) return insets

        runningAnimations
            .firstOrNull { it.typeMask and ime() != 0 }
            ?.let { imeAnimation ->
                val fraction = if (isKeyboardShowingUp) {
                    imeAnimation.interpolatedFraction
                } else {
                    1 - imeAnimation.interpolatedFraction
                }

                updateTargetBottomMargin(
                    calculateBottomMargin(
                        (keyboardHeight * fraction).toInt(),
                        getNavbarHeight(),
                    ),
                )
            }

        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        keyboardAnimationInProgress = false

        val currentInsets = getCurrentInsets()
        if (currentInsets != null && areKeyboardInsetsDeferred && animation.typeMask and ime() != 0) {
            areKeyboardInsetsDeferred = false
            ViewCompat.dispatchApplyWindowInsets(insetsSource, currentInsets)
        }
    }

    private fun getNavbarHeight(): Int =
        ViewCompat.getRootWindowInsets(insetsSource)?.getInsets(systemBars())?.bottom
            ?: if (::lastWindowInsets.isInitialized && lastWindowInsets.isVisible(ime())) {
                lastWindowInsets.getInsets(systemBars()).bottom
            } else {
                0
            }

    private fun getCurrentInsets(): WindowInsetsCompat? =
        if (::lastWindowInsets.isInitialized) lastWindowInsets
        else ViewCompat.getRootWindowInsets(insetsSource)

    private fun calculateBottomMargin(keyboardHeight: Int, navigationBarHeight: Int): Int =
        (keyboardHeight - navigationBarHeight).coerceAtLeast(0)

    private fun updateTargetBottomMargin(bottom: Int) {
        if (!synchronizeViewWithIME) return
        val params = targetView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin == bottom) return
        params.bottomMargin = bottom
        targetView.layoutParams = params
        targetView.requestLayout()
    }

    companion object {
        fun setup(
            targetView: View,
            insetsSource: View = targetView,
            synchronizeViewWithIME: Boolean = true,
            onIMEAnimationStarted: (Boolean, Int) -> Unit = { _, _ -> },
            onIMEAnimationFinished: (Boolean, Int) -> Unit = { _, _ -> },
        ): ImeInsetsSynchronizer? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ImeInsetsSynchronizer(
                    targetView = targetView,
                    insetsSource = insetsSource,
                    synchronizeViewWithIME = synchronizeViewWithIME,
                    onIMEAnimationStarted = onIMEAnimationStarted,
                    onIMEAnimationFinished = onIMEAnimationFinished,
                )
            } else {
                null
            }
    }
}

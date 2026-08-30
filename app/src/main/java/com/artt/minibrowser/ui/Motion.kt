package com.artt.minibrowser.ui

import android.os.Build
import android.view.View
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** Shared timing vocabulary; app-level motion is intentionally minimal. */
object MotionTokens {
    // Full-screen chrome needs enough real frames to read as motion rather than a jump.
    // The duration is refresh-rate independent: 180 ms is ~11 frames at 60 Hz and ~22 at 120 Hz.
    const val Screen = 180
}

/**
 * Ask Android 15+ for the high frame-rate category only while app-chrome motion is actually
 * producing frames. The hint is applied to the Compose host View only, so it does not override the
 * independently rendered GeckoView child. Restoring the previous vote lets Android return to its
 * normal/adaptive refresh policy as soon as the transition is finished.
 */
@Composable
fun HighFrameRateDuringMotion(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && active) {
            val previousFrameRate = view.requestedFrameRate
            view.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            onDispose { view.requestedFrameRate = previousFrameRate }
        } else {
            onDispose { }
        }
    }
}

/** App-chrome click using the platform/Material indication and button semantics. */
@Composable
fun Modifier.softClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )
}

/** Same single-indication behavior while preserving long-click and button semantics. */
@Composable
fun Modifier.softCombinedClickable(
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return combinedClickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        role = Role.Button,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

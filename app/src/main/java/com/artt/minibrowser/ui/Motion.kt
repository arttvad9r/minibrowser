package com.artt.minibrowser.ui

import android.os.Build
import android.view.View
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

/** Small shared timing vocabulary; screen-level motion is intentionally minimal. */
object MotionTokens {
    const val Quick = 120
    const val Standard = 190
    const val Emphasis = 280

    val StandardSpatial = standardSpatialSpring<Float>()
    val ExpressiveSpatial = expressiveSpatialSpring<Float>()
}

fun <T> standardSpatialSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.86f,
    stiffness = 430f,
)

fun <T> expressiveSpatialSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.78f,
    stiffness = 360f,
)

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

/**
 * App-chrome click using the platform/Material indication only. A second press-scale spring made
 * rapid taps keep moving after navigation had already started, which read as a small UI hitch.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.softClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Same single-indication behavior while preserving long-click semantics. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.softCombinedClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return combinedClickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

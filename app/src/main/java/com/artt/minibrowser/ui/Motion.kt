package com.artt.minibrowser.ui

import android.os.Build
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** Browser-specific motion vocabulary. Timings are semantic rather than screen-global. */
object MotionTokens {
    const val Press = 90
    const val IconState = 140
    const val Popup = 170
    const val Destination = 190
    const val Content = 210
    const val TabTransform = 300
    const val GestureSettle = 190
}

object MotionEasing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * Ask Android 15+ for the high frame-rate category only while app-chrome motion is actually
 * producing frames. This is reserved for the tab overview, whose large preview transforms benefit
 * from an explicit high-rate vote. Short internal destination transitions intentionally rely on the
 * display's existing adaptive/touch policy so entering a screen does not itself trigger a refresh
 * category switch.
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

/** App-chrome click with immediate press feedback plus the platform/Material indication. */
@Composable
fun Modifier.softClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.82f else 1f,
        animationSpec = tween(MotionTokens.Press, easing = MotionEasing.Standard),
        label = "browser press feedback",
    )
    return graphicsLayer { alpha = pressAlpha }
        .clickable(
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
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.82f else 1f,
        animationSpec = tween(MotionTokens.Press, easing = MotionEasing.Standard),
        label = "browser combined press feedback",
    )
    return graphicsLayer { alpha = pressAlpha }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

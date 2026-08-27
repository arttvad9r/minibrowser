package com.artt.minibrowser.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Motion is intentionally short and functional: feedback, state changes and orientation only. */
object MotionTokens {
    const val PressIn = 70
    const val Quick = 110
    const val Standard = 160
    const val Emphasis = 220
}

/**
 * Small press feedback for compact app chrome. Uses a graphics-layer transform, so it does not
 * trigger remeasurement or affect GeckoView/content scrolling.
 */
@Composable
fun Modifier.softClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) MotionTokens.PressIn else MotionTokens.Quick,
            easing = FastOutSlowInEasing,
        ),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        onClick = onClick,
    )
}

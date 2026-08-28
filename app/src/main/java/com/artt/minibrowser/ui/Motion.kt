package com.artt.minibrowser.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Restrained app motion system.
 *
 * Durations remain for effect-only transitions such as fades. Spatial movement uses springs so
 * interrupted gestures and rapid state changes preserve velocity instead of visibly restarting.
 */
object MotionTokens {
    const val PressIn = 70
    const val Quick = 120
    const val Standard = 190
    const val Emphasis = 280

    val PressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1_000f,
    )
    val ReleaseSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 560f,
    )
    val StandardSpatial = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 430f,
    )
    val ExpressiveSpatial = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 360f,
    )
}

/**
 * Small physical press feedback for app chrome. The transform stays in the graphics layer, so it
 * does not trigger layout or disturb GeckoView/content scrolling.
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
        animationSpec = if (pressed) MotionTokens.PressSpring else MotionTokens.ReleaseSpring,
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

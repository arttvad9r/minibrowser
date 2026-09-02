package com.artt.minibrowser.ui

import android.os.Build
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
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

/**
 * Chromium Android motion vocabulary.
 *
 * These values mirror the timing families used by Chrome's Android toolbar/start-surface code:
 * 225 ms transforms, 100 ms fast toolbar fades, 150 ms popup/icon fades, 300 ms tab transforms,
 * and 250 ms theme-color transitions. Keep app motion on these families instead of inventing
 * per-screen timings.
 */
object MotionTokens {
    const val Press = 100
    const val ToolbarButton = 100
    const val IconState = 150
    const val Popup = 150
    const val TabBackgroundFade = 150
    const val ListChange = 200
    const val Destination = 225
    const val Content = 225
    const val ToolbarFocus = 225
    const val Theme = 250
    const val TabTransform = 300
    const val GestureSettle = 200
}

/** Chromium's legacy BakedBezier curves, exposed as Compose easing values. */
object MotionEasing {
    /** FastOutSlowInInterpolator / former TRANSFORM_CURVE. */
    val Transform = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** LinearOutSlowInInterpolator / former FADE_IN_CURVE. */
    val FadeIn = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** FastOutLinearInInterpolator / former FADE_OUT_CURVE. */
    val FadeOut = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    // Compatibility aliases for existing call sites. New code should prefer the semantic names.
    val Standard = Transform
    val Emphasized = Transform
}

/**
 * Ask Android 15+ for the high frame-rate category only while the tab overview is transforming.
 * Short toolbar/menu/destination motion follows the display's normal adaptive/touch policy, just
 * like Chromium avoids changing refresh policy for every small UI transition.
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
 * Chrome-style app-chrome click: rely on the bounded platform/Material ripple only. The previous
 * extra whole-control alpha animation made every press visibly dim twice and added animation work
 * on top of the ripple.
 */
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

/** Same single-ripple behavior while preserving long-click and button semantics. */
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

package com.artt.minibrowser.ui

import android.graphics.Path
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import kotlin.math.PI
import kotlin.math.cos

/** Values copied from current Chromium Android motion resources/constants. */
object MotionTokens {
    // ToolbarPhone.java.
    const val ToolbarButton = 100
    const val ToolbarFocus = 225
    const val Theme = 250

    // ui/android menu_enter.xml + menu_exit.xml.
    const val MenuEnter = 250
    const val MenuExit = 150

    // components/browser_ui/styles shared_x_axis_* resources.
    const val SharedXAxisExitFade = 83
    const val SharedXAxisEnterFade = 283
    const val SharedXAxisTransition = 366

    // HubAnimationConstants.java + TabListItemAnimator.java.
    const val TabTransform = 325
    const val TabFallbackFade = 325
    const val TabListFade = 400
    const val TabRemove = 200
    const val TabMove = 250
    const val TabNew = 300
    const val TabSwipeDismissThresholdDp = 144
    const val PaneFade = 120
    const val PaneSlide = 250
    const val GestureSettle = 160

    // Existing non-route state changes keep Chromium's short UI timing family.
    const val IconState = 150
    const val Popup = 150
    const val ListChange = 200
    const val Content = 200

    // Compatibility names used by existing call sites.
    const val Press = ToolbarButton
    const val TabBackgroundFade = TabFallbackFade
    const val Destination = SharedXAxisTransition
}

/** Exact easing curves referenced by Chromium's Android resources. */
object MotionEasing {
    /** ui/android/java/res/anim/emphasized.xml / Interpolators.EMPHASIZED. */
    private val emphasizedInterpolator = PathInterpolator(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )
    val Emphasized = Easing { fraction -> emphasizedInterpolator.getInterpolation(fraction) }

    /** m3_sys_motion_easing_standard / Interpolators.STANDARD_INTERPOLATOR. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** m3_sys_motion_easing_standard_accelerate. */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** m3_sys_motion_easing_standard_decelerate. */
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    // Chromium/Android legacy curves used by Hub and ItemTouchHelper.
    val Transform = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val FadeIn = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val FadeOut = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val Linear = Easing { it }
    val AccelerateDecelerate = Easing { fraction ->
        ((cos((fraction + 1f) * PI) / 2.0) + 0.5).toFloat()
    }
}

/** Chromium shared_x_axis_open_enter / shared_x_axis_close_enter. */
fun chromiumSharedXAxisEnter(forward: Boolean): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(
            durationMillis = MotionTokens.SharedXAxisTransition,
            easing = MotionEasing.Emphasized,
        ),
        initialOffsetX = { width -> if (forward) width / 4 else -width / 4 },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = MotionTokens.SharedXAxisEnterFade,
            delayMillis = MotionTokens.SharedXAxisExitFade,
            easing = MotionEasing.StandardDecelerate,
        ),
    )

/** Chromium shared_x_axis_open_exit / shared_x_axis_close_exit. */
fun chromiumSharedXAxisExit(forward: Boolean): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(
            durationMillis = MotionTokens.SharedXAxisTransition,
            easing = MotionEasing.Emphasized,
        ),
        targetOffsetX = { width -> if (forward) -width / 4 else width / 4 },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = MotionTokens.SharedXAxisExitFade,
            easing = MotionEasing.StandardAccelerate,
        ),
    )

/**
 * Keeps a heavy underlay (notably GeckoView) composed while applying Chromium's exact
 * shared_x_axis_open_exit / shared_x_axis_close_enter pair. The hidden state is the open-exit
 * endpoint: -25% X and alpha 0. Returning reverses through close-enter with Chromium's 83 ms fade
 * delay and 283 ms fade duration.
 *
 * The visual surface moves during the transition, but its input shield stays fixed to the window.
 * This prevents exposed transition margins from dispatching gestures into the hidden browser.
 */
@Composable
fun ChromiumSharedXAxisUnderlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = updateTransition(targetState = visible, label = "chromium shared x underlay")
    val translationFraction by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = MotionTokens.SharedXAxisTransition,
                easing = MotionEasing.Emphasized,
            )
        },
        label = "chromium shared x underlay translation",
    ) { shown -> if (shown) 0f else -0.25f }
    val alpha by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(
                    durationMillis = MotionTokens.SharedXAxisEnterFade,
                    delayMillis = MotionTokens.SharedXAxisExitFade,
                    easing = MotionEasing.StandardDecelerate,
                )
            } else {
                tween(
                    durationMillis = MotionTokens.SharedXAxisExitFade,
                    easing = MotionEasing.StandardAccelerate,
                )
            }
        },
        label = "chromium shared x underlay alpha",
    ) { shown -> if (shown) 1f else 0f }
    var widthPx by remember { mutableIntStateOf(0) }
    val inputBlocked = !visible || transition.currentState != transition.targetState
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = translationFraction * widthPx
                    this.alpha = alpha
                },
        ) {
            content()
        }
        if (inputBlocked) {
            InputShield()
        }
    }
}

/** Chromium uses the high-rate vote only for the large tab overview transform. */
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

/** Use the platform/Material ripple without an additional invented alpha animation. */
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

package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Full-screen app destination over the browser.
 *
 * The opaque destination background is never animated. Only the destination content moves/fades,
 * so a live GeckoView is not blended through a translucent Compose layer during navigation.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    fromBottom: Boolean = false,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var activeAnimations by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val travelPx = if (fromBottom) with(density) { 20.dp.toPx() } else 0f

    HighFrameRateDuringMotion(activeAnimations > 0)

    suspend fun animateReveal(target: Float) {
        activeAnimations++
        try {
            reveal.animateTo(target, animationSpec = tween(MotionTokens.Quick))
        } finally {
            activeAnimations--
        }
    }

    LaunchedEffect(pendingExit) {
        val action = pendingExit
        animateReveal(if (action == null) 1f else 0f)
        action?.invoke()
    }

    fun requestExit(action: () -> Unit) {
        if (pendingExit != null) return
        pendingExit = action
    }

    BackHandler(enabled = pendingExit == null) { requestExit(onBack) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * travelPx
                },
        ) {
            content(::requestExit)
        }
    }
}

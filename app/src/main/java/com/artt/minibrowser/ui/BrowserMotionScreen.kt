package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * Full-screen app destination over the browser.
 *
 * Destinations move as one fully opaque bottom-origin surface. This avoids the white/empty frame
 * caused by fading only the destination content while an opaque background had already covered the
 * previous screen, and it never alpha-blends a live GeckoView with Compose.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    fromBottom: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    val reveal = remember(fromBottom) { Animatable(if (fromBottom) 0f else 1f) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var activeAnimations by remember { mutableIntStateOf(0) }

    HighFrameRateDuringMotion(activeAnimations > 0)

    suspend fun animateReveal(target: Float) {
        activeAnimations++
        try {
            reveal.animateTo(target, animationSpec = tween(MotionTokens.Quick))
        } finally {
            activeAnimations--
        }
    }

    LaunchedEffect(Unit) {
        if (fromBottom) animateReveal(1f)
    }

    LaunchedEffect(pendingExit) {
        val action = pendingExit ?: return@LaunchedEffect
        if (fromBottom) animateReveal(0f)
        pendingExit = null
        action()
    }

    fun requestExit(action: () -> Unit) {
        if (pendingExit != null) return
        pendingExit = action
    }

    BackHandler(enabled = pendingExit == null) { requestExit(onBack) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val travelPx = with(density) { maxHeight.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (fromBottom) (1f - reveal.value) * travelPx else 0f
                }
                .background(MaterialTheme.colorScheme.background),
        ) {
            content(::requestExit)
        }
    }
}

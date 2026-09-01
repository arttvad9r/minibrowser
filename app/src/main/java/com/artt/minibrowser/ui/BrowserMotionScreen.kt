package com.artt.minibrowser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Full-screen app destination over the browser.
 *
 * The destination surface stays spatially fixed. Entry, exit and predictive back use only a
 * restrained opacity change, so returning from settings/history/bookmarks does not look like a
 * sheet sliding in the wrong direction and cannot expose an empty Gecko frame underneath.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    fromBottom: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    val reveal = remember(fromBottom) { Animatable(if (fromBottom) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var predictiveBackActive by remember { mutableStateOf(false) }
    var activeAnimations by remember { mutableIntStateOf(0) }

    HighFrameRateDuringMotion(activeAnimations > 0 || predictiveBackActive)

    suspend fun animateReveal(target: Float) {
        activeAnimations++
        try {
            reveal.animateTo(target, animationSpec = tween(MotionTokens.Screen))
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
        if (pendingExit != null || predictiveBackActive) return
        pendingExit = action
    }

    PredictiveBackHandler(enabled = pendingExit == null) { progress ->
        predictiveBackActive = true
        try {
            if (fromBottom) {
                progress.collect { event ->
                    reveal.snapTo(predictiveBackReveal(event.progress))
                }
                reveal.snapTo(0f)
            } else {
                progress.collect { }
            }
            predictiveBackActive = false
            onBack()
        } catch (cancelled: CancellationException) {
            predictiveBackActive = false
            if (fromBottom) scope.launch { animateReveal(1f) }
            throw cancelled
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .graphicsLayer {
                    if (fromBottom) {
                        // Neutral fade only: no directional slide on destination exit.
                        alpha = 0.96f + reveal.value * 0.04f
                    }
                },
        ) {
            content(::requestExit)
        }
    }
}

internal fun predictiveBackReveal(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

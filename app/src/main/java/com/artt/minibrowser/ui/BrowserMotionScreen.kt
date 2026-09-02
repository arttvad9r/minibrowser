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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Full-screen app destination over the browser.
 *
 * Entry and predictive back use restrained spatial motion. Ordinary taps are never held behind an
 * exit animation: the requested navigation runs immediately, while predictive back remains fully
 * gesture-driven.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    fromBottom: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    val reveal = remember(fromBottom) { Animatable(if (fromBottom) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var predictiveBackActive by remember { mutableStateOf(false) }
    var activeAnimations by remember { mutableIntStateOf(0) }

    HighFrameRateDuringMotion(activeAnimations > 0 || predictiveBackActive)

    suspend fun animateReveal(target: Float) {
        activeAnimations++
        try {
            reveal.animateTo(
                target,
                animationSpec = tween(MotionTokens.Destination, easing = MotionEasing.Standard),
            )
        } finally {
            activeAnimations--
        }
    }

    LaunchedEffect(Unit) {
        if (fromBottom) animateReveal(1f)
    }

    fun requestExit(action: () -> Unit) {
        if (!predictiveBackActive) action()
    }

    PredictiveBackHandler { progress ->
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

    val travelPx = with(LocalDensity.current) { 24.dp.toPx() }
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
                        val progress = reveal.value.coerceIn(0f, 1f)
                        translationX = (1f - progress) * travelPx
                        alpha = 0.88f + progress * 0.12f
                        val scale = 0.985f + progress * 0.015f
                        scaleX = scale
                        scaleY = scale
                    }
                },
        ) {
            content(::requestExit)
        }
    }
}

internal fun predictiveBackReveal(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

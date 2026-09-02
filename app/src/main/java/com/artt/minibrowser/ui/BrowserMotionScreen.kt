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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Full-screen browser destination using the same restrained transform family as Chromium Android.
 * The complete surface moves together by a small horizontal offset; there is no scale or alpha
 * cross-fade. Ordinary Back stays immediate so animation never blocks navigation.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    fromBottom: Boolean = true,
    backEnabled: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    val reveal = remember(fromBottom) { Animatable(if (fromBottom) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var predictiveBackActive by remember { mutableStateOf(false) }

    suspend fun animateReveal(target: Float) {
        reveal.animateTo(
            target,
            animationSpec = tween(
                durationMillis = MotionTokens.Destination,
                easing = MotionEasing.Transform,
            ),
        )
    }

    LaunchedEffect(Unit) {
        if (fromBottom) animateReveal(1f)
    }

    fun requestExit(action: () -> Unit) {
        if (!predictiveBackActive) action()
    }

    if (backEnabled) {
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
    }

    // Chrome's toolbar focus motion uses 10 dp for auxiliary controls. A 12 dp full-surface travel
    // preserves the same visual weight without turning destination changes into obvious slides.
    val travelPx = with(LocalDensity.current) { 12.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (fromBottom) {
                        val progress = reveal.value.coerceIn(0f, 1f)
                        translationX = (1f - progress) * travelPx
                    }
                }
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            content(::requestExit)
        }
    }
}

internal fun predictiveBackReveal(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

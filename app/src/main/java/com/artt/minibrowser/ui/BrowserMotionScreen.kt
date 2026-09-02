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
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Full-screen app destination using Chromium's shared_x_axis_open_enter and
 * shared_x_axis_close_exit resources verbatim: 25% horizontal travel over 366 ms, an 83 ms exit
 * fade, and a 283 ms delayed enter fade using the same Material 3 easing curves.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") fromBottom: Boolean = true,
    backEnabled: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    val translation = remember { Animatable(1f) }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(0) }
    var exiting by remember { mutableStateOf(false) }
    var predictiveBackActive by remember { mutableStateOf(false) }

    suspend fun animateOpen() = coroutineScope {
        launch {
            translation.animateTo(
                0f,
                tween(
                    durationMillis = MotionTokens.SharedXAxisTransition,
                    easing = MotionEasing.Emphasized,
                ),
            )
        }
        launch {
            delay(MotionTokens.SharedXAxisExitFade.toLong())
            alpha.animateTo(
                1f,
                tween(
                    durationMillis = MotionTokens.SharedXAxisEnterFade,
                    easing = MotionEasing.StandardDecelerate,
                ),
            )
        }
    }

    suspend fun animateClose(action: () -> Unit) {
        if (exiting) return
        exiting = true
        coroutineScope {
            launch {
                translation.animateTo(
                    1f,
                    tween(
                        durationMillis = MotionTokens.SharedXAxisTransition,
                        easing = MotionEasing.Emphasized,
                    ),
                )
            }
            launch {
                alpha.animateTo(
                    0f,
                    tween(
                        durationMillis = MotionTokens.SharedXAxisExitFade,
                        easing = MotionEasing.StandardAccelerate,
                    ),
                )
            }
        }
        action()
    }

    LaunchedEffect(Unit) { animateOpen() }

    fun requestExit(action: () -> Unit) {
        if (!predictiveBackActive && !exiting) {
            scope.launch { animateClose(action) }
        }
    }

    if (backEnabled) {
        PredictiveBackHandler { progress ->
            predictiveBackActive = true
            try {
                progress.collect { }
                predictiveBackActive = false
                animateClose(onBack)
            } catch (cancelled: CancellationException) {
                predictiveBackActive = false
                throw cancelled
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer {
                translationX = translation.value * widthPx * 0.25f
                this.alpha = alpha.value
            }
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        content(::requestExit)
    }
}

internal fun predictiveBackReveal(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

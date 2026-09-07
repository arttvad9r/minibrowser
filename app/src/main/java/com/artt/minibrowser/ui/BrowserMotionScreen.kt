package com.artt.minibrowser.ui

import androidx.activity.BackEventCompat
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Static content shell for an internal browser destination.
 *
 * BrowserRoute owns the actual Chromium shared-X animation so outgoing and incoming surfaces move
 * concurrently, as they do in Chromium. This shell owns Back/predictive-Back dispatch, the
 * gesture-driven preview transform, and safe-area/background layout.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") fromBottom: Boolean = true,
    backEnabled: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    var predictiveBackActive by remember { mutableStateOf(false) }
    var predictiveBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val predictiveBackProgress = remember { Animatable(0f) }
    val predictiveTranslationPx = with(LocalDensity.current) { 28.dp.toPx() }

    fun requestExit(action: () -> Unit) {
        if (!predictiveBackActive) action()
    }

    if (backEnabled) {
        PredictiveBackHandler { progress ->
            predictiveBackActive = true
            try {
                progress.collect { event ->
                    predictiveBackEdge = event.swipeEdge
                    predictiveBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                predictiveBackActive = false
                onBack()
            } catch (cancelled: CancellationException) {
                predictiveBackActive = false
                predictiveBackProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = MotionTokens.GestureSettle,
                        easing = MotionEasing.Standard,
                    ),
                )
                throw cancelled
            }
        }
    }

    val reveal = predictiveBackReveal(predictiveBackProgress.value)
    val direction = if (predictiveBackEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = direction * predictiveTranslationPx * predictiveBackProgress.value
                scaleX = 0.96f + 0.04f * reveal
                scaleY = 0.96f + 0.04f * reveal
                alpha = 0.92f + 0.08f * reveal
            }
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        InputShield()
        content(::requestExit)
    }
}

internal fun predictiveBackReveal(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

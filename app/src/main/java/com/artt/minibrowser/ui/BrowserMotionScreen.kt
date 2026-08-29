package com.artt.minibrowser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

/**
 * Shared motion container for full-screen browser destinations such as Settings/History/Bookmarks.
 * It keeps the underlying browser composed and exposes an exit callback so taps and Back use the
 * same transition. Predictive Back maps gesture progress directly to the visual state.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var predictiveActive by remember { mutableStateOf(false) }
    var predictiveReturning by remember { mutableStateOf(false) }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    val predictiveReturn = remember { Animatable(1f) }
    val density = LocalDensity.current
    val travelPx = with(density) { 22.dp.toPx() }

    LaunchedEffect(Unit) { entered = true }

    val settledProgress by animateFloatAsState(
        targetValue = if (entered && !leaving) 1f else 0f,
        animationSpec = MotionTokens.StandardSpatial,
        label = "browserScreenMotion",
    )

    LaunchedEffect(pendingExit) {
        val action = pendingExit ?: return@LaunchedEffect
        snapshotFlow { settledProgress }.first { it <= 0.015f }
        pendingExit = null
        action()
    }

    fun requestExit(action: () -> Unit) {
        if (leaving || pendingExit != null) return
        leaving = true
        pendingExit = action
    }

    PredictiveBackHandler(enabled = !leaving) { events ->
        var receivedProgress = false
        try {
            predictiveReturning = false
            predictiveActive = true
            events.collect { event ->
                receivedProgress = true
                predictiveProgress = event.progress.coerceIn(0f, 1f)
            }
            if (receivedProgress) {
                predictiveProgress = 1f
                onBack()
            } else {
                predictiveActive = false
                requestExit(onBack)
            }
        } catch (_: CancellationException) {
            val visibleProgress = (1f - predictiveProgress).coerceIn(0f, 1f)
            predictiveActive = false
            predictiveReturning = true
            predictiveReturn.snapTo(visibleProgress)
            predictiveReturn.animateTo(1f, MotionTokens.StandardSpatial)
            predictiveProgress = 0f
            predictiveReturning = false
        }
    }

    val progress = when {
        predictiveActive -> 1f - predictiveProgress
        predictiveReturning -> predictiveReturn.value
        else -> settledProgress
    }.coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                translationX = (1f - progress) * travelPx
                val scale = 0.985f + 0.015f * progress
                scaleX = scale
                scaleY = scale
            }
            .background(MaterialTheme.colorScheme.background),
    ) {
        content(::requestExit)
    }
}

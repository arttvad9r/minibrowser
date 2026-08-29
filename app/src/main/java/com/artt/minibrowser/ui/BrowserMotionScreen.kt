package com.artt.minibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shared full-screen destination container for Settings/History/Bookmarks.
 *
 * Most destinations use only a short opacity transition. A destination that is opened from a
 * bottom-sheet action can opt into a small vertical travel so its motion follows the action's
 * physical origin without moving the underlying GeckoView.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    fromBottom: Boolean = false,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val density = LocalDensity.current
    val travelPx = if (fromBottom) with(density) { 28.dp.toPx() } else 0f

    LaunchedEffect(Unit) { visible = true }

    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(MotionTokens.Quick),
        label = "browserScreenReveal",
    )

    LaunchedEffect(pendingExit) {
        val action = pendingExit ?: return@LaunchedEffect
        visible = false
        delay(MotionTokens.Quick.toLong())
        pendingExit = null
        action()
    }

    fun requestExit(action: () -> Unit) {
        if (pendingExit != null) return
        pendingExit = action
    }

    BackHandler(enabled = pendingExit == null) { requestExit(onBack) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * travelPx
            }
            .background(MaterialTheme.colorScheme.background),
    ) {
        content(::requestExit)
    }
}

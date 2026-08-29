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
import kotlinx.coroutines.delay

/**
 * Shared full-screen destination container for Settings/History/Bookmarks.
 *
 * These screens deliberately use only a short opacity transition. Horizontal translation made
 * destinations look like side sheets and produced visible stepping when GeckoView was composed
 * underneath them. Back and toolbar actions use the same exit path.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(MotionTokens.Quick),
        label = "browserScreenFade",
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
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.background),
    ) {
        content(::requestExit)
    }
}

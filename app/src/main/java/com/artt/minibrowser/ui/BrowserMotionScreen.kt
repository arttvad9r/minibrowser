package com.artt.minibrowser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Static content shell for an internal browser destination.
 *
 * BrowserRoute owns the actual Chromium shared-X animation so outgoing and incoming surfaces move
 * concurrently, as they do in Chromium. This shell only owns Back/predictive-Back dispatch and
 * safe-area/background layout.
 */
@Composable
fun BrowserMotionScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") fromBottom: Boolean = true,
    backEnabled: Boolean = true,
    content: @Composable (requestExit: (() -> Unit) -> Unit) -> Unit,
) {
    var predictiveBackActive by remember { mutableStateOf(false) }

    fun requestExit(action: () -> Unit) {
        if (!predictiveBackActive) action()
    }

    if (backEnabled) {
        PredictiveBackHandler { progress ->
            predictiveBackActive = true
            try {
                progress.collect { }
                predictiveBackActive = false
                onBack()
            } catch (cancelled: CancellationException) {
                predictiveBackActive = false
                throw cancelled
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        InputShield()
        content(::requestExit)
    }
}

internal fun predictiveBackReveal(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

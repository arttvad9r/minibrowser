package com.artt.minibrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Reflect Gecko's real progress; only the indicator's appearance/disappearance is animated. */
@Composable
internal fun BrowserPageProgress(progress: Float) {
    Box(Modifier.fillMaxWidth().height(2.dp)) {
        AnimatedVisibility(
            visible = progress >= 0f,
            enter = fadeIn(tween(MotionTokens.Popup, easing = MotionEasing.FadeIn)),
            exit = fadeOut(tween(MotionTokens.Popup, easing = MotionEasing.FadeOut)),
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

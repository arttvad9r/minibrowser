package com.artt.minibrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artt.minibrowser.engine.Tab

/** Reflect Gecko's real progress without adding an artificial catch-up animation. */
@Composable
internal fun BrowserPageProgress(tab: Tab?) {
    val progress = tab?.progress ?: -1f
    Box(Modifier.fillMaxWidth().height(2.dp)) {
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

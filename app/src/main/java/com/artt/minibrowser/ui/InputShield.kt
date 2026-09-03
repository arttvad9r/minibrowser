package com.artt.minibrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Invisible hit-test surface placed behind the visible content of a modal/full-screen layer.
 *
 * Interactive children are composed after this shield and therefore keep their normal pointer
 * handling. Empty areas fall back to this surface instead of leaking the gesture to a lower layer.
 */
@Composable
internal fun InputShield(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    )
}

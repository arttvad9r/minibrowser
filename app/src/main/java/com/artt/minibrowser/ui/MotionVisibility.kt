package com.artt.minibrowser.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Explicit BoxScope overloads avoid Compose layout DSL-marker receiver conflicts when visibility
 * transitions are emitted from a Box (including nested Popup content) while preserving the normal
 * top-level AnimatedVisibility implementation.
 */
@Composable
internal fun BoxScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

@Composable
internal fun BoxScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

package com.artt.minibrowser.browser

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Owns root-level browser side effects and back-dispatch precedence.
 *
 * Rendering remains outside this coordinator; it only translates root UI state into callbacks.
 */
@Composable
internal fun BrowserRootEffects(
    screen: BrowserScreen,
    showSwitcher: Boolean,
    currentUrl: String?,
    canGoBack: Boolean,
    inFullscreen: Boolean,
    showFind: Boolean,
    onClearFocus: () -> Unit,
    onInstallExternalNavigation: () -> Unit,
    onSyncBookmark: (String?) -> Unit,
    onGoBack: () -> Unit,
    onExitFullscreen: () -> Unit,
    onCloseFind: () -> Unit,
) {
    LaunchedEffect(screen, showSwitcher) {
        if (screen != BrowserScreen.Browser || showSwitcher) onClearFocus()
    }

    LaunchedEffect(Unit) {
        onInstallExternalNavigation()
    }

    LaunchedEffect(screen, currentUrl) {
        if (screen == BrowserScreen.Browser) onSyncBookmark(currentUrl)
    }

    // Keep declaration order aligned with the previous root so the most recently composed
    // enabled handler keeps precedence: find -> fullscreen -> page history.
    BackHandler(enabled = screen == BrowserScreen.Browser && canGoBack, onBack = onGoBack)
    BackHandler(enabled = inFullscreen, onBack = onExitFullscreen)
    BackHandler(enabled = showFind, onBack = onCloseFind)
}

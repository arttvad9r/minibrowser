@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.artt.minibrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.engine.ExtensionLoader
import java.io.File

internal data class BrowserPageUiState(
    val chrome: BrowserChromeUiState,
    val tabCount: Int,
    val bookmarked: Boolean,
    val suggestions: List<Suggestion>,
    val adblockStatus: ExtensionLoader.Status?,
    val showFind: Boolean,
    val showStart: Boolean,
    val inFullscreen: Boolean,
    val loadError: String?,
)

internal data class BrowserPageActions(
    val onSuggestionQueryChanged: (String?) -> Unit,
    val onSubmitQuery: (String) -> Unit,
    val onNavigate: (String) -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReload: () -> Unit,
    val onSiteInfo: () -> Unit,
    val onSwitcher: () -> Unit,
    val onNewTab: () -> Unit,
    val onNewPrivateTab: () -> Unit,
    val onFind: () -> Unit,
    val onCloseFind: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onBookmarks: () -> Unit,
    val onHistory: () -> Unit,
    val onShare: () -> Unit,
    val onSettings: () -> Unit,
    val onToggleAdblock: (Boolean) -> Unit,
    val onRetryAdblock: () -> Unit,
    val onTranslate: () -> Unit,
    val onToggleDesktop: () -> Unit,
)

/**
 * Browser-page renderer. Engine-specific content is supplied through slots by the screen route;
 * this layer itself depends only on display state and UI callbacks.
 */
@Composable
internal fun BrowserPageContent(
    state: BrowserPageUiState,
    actions: BrowserPageActions,
    iconsDir: File,
    browserContent: @Composable () -> Unit,
    findContent: (@Composable () -> Unit)?,
    startPageContent: @Composable () -> Unit,
) {
    val omniboxFocus = remember { FocusRequester() }
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val topSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    val bottomSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(horizontalSafeInsets),
    ) {
        if (!state.inFullscreen) {
            Box(Modifier.windowInsetsPadding(topSafeInsets)) {
                TopBar(
                    state.chrome,
                    tabCount = state.tabCount,
                    bookmarked = state.bookmarked,
                    iconsDir = iconsDir,
                    omniboxFocus = omniboxFocus,
                    suggestions = state.suggestions,
                    onSuggestionQueryChanged = actions.onSuggestionQueryChanged,
                    onSubmitQuery = actions.onSubmitQuery,
                    onNavigate = actions.onNavigate,
                    onBack = actions.onBack,
                    onForward = actions.onForward,
                    onReload = actions.onReload,
                    onSiteInfo = actions.onSiteInfo,
                    onSwitcher = actions.onSwitcher,
                    onNewTab = actions.onNewTab,
                    onNewPrivateTab = actions.onNewPrivateTab,
                    onFind = actions.onFind,
                    onToggleBookmark = actions.onToggleBookmark,
                    onBookmarks = actions.onBookmarks,
                    onHistory = actions.onHistory,
                    onShare = actions.onShare,
                    onSettings = actions.onSettings,
                    onToggleAdblock = actions.onToggleAdblock,
                    onRetryAdblock = actions.onRetryAdblock,
                    adblockStatus = state.adblockStatus,
                    onTranslate = actions.onTranslate,
                    onToggleDesktop = actions.onToggleDesktop,
                )
            }
        }
        if (state.showFind && !state.inFullscreen) {
            findContent?.invoke()
        }
        Box(
            Modifier
                .weight(1f)
                .windowInsetsPadding(bottomSafeInsets),
        ) {
            browserContent()
            if (state.showStart) {
                startPageContent()
            }
            val loadError = state.loadError
            if (!state.showStart && loadError != null) {
                ErrorOverlay(loadError, actions.onReload)
            }
        }
    }
}

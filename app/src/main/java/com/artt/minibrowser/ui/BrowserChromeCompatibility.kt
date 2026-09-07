package com.artt.minibrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import java.io.File

/**
 * Source-compatible overload for render tests and callers that predate the removed generic
 * "open in external app" menu action. The real navigation path now handles Android App Links
 * automatically, so these two obsolete menu parameters are always disabled here.
 */
@Composable
internal fun TopBar(
    state: BrowserChromeUiState,
    tabCount: Int,
    bookmarked: Boolean,
    iconsDir: File,
    omniboxFocus: FocusRequester,
    suggestions: List<BrowserSuggestionUiState>,
    onSuggestionQueryChanged: (String?) -> Unit,
    onSubmitQuery: (String) -> Unit,
    adblockStatus: BrowserExtensionUiState,
    onToggleAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSiteInfo: () -> Unit,
    onSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onTranslate: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    TopBar(
        state = state,
        tabCount = tabCount,
        bookmarked = bookmarked,
        iconsDir = iconsDir,
        omniboxFocus = omniboxFocus,
        suggestions = suggestions,
        onSuggestionQueryChanged = onSuggestionQueryChanged,
        onSubmitQuery = onSubmitQuery,
        adblockStatus = adblockStatus,
        onToggleAdblock = onToggleAdblock,
        onRetryAdblock = onRetryAdblock,
        onNavigate = onNavigate,
        onBack = onBack,
        onForward = onForward,
        onReload = onReload,
        onSiteInfo = onSiteInfo,
        onSwitcher = onSwitcher,
        onNewTab = onNewTab,
        onNewPrivateTab = onNewPrivateTab,
        onFind = onFind,
        onShare = onShare,
        canOpenExternal = false,
        onOpenExternal = {},
        onToggleBookmark = onToggleBookmark,
        onBookmarks = onBookmarks,
        onHistory = onHistory,
        onDownloads = onDownloads,
        onSettings = onSettings,
        onTranslate = onTranslate,
        onToggleDesktop = onToggleDesktop,
    )
}

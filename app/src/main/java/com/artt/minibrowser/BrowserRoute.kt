package com.artt.minibrowser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.browser.BrowserDataClearer
import com.artt.minibrowser.browser.BrowserDataViewModel
import com.artt.minibrowser.browser.BrowserIntentController
import com.artt.minibrowser.browser.BrowserRootEffects
import com.artt.minibrowser.browser.BrowserScreen
import com.artt.minibrowser.browser.BrowserViewModel
import com.artt.minibrowser.browser.BrowserWindowController
import com.artt.minibrowser.browser.BrowserWindowEffects
import com.artt.minibrowser.browser.NavigationController
import com.artt.minibrowser.browser.OmniboxSuggestionsViewModel
import com.artt.minibrowser.browser.PageBookmarkViewModel
import com.artt.minibrowser.browser.SettingsViewModel
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.NavigationTarget
import com.artt.minibrowser.engine.PageLoadError
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.SecurityState
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.engine.buildTranslateUri
import com.artt.minibrowser.engine.resolveNavigation
import com.artt.minibrowser.engine.toggleDesktopMode
import com.artt.minibrowser.net.isValidWebUri
import com.artt.minibrowser.ui.BrowserChromeUiState
import com.artt.minibrowser.ui.BrowserExtensionUiState
import com.artt.minibrowser.ui.BrowserPageActions
import com.artt.minibrowser.ui.BrowserPageContent
import com.artt.minibrowser.ui.BrowserPageLoadErrorUiState
import com.artt.minibrowser.ui.BrowserPageProgress
import com.artt.minibrowser.ui.BrowserPageUiState
import com.artt.minibrowser.ui.BrowserSecurityUiState
import com.artt.minibrowser.ui.BrowserSuggestionUiState
import com.artt.minibrowser.ui.BrowserTabItemUiState
import com.artt.minibrowser.ui.BrowserTabSwitcher
import com.artt.minibrowser.ui.ChromiumSharedXAxisUnderlay
import com.artt.minibrowser.ui.FindInPageRoute
import com.artt.minibrowser.ui.GeckoContent
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.SettingsScreenUiState
import com.artt.minibrowser.ui.SettingsSearchEngineUiState
import com.artt.minibrowser.ui.SiteInfoSheet
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.TabPreviewStore
import com.artt.minibrowser.ui.chromiumSharedXAxisEnter
import com.artt.minibrowser.ui.chromiumSharedXAxisExit
import java.io.File
import kotlinx.coroutines.launch

/**
 * Screen-level browser route. It collects state from browser ViewModels and translates user
 * actions into explicit callbacks. Rendering receives state/callback contracts only.
 */
@Composable
internal fun BrowserRoute(
    tabManager: TabManager,
    settingsViewModel: SettingsViewModel,
    browserDataViewModel: BrowserDataViewModel,
    browserDataClearer: BrowserDataClearer,
    browserViewModel: BrowserViewModel,
    pageBookmarkViewModel: PageBookmarkViewModel,
    omniboxSuggestionsViewModel: OmniboxSuggestionsViewModel,
    bookmarksRepository: BookmarksRepository,
    historyRepository: HistoryRepository,
    browserWindow: BrowserWindowController,
    browserIntents: BrowserIntentController,
    externalNavigation: NavigationController,
    tabPreviewStore: TabPreviewStore,
    iconsDir: File,
) {
    val settingsUi by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val browserDataUi by browserDataViewModel.uiState.collectAsStateWithLifecycle()
    val prefs = settingsUi.prefs
    val darkTheme = when (prefs.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val browserUi by browserViewModel.state.collectAsStateWithLifecycle()
    val pageBookmarkUi by pageBookmarkViewModel.uiState.collectAsStateWithLifecycle()
    val omniboxSuggestionsUi by omniboxSuggestionsViewModel.uiState.collectAsStateWithLifecycle()
    val screen = browserUi.screen
    val showSwitcher = browserUi.showSwitcher
    val showFind = browserUi.showFind
    val showSiteInfo = browserUi.showSiteInfo
    var downloadsReturnToSettings by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val routeScope = rememberCoroutineScope()

    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val currentId by tabManager.currentId.collectAsStateWithLifecycle()
    val currentTab = tabs.firstOrNull { it.id == currentId }
    val currentSession = currentTab?.session
    val focusManager = LocalFocusManager.current
    val inFullscreen = currentTab?.fullscreen == true
    val isLoading = (currentTab?.progress ?: -1f) >= 0f

    BrowserRootEffects(
        screen = screen,
        showSwitcher = showSwitcher,
        currentUrl = currentTab?.url,
        canGoBack = currentTab?.canGoBack == true,
        inFullscreen = inFullscreen,
        showFind = showFind,
        onClearFocus = { focusManager.clearFocus(force = true) },
        onInstallExternalNavigation = {
            externalNavigation.setHandler { uri -> tabManager.newTab(uri) }
        },
        onSyncBookmark = pageBookmarkViewModel::sync,
        onGoBack = { currentSession?.goBack() },
        onExitFullscreen = { currentSession?.exitFullScreen() },
        onCloseFind = {
            currentSession?.finder?.clear()
            browserViewModel.showFind(false)
        },
    )

    BrowserWindowEffects(
        controller = browserWindow,
        darkTheme = darkTheme,
        isPrivate = currentTab?.isPrivate == true,
        inFullscreen = inFullscreen,
    )

    val bookmarked = pageBookmarkUi.url == currentTab?.url && pageBookmarkUi.isBookmarked
    val showStart = currentTab?.url.isNullOrBlank() || currentTab?.url == "about:blank"
    val toggleAdblock: (Boolean) -> Unit = settingsViewModel::setAdblock
    val retryAdblock: () -> Unit = settingsViewModel::retryAdblock
    val toggleVot: (Boolean) -> Unit = settingsViewModel::setVot
    val retryVot: () -> Unit = settingsViewModel::retryVot
    val chromeSecurityState = when (currentTab?.securityState) {
        SecurityState.Secure -> BrowserSecurityUiState.Secure
        SecurityState.Insecure -> BrowserSecurityUiState.Insecure
        SecurityState.Exception -> BrowserSecurityUiState.Exception
        SecurityState.Unknown, null -> BrowserSecurityUiState.Unknown
    }
    val chromeAdblockStatus = settingsUi.adblockStatus.toExtensionUiState()
    val settingsVotStatus = settingsUi.votStatus.toExtensionUiState()
    val chromeState = BrowserChromeUiState(
        url = currentTab?.url.orEmpty(),
        isWebPage = currentTab?.url?.let(::isValidWebUri) == true,
        isPrivate = currentTab?.isPrivate == true,
        isLoading = isLoading,
        securityState = chromeSecurityState,
        canGoBack = currentTab?.canGoBack == true,
        canGoForward = currentTab?.canGoForward == true,
        desktop = currentTab?.desktop == true,
    )
    val settingsScreenState = SettingsScreenUiState(
        searchEngine = prefs.searchEngine.toSettingsUiState(),
        theme = prefs.theme,
        adblockEnabled = prefs.adblockEnabled,
        votEnabled = prefs.votEnabled,
        translateTarget = prefs.translateTarget,
        adblockStatus = chromeAdblockStatus,
        votStatus = settingsVotStatus,
        clearDataInProgress = browserDataUi.isClearing,
        clearDataFailed = browserDataUi.clearFailed,
    )
    val tabItems = tabs.map { tab ->
        BrowserTabItemUiState(
            id = tab.id,
            url = tab.url,
            title = tab.title,
            isPrivate = tab.isPrivate,
        )
    }
    val suggestionItems = omniboxSuggestionsUi.suggestions.map { suggestion ->
        BrowserSuggestionUiState(
            label = suggestion.label,
            url = suggestion.url,
        )
    }

    val browserContentHiddenByRoute = screen != BrowserScreen.Browser || showSwitcher
    val pageState = BrowserPageUiState(
        chrome = chromeState,
        tabCount = tabs.size,
        bookmarked = bookmarked,
        suggestions = suggestionItems,
        adblockStatus = chromeAdblockStatus,
        showFind = showFind,
        showStart = showStart,
        inFullscreen = inFullscreen,
        loadError = currentTab?.loadError.toUiState(),
        browserContentHiddenByRoute = browserContentHiddenByRoute,
    )
    val pageActions = BrowserPageActions(
        onSuggestionQueryChanged = omniboxSuggestionsViewModel::updateQuery,
        onSubmitQuery = { query ->
            when (val target = resolveNavigation(query)) {
                is NavigationTarget.External -> browserIntents.openExternalUri(target.uri)
                is NavigationTarget.Web,
                is NavigationTarget.Internal,
                is NavigationTarget.Search,
                -> (currentTab ?: tabManager.newTab(null)).session.loadUri(
                    buildLoadUri(query, prefs.searchEngine),
                )
            }
        },
        onNavigate = { uri ->
            (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
        },
        onBack = { currentSession?.goBack() },
        onForward = { currentSession?.goForward() },
        onReload = {
            if (isLoading) currentSession?.stop() else currentSession?.reload()
        },
        onSiteInfo = { browserViewModel.showSiteInfo(true) },
        onSwitcher = { browserViewModel.showSwitcher(true) },
        onNewTab = { tabManager.newTab(null) },
        onNewPrivateTab = { tabManager.newTab(null, private = true) },
        onFind = { browserViewModel.showFind(true) },
        onCloseFind = { browserViewModel.showFind(false) },
        onToggleBookmark = {
            currentTab?.let { tab -> pageBookmarkViewModel.toggle(tab.url, tab.title) }
        },
        onBookmarks = { browserViewModel.screen(BrowserScreen.Bookmarks) },
        onHistory = { browserViewModel.screen(BrowserScreen.History) },
        onDownloads = {
            downloadsReturnToSettings = false
            browserViewModel.screen(BrowserScreen.Downloads)
        },
        onShare = { browserIntents.shareUrl(currentTab?.url) },
        onSettings = { browserViewModel.screen(BrowserScreen.Settings) },
        onToggleAdblock = toggleAdblock,
        onRetryAdblock = retryAdblock,
        onTranslate = {
            currentTab?.let { tab ->
                buildTranslateUri(tab.url, prefs.translateTarget)?.let(tab.session::loadUri)
            }
        },
        onToggleDesktop = {
            currentTab?.let(::toggleDesktopMode)
        },
    )

    val settingsPaneTitle = stringResource(R.string.settings_title)
    val downloadsPaneTitle = stringResource(R.string.downloads_title)
    val historyPaneTitle = stringResource(R.string.history_title)
    val bookmarksPaneTitle = stringResource(R.string.bookmarks_title)
    val tabClosedMessage = stringResource(R.string.tab_closed_message)
    val undoLabel = stringResource(R.string.action_undo)

    MinibrowserTheme(darkTheme = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ChromiumSharedXAxisUnderlay(
                visible = screen == BrowserScreen.Browser,
                modifier = Modifier.hideFromAccessibilityWhen(browserContentHiddenByRoute),
            ) {
                BrowserPageContent(
                    state = pageState,
                    actions = pageActions,
                    iconsDir = iconsDir,
                    browserContent = {
                        GeckoContent(
                            currentTab,
                            tabPreviewStore,
                            Modifier.fillMaxSize(),
                        )
                        BrowserPageProgress(currentTab?.progress ?: -1f)
                    },
                    findContent = if (currentTab != null) {
                        {
                            key(currentTab.id) {
                                FindInPageRoute(currentTab.session, pageActions.onCloseFind)
                            }
                        }
                    } else {
                        null
                    },
                    startPageContent = {
                        if (currentTab?.isPrivate == true) {
                            StartPage(
                                bookmarks = emptyList(),
                                iconsDir = iconsDir,
                                recent = emptyList(),
                                isPrivate = true,
                                onOpen = { uri -> currentTab.session.loadUri(uri) },
                                onAllBookmarks = {},
                                onAllHistory = {},
                                onRefreshRecent = {},
                                onRename = { _, _ -> },
                                onDelete = {},
                                onAdd = { _, _ -> },
                            )
                        } else {
                            StartPageRoute(
                                bookmarksRepository = bookmarksRepository,
                                historyRepository = historyRepository,
                                iconsDir = iconsDir,
                                refreshKey = currentTab?.id,
                                onOpen = { uri ->
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onAllBookmarks = { browserViewModel.screen(BrowserScreen.Bookmarks) },
                                onAllHistory = { browserViewModel.screen(BrowserScreen.History) },
                            )
                        }
                    },
                )
            }

            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = isForwardBrowserRouteTransition(initialState, targetState)
                    when {
                        initialState == BrowserScreen.Browser && targetState != BrowserScreen.Browser ->
                            chromiumSharedXAxisEnter(forward = true)
                                .togetherWith(ExitTransition.None)

                        initialState != BrowserScreen.Browser && targetState == BrowserScreen.Browser ->
                            EnterTransition.None
                                .togetherWith(chromiumSharedXAxisExit(forward = false))

                        forward ->
                            chromiumSharedXAxisEnter(forward = true)
                                .togetherWith(chromiumSharedXAxisExit(forward = true))

                        else ->
                            chromiumSharedXAxisEnter(forward = false)
                                .togetherWith(chromiumSharedXAxisExit(forward = false))
                    }
                },
                contentKey = { it },
                label = "Chromium internal route",
            ) { targetScreen ->
                when (targetScreen) {
                    BrowserScreen.Browser -> Box(Modifier.fillMaxSize())

                    BrowserScreen.Settings -> {
                        Box(Modifier.fillMaxSize().accessibilityPane(settingsPaneTitle)) {
                            SettingsScreen(
                                state = settingsScreenState,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onEngine = { settingsViewModel.setSearchEngine(it.toSearchEngine()) },
                                onTheme = settingsViewModel::setTheme,
                                onAdblock = toggleAdblock,
                                onRetryAdblock = retryAdblock,
                                onVot = toggleVot,
                                onRetryVot = retryVot,
                                onDownloads = {
                                    downloadsReturnToSettings = true
                                    browserViewModel.screen(BrowserScreen.Downloads)
                                },
                                onClearData = { withBookmarks ->
                                    browserDataViewModel.clear(withBookmarks, browserDataClearer)
                                },
                                onTranslateLang = settingsViewModel::setTranslateTarget,
                            )
                        }
                    }

                    BrowserScreen.Downloads -> {
                        Box(Modifier.fillMaxSize().accessibilityPane(downloadsPaneTitle)) {
                            MotionDownloadsScreen(
                                onBack = {
                                    browserViewModel.screen(
                                        if (downloadsReturnToSettings) {
                                            BrowserScreen.Settings
                                        } else {
                                            BrowserScreen.Browser
                                        },
                                    )
                                },
                            )
                        }
                    }

                    BrowserScreen.History -> {
                        Box(Modifier.fillMaxSize().accessibilityPane(historyPaneTitle)) {
                            MotionHistoryScreen(
                                historyRepository,
                                iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                            )
                        }
                    }

                    BrowserScreen.Bookmarks -> {
                        Box(Modifier.fillMaxSize().accessibilityPane(bookmarksPaneTitle)) {
                            MotionBookmarksScreen(
                                bookmarksRepository,
                                iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                            )
                        }
                    }
                }
            }

            if (showSwitcher) {
                BrowserTabSwitcher(
                    tabs = tabItems,
                    currentId = currentId,
                    iconsDir = iconsDir,
                    previewStore = tabPreviewStore,
                    onSelect = { tabManager.select(it) },
                    onClose = { id ->
                        val closed = tabManager.closeTab(id)
                        if (closed != null) {
                            routeScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = tabClosedMessage,
                                    actionLabel = undoLabel,
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    tabManager.restoreClosedTab(closed)
                                } else {
                                    tabPreviewStore.remove(closed.id)
                                }
                            }
                        }
                    },
                    onNew = { tabManager.newTab(null) },
                    onDismiss = { browserViewModel.showSwitcher(false) },
                )
            }
            if (showSiteInfo && currentTab != null) {
                SiteInfoSheet(chromeState, prefs.adblockEnabled) {
                    browserViewModel.showSiteInfo(false)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            )
        }
    }
}

private fun isForwardBrowserRouteTransition(
    initial: BrowserScreen,
    target: BrowserScreen,
): Boolean = when {
    initial == BrowserScreen.Browser && target != BrowserScreen.Browser -> true
    initial == BrowserScreen.Settings && target == BrowserScreen.Downloads -> true
    else -> false
}

internal fun Modifier.hideFromAccessibilityWhen(hidden: Boolean): Modifier =
    if (hidden) semantics { hideFromAccessibility() } else this

internal fun Modifier.accessibilityPane(title: String): Modifier =
    semantics { paneTitle = title }

private fun ExtensionLoader.Status?.toExtensionUiState(): BrowserExtensionUiState = when (this) {
    ExtensionLoader.Status.Error -> BrowserExtensionUiState.Error
    ExtensionLoader.Status.Enabled -> BrowserExtensionUiState.Enabled
    ExtensionLoader.Status.Disabled -> BrowserExtensionUiState.Disabled
    ExtensionLoader.Status.Installing, null -> BrowserExtensionUiState.Installing
}

private fun PageLoadError?.toUiState(): BrowserPageLoadErrorUiState? = when (this) {
    PageLoadError.Security -> BrowserPageLoadErrorUiState.Security
    PageLoadError.Network -> BrowserPageLoadErrorUiState.Network
    PageLoadError.Generic -> BrowserPageLoadErrorUiState.Generic
    null -> null
}

private fun SearchEngine.toSettingsUiState(): SettingsSearchEngineUiState = when (this) {
    SearchEngine.GOOGLE -> SettingsSearchEngineUiState.Google
    SearchEngine.DUCKDUCKGO -> SettingsSearchEngineUiState.DuckDuckGo
    SearchEngine.YANDEX -> SettingsSearchEngineUiState.Yandex
    SearchEngine.BING -> SettingsSearchEngineUiState.Bing
}

private fun SettingsSearchEngineUiState.toSearchEngine(): SearchEngine = when (this) {
    SettingsSearchEngineUiState.Google -> SearchEngine.GOOGLE
    SettingsSearchEngineUiState.DuckDuckGo -> SearchEngine.DUCKDUCKGO
    SettingsSearchEngineUiState.Yandex -> SearchEngine.YANDEX
    SettingsSearchEngineUiState.Bing -> SearchEngine.BING
}

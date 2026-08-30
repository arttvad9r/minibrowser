@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.artt.minibrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.browser.BrowserActivityRequestController
import com.artt.minibrowser.browser.BrowserDataViewModel
import com.artt.minibrowser.browser.BrowserIntentController
import com.artt.minibrowser.browser.BrowserRootEffects
import com.artt.minibrowser.browser.BrowserTabLifecycleController
import com.artt.minibrowser.browser.BrowserScreen
import com.artt.minibrowser.browser.BrowserViewModel
import com.artt.minibrowser.browser.BrowserWindowController
import com.artt.minibrowser.browser.BrowserWindowEffects
import com.artt.minibrowser.browser.NavigationController
import com.artt.minibrowser.browser.OmniboxSuggestionsViewModel
import com.artt.minibrowser.browser.PageBookmarkViewModel
import com.artt.minibrowser.browser.SettingsViewModel
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildTranslateUri
import com.artt.minibrowser.ui.BrowserTabSwitcher
import com.artt.minibrowser.ui.ErrorOverlay
import com.artt.minibrowser.ui.FindBar
import com.artt.minibrowser.ui.GeckoContent
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.SiteInfoSheet
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.TabPreviewStore
import com.artt.minibrowser.ui.TopBar
import java.io.File

class MainActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }
    private val historyRepo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val bookmarksRepo by lazy { BookmarksRepository(DbHolder.db.dao()) }
    private lateinit var tabManager: TabManager
    private lateinit var tabLifecycle: BrowserTabLifecycleController
    private val browserViewModel by lazy { ViewModelProvider(this)[BrowserViewModel::class.java] }
    private val settingsViewModel by lazy {
        ViewModelProvider(
            this,
            SettingsViewModel.factory(settingsRepo, Engine.runtime),
        )[SettingsViewModel::class.java]
    }
    private val browserDataViewModel by lazy {
        ViewModelProvider(
            this,
            BrowserDataViewModel.factory(
                clearTabPreviews = TabPreviewStore::clear,
                clearHistory = { historyRepo.clear() },
                clearBookmarks = { bookmarksRepo.clearAll() },
                clearFaviconCaches = {
                    com.artt.minibrowser.ui.clearFaviconCaches(File(filesDir, "icons"))
                },
                clearWebData = {
                    tabManager.clearWebData()
                    Unit
                },
            ),
        )[BrowserDataViewModel::class.java]
    }
    private val pageBookmarkViewModel by lazy {
        ViewModelProvider(
            this,
            PageBookmarkViewModel.factory { bookmarksRepo },
        )[PageBookmarkViewModel::class.java]
    }
    private val omniboxSuggestionsViewModel by lazy {
        ViewModelProvider(
            this,
            OmniboxSuggestionsViewModel.factory { historyRepo },
        )[OmniboxSuggestionsViewModel::class.java]
    }

    private val externalNavigation = NavigationController()
    private val activityRequests = BrowserActivityRequestController(this)
    private val browserWindow by lazy { BrowserWindowController(window) }
    private val browserIntents by lazy {
        BrowserIntentController(this) { fallback ->
            if (::tabManager.isInitialized) {
                (tabManager.current() ?: tabManager.newTab(null)).session.loadUri(fallback)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalNavigation.accept(intent.data?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tabManager = TabManager(
            Engine.runtime,
            File(filesDir, "tabs"),
            this,
            permissionRequester = activityRequests::requestPermissions,
            filePicker = activityRequests::pickFiles,
        )
        tabLifecycle = BrowserTabLifecycleController(this, tabManager)
        val iconsDir = File(filesDir, "icons")

        setContent {
            val settingsUi by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val browserDataUi by browserDataViewModel.uiState.collectAsStateWithLifecycle()
            val prefs = settingsUi.prefs
            val adblockStatus = settingsUi.adblockStatus
            val votStatus = settingsUi.votStatus
            val darkTheme = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val browserUi by browserViewModel.state.collectAsStateWithLifecycle()
            val pageBookmarkUi by pageBookmarkViewModel.uiState.collectAsStateWithLifecycle()
            val omniboxSuggestionsUi by omniboxSuggestionsViewModel.uiState.collectAsStateWithLifecycle()
            val screen = browserUi.screen
            val showSwitcher = browserUi.showSwitcher
            val showFind = browserUi.showFind
            val showSiteInfo = browserUi.showSiteInfo

            val tabs by tabManager.tabs.collectAsStateWithLifecycle()
            val currentId by tabManager.currentId.collectAsStateWithLifecycle()
            val currentTab = tabs.firstOrNull { it.id == currentId }
            val currentSession = currentTab?.session
            val focusManager = LocalFocusManager.current
            val omniboxFocus = remember { FocusRequester() }
            val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            val topSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            val bottomSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

            val inFullscreen = currentTab?.fullscreen == true
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
            val bookmarked = pageBookmarkUi.url == currentTab?.url && pageBookmarkUi.isBookmarked
            val showStart = screen == BrowserScreen.Browser &&
                (currentTab?.url.isNullOrBlank() || currentTab.url == "about:blank")

            BrowserWindowEffects(
                controller = browserWindow,
                darkTheme = darkTheme,
                isPrivate = currentTab?.isPrivate == true,
                inFullscreen = inFullscreen,
            )
            val toggleAdblock: (Boolean) -> Unit = settingsViewModel::setAdblock
            val retryAdblock: () -> Unit = settingsViewModel::retryAdblock
            val toggleVot: (Boolean) -> Unit = settingsViewModel::setVot
            val retryVot: () -> Unit = settingsViewModel::retryVot
            val onShare: () -> Unit = { browserIntents.shareUrl(currentTab?.url) }

            MinibrowserTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .focusable(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(horizontalSafeInsets),
                        ) {
                            if (!inFullscreen) {
                                Box(Modifier.windowInsetsPadding(topSafeInsets)) {
                                    TopBar(
                                        currentTab,
                                        engine = prefs.searchEngine,
                                        tabCount = tabs.size,
                                        bookmarked = bookmarked,
                                        iconsDir = iconsDir,
                                        omniboxFocus = omniboxFocus,
                                        suggestions = omniboxSuggestionsUi.suggestions,
                                        onSuggestionQueryChanged = omniboxSuggestionsViewModel::updateQuery,
                                        onNavigate = { uri ->
                                            (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                        },
                                        onExternal = browserIntents::openExternalUri,
                                        onBack = { currentSession?.goBack() },
                                        onForward = { currentSession?.goForward() },
                                        onReload = { currentSession?.reload() },
                                        onSiteInfo = { browserViewModel.showSiteInfo(true) },
                                        onSwitcher = {
                                            TabPreviewStore.captureCurrent()
                                            browserViewModel.showSwitcher(true)
                                        },
                                        onNewTab = { tabManager.newTab(null) },
                                        onNewPrivateTab = { tabManager.newTab(null, private = true) },
                                        onFind = { browserViewModel.showFind(true) },
                                        onToggleBookmark = {
                                            val t = currentTab ?: return@TopBar
                                            pageBookmarkViewModel.toggle(t.url, t.title)
                                        },
                                        onBookmarks = { browserViewModel.screen(BrowserScreen.Bookmarks) },
                                        onHistory = { browserViewModel.screen(BrowserScreen.History) },
                                        onShare = onShare,
                                        onSettings = { browserViewModel.screen(BrowserScreen.Settings) },
                                        onToggleAdblock = toggleAdblock,
                                        onRetryAdblock = retryAdblock,
                                        adblockStatus = adblockStatus,
                                        onTranslate = {
                                            val u = currentTab?.url ?: return@TopBar
                                            buildTranslateUri(u, prefs.translateTarget)?.let(currentTab.session::loadUri)
                                        },
                                    )
                                }
                            }
                            if (showFind && currentSession != null && !inFullscreen) {
                                key(currentTab.id) {
                                    FindBar(currentSession) { browserViewModel.showFind(false) }
                                }
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .windowInsetsPadding(bottomSafeInsets),
                            ) {
                                GeckoContent(currentTab, Modifier.fillMaxSize())
                                SmoothPageProgress(currentTab)
                                if (showStart) {
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
                                            bookmarksRepository = bookmarksRepo,
                                            historyRepository = historyRepo,
                                            iconsDir = iconsDir,
                                            refreshKey = currentTab?.id,
                                            onOpen = { uri -> currentTab?.session?.loadUri(uri) },
                                            onAllBookmarks = { browserViewModel.screen(BrowserScreen.Bookmarks) },
                                            onAllHistory = { browserViewModel.screen(BrowserScreen.History) },
                                        )
                                    }
                                }
                                if (!showStart && currentTab?.loadError != null) {
                                    ErrorOverlay(currentTab.loadError.orEmpty()) { currentSession?.reload() }
                                }
                            }
                        }
                        if (screen == BrowserScreen.Settings) {
                            Box(Modifier.fillMaxSize()) {
                                SettingsScreen(
                                    prefs,
                                    onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                    onEngine = settingsViewModel::setSearchEngine,
                                    onTheme = settingsViewModel::setTheme,
                                    onAdblock = toggleAdblock,
                                    onRetryAdblock = retryAdblock,
                                    adblockStatus = adblockStatus,
                                    votEnabled = prefs.votEnabled,
                                    votStatus = votStatus,
                                    onVot = toggleVot,
                                    onRetryVot = retryVot,
                                    onClearData = browserDataViewModel::clear,
                                    clearDataInProgress = browserDataUi.isClearing,
                                    clearDataFailed = browserDataUi.clearFailed,
                                    onTranslateLang = settingsViewModel::setTranslateTarget,
                                )
                            }
                        }
                        if (screen == BrowserScreen.History) Box(Modifier.fillMaxSize()) {
                            MotionHistoryScreen(
                                historyRepo,
                                iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                            )
                        }
                        if (screen == BrowserScreen.Bookmarks) Box(Modifier.fillMaxSize()) {
                            MotionBookmarksScreen(
                                bookmarksRepo,
                                iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                            )
                        }
                        if (showSwitcher) {
                            BrowserTabSwitcher(
                                tabs, currentId, iconsDir,
                                onSelect = { tabManager.select(it) },
                                onClose = {
                                    TabPreviewStore.remove(it)
                                    tabManager.closeTab(it)
                                },
                                onNew = { browserViewModel.showSwitcher(false); tabManager.newTab(null) },
                                onDismiss = { browserViewModel.showSwitcher(false) },
                            )
                        }
                        if (showSiteInfo && currentTab != null) {
                            SiteInfoSheet(currentTab, prefs.adblockEnabled) { browserViewModel.showSiteInfo(false) }
                        }
                    }
                }
            }
        }
        externalNavigation.accept(intent?.data?.toString())
    }

    override fun onDestroy() {
        activityRequests.cancelAll()
        if (::tabManager.isInitialized) tabManager.close()
        super.onDestroy()
    }
}

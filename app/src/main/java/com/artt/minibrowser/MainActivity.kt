package com.artt.minibrowser

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.artt.minibrowser.browser.BrowserActivityRequestController
import com.artt.minibrowser.browser.BrowserDataClearer
import com.artt.minibrowser.browser.BrowserDataViewModel
import com.artt.minibrowser.browser.BrowserIntentController
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserScreen
import com.artt.minibrowser.browser.BrowserShortcut
import com.artt.minibrowser.browser.BrowserTabLifecycleController
import com.artt.minibrowser.browser.BrowserViewModel
import com.artt.minibrowser.browser.BrowserWindowController
import com.artt.minibrowser.browser.NavigationController
import com.artt.minibrowser.browser.OmniboxSuggestionsViewModel
import com.artt.minibrowser.browser.PageBookmarkViewModel
import com.artt.minibrowser.browser.SettingsViewModel
import com.artt.minibrowser.browser.browserShortcutForAction
import com.artt.minibrowser.browser.initialExternalNavigationUri
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.engine.BackgroundTabHost
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.FaviconRepository
import com.artt.minibrowser.engine.TabManager
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity(), BackgroundTabHost {
    private val browserApp by lazy { application as BrowserApp }
    private val runtime by lazy { browserApp.runtime }
    private val extensionLoader by lazy { browserApp.extensionLoader }
    private val settingsRepo by lazy { SettingsRepository(this) }
    private val historyRepo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val bookmarksRepo by lazy { BookmarksRepository(DbHolder.db.dao()) }
    private val iconsDir by lazy { File(filesDir, "icons") }
    private val tabPreviewStore by lazy { browserApp.tabPreviewStore }
    private val pictureInPicture by lazy { BrowserPictureInPictureController(this) }
    private val backgroundTabOpened = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private lateinit var tabManager: TabManager
    private val browserViewModel by lazy { ViewModelProvider(this)[BrowserViewModel::class.java] }
    private val settingsViewModel by lazy {
        ViewModelProvider(
            this,
            SettingsViewModel.factory(settingsRepo, extensionLoader),
        )[SettingsViewModel::class.java]
    }
    private val browserDataClearer by lazy {
        BrowserDataClearer(
            clearTabPreviews = tabPreviewStore::clear,
            clearHistory = { historyRepo.clear() },
            clearBookmarks = { bookmarksRepo.clearAll() },
            clearFaviconCaches = { FaviconRepository.clear(iconsDir) },
            clearWebData = { tabManager.clearWebData() },
        )
    }
    private val browserDataViewModel by lazy {
        ViewModelProvider(this)[BrowserDataViewModel::class.java]
    }
    private val pageBookmarkViewModel by lazy {
        ViewModelProvider(
            this,
            PageBookmarkViewModel.factory { BookmarksRepository(DbHolder.db.dao()) },
        )[PageBookmarkViewModel::class.java]
    }
    private val omniboxSuggestionsViewModel by lazy {
        ViewModelProvider(
            this,
            OmniboxSuggestionsViewModel.factory { HistoryRepository(DbHolder.db.dao()) },
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
        if (!handleShortcut(intent)) {
            externalNavigation.accept(intent.data?.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tabManager = TabManager(
            runtime,
            File(filesDir, "tabs"),
            this,
            permissionRequester = activityRequests::requestPermissions,
            filePicker = activityRequests::pickFiles,
        )
        BrowserTabLifecycleController(this, tabManager)
        val handledShortcut = handleShortcut(intent)

        setContent {
            BrowserPictureInPictureEffect(tabManager, pictureInPicture)
            BrowserRoute(
                tabManager = tabManager,
                settingsViewModel = settingsViewModel,
                browserDataViewModel = browserDataViewModel,
                browserDataClearer = browserDataClearer,
                browserViewModel = browserViewModel,
                pageBookmarkViewModel = pageBookmarkViewModel,
                omniboxSuggestionsViewModel = omniboxSuggestionsViewModel,
                bookmarksRepository = bookmarksRepo,
                historyRepository = historyRepo,
                browserWindow = browserWindow,
                browserIntents = browserIntents,
                externalNavigation = externalNavigation,
                backgroundTabOpened = backgroundTabOpened,
                tabPreviewStore = tabPreviewStore,
                iconsDir = iconsDir,
            )
        }
        if (!handledShortcut) {
            externalNavigation.accept(
                initialExternalNavigationUri(
                    intentUri = intent?.data?.toString(),
                    hasSavedInstanceState = savedInstanceState != null,
                ),
            )
        }
    }

    override fun openBackgroundTab(uri: String, private: Boolean) {
        if (!::tabManager.isInitialized) return
        val previousId = tabManager.currentId.value
        val opened = tabManager.newTab(uri, private)
        if (previousId != null) tabManager.select(previousId)
        backgroundTabOpened.tryEmit(opened.id)
    }

    private fun handleShortcut(intent: Intent?): Boolean {
        val shortcut = browserShortcutForAction(intent?.action) ?: return false
        // Keep a handled shortcut from being replayed if Android recreates this singleTask Activity.
        intent?.action = Intent.ACTION_MAIN
        when (shortcut) {
            BrowserShortcut.NewTab -> openShortcutTab(private = false)
            BrowserShortcut.NewPrivateTab -> openShortcutTab(private = true)
        }
        return true
    }

    private fun openShortcutTab(private: Boolean) {
        if (!::tabManager.isInitialized) return
        tabManager.newTab(null, private)
        browserViewModel.screen(BrowserScreen.Browser)
        browserViewModel.showSwitcher(false)
        browserViewModel.showFind(false)
        browserViewModel.showSiteInfo(false)
    }

    override fun onPictureInPictureRequested(): Boolean {
        if (!::tabManager.isInitialized) return false
        val entered = pictureInPicture.enterIfEligible()
        if (entered) tabManager.setAppVisible(true)
        return entered
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            ::tabManager.isInitialized &&
            pictureInPicture.enterIfEligible()
        ) {
            tabManager.setAppVisible(true)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode && ::tabManager.isInitialized) {
            // BrowserTabLifecycleController may receive onPause during the transition. Keep the
            // selected GeckoSession active so suspendMediaWhenInactive does not stop the video.
            tabManager.setAppVisible(true)
        }
    }

    override fun onDestroy() {
        activityRequests.cancelAll()
        super.onDestroy()
    }
}

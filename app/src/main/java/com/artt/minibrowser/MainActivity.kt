package com.artt.minibrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.artt.minibrowser.browser.BrowserActivityRequestController
import com.artt.minibrowser.browser.BrowserDataClearer
import com.artt.minibrowser.browser.BrowserDataViewModel
import com.artt.minibrowser.browser.BrowserIntentController
import com.artt.minibrowser.browser.BrowserTabLifecycleController
import com.artt.minibrowser.browser.BrowserViewModel
import com.artt.minibrowser.browser.BrowserWindowController
import com.artt.minibrowser.browser.NavigationController
import com.artt.minibrowser.browser.OmniboxSuggestionsViewModel
import com.artt.minibrowser.browser.PageBookmarkViewModel
import com.artt.minibrowser.browser.SettingsViewModel
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.FaviconRepository
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.ui.TabPreviewStore
import java.io.File

class MainActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }
    private val historyRepo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val bookmarksRepo by lazy { BookmarksRepository(DbHolder.db.dao()) }
    private val iconsDir by lazy { File(filesDir, "icons") }
    private lateinit var tabManager: TabManager
    private val browserViewModel by lazy { ViewModelProvider(this)[BrowserViewModel::class.java] }
    private val settingsViewModel by lazy {
        ViewModelProvider(
            this,
            SettingsViewModel.factory(settingsRepo, Engine.runtime),
        )[SettingsViewModel::class.java]
    }
    private val browserDataClearer by lazy {
        BrowserDataClearer(
            clearTabPreviews = TabPreviewStore::clear,
            clearHistory = { historyRepo.clear() },
            clearBookmarks = { bookmarksRepo.clearAll() },
            clearFaviconCaches = { FaviconRepository.clear(iconsDir) },
            clearWebData = {
                tabManager.clearWebData()
                Unit
            },
        )
    }
    private val browserDataViewModel by lazy {
        ViewModelProvider(
            this,
            BrowserDataViewModel.factory(browserDataClearer),
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
        BrowserTabLifecycleController(this, tabManager)

        setContent {
            BrowserRoute(
                tabManager = tabManager,
                settingsViewModel = settingsViewModel,
                browserDataViewModel = browserDataViewModel,
                browserViewModel = browserViewModel,
                pageBookmarkViewModel = pageBookmarkViewModel,
                omniboxSuggestionsViewModel = omniboxSuggestionsViewModel,
                bookmarksRepository = bookmarksRepo,
                historyRepository = historyRepo,
                browserWindow = browserWindow,
                browserIntents = browserIntents,
                externalNavigation = externalNavigation,
                iconsDir = iconsDir,
            )
        }
        externalNavigation.accept(intent?.data?.toString())
    }

    override fun onDestroy() {
        activityRequests.cancelAll()
        super.onDestroy()
    }
}

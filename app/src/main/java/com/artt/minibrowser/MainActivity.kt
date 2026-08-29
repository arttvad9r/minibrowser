@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.artt.minibrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.artt.minibrowser.browser.ActivityRequestCoordinator
import com.artt.minibrowser.browser.BrowserScreen
import com.artt.minibrowser.browser.BrowserViewModel
import com.artt.minibrowser.browser.NavigationController
import com.artt.minibrowser.browser.areRequestedPermissionsSatisfied
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.NavigationTarget
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.SecurityState
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.engine.buildTranslateUri
import com.artt.minibrowser.engine.createSafeExternalIntent
import com.artt.minibrowser.engine.formatFindCounter
import com.artt.minibrowser.engine.safeExternalFallbackUrl
import com.artt.minibrowser.ui.AppIcons
import com.artt.minibrowser.ui.BrowserBottomSheet
import com.artt.minibrowser.ui.BrowserTabSwitcher
import com.artt.minibrowser.ui.BrowserTextField
import com.artt.minibrowser.ui.Favicon
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.QuickAction
import com.artt.minibrowser.ui.Radius
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.SheetRow
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.TabPreviewStore
import com.artt.minibrowser.ui.ToggleRow
import com.artt.minibrowser.ui.hostOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.io.File

class MainActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }
    private val historyRepo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val bookmarksRepo by lazy { BookmarksRepository(DbHolder.db.dao()) }
    private lateinit var tabManager: TabManager
    private val browserViewModel by lazy { ViewModelProvider(this)[BrowserViewModel::class.java] }

    private val externalNavigation = NavigationController()
    private val permissionRequests = ActivityRequestCoordinator<Boolean>()
    private val fileRequests = ActivityRequestCoordinator<Array<Uri>>()
    private var permissionCompletion: ((Boolean) -> Unit)? = null
    private var pendingPermissionRequest: Set<String> = emptySet()
    private var fileCompletion: ((Array<Uri>) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val requested = pendingPermissionRequest
        pendingPermissionRequest = emptySet()
        val completion = permissionCompletion
        permissionCompletion = null
        completion?.invoke(areRequestedPermissionsSatisfied(requested, grants))
    }
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val completion = fileCompletion
        fileCompletion = null
        completion?.invoke(uris.toTypedArray())
    }
    private val singleFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val completion = fileCompletion
        fileCompletion = null
        completion?.invoke(uri?.let { arrayOf(it) } ?: emptyArray())
    }
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val completion = fileCompletion
        fileCompletion = null
        completion?.invoke(uri?.let { arrayOf(it) } ?: emptyArray())
    }

    private fun openExternalUri(value: String) {
        val external = createSafeExternalIntent(value)
        if (external != null && external.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(external, "Открыть с помощью"))
            return
        }
        safeExternalFallbackUrl(value)?.let { fallback ->
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
        tabManager = TabManager(
            Engine.runtime,
            File(filesDir, "tabs"),
            this,
            permissionRequester = { permissions, callback ->
                permissionRequests.enqueue(
                    start = { complete ->
                        pendingPermissionRequest = permissions.toSet()
                        permissionCompletion = { granted -> callback(granted); complete(granted) }
                        permissionLauncher.launch(permissions)
                    },
                    cancel = { callback(false) },
                )
            },
            filePicker = { type, mimeTypes, callback ->
                runOnUiThread {
                    fileRequests.enqueue(
                        start = { complete ->
                            val accepted = mimeTypes
                                .filter { it.isNotBlank() && it.contains('/') }
                                .distinct()
                                .toTypedArray()
                                .let { if (it.isEmpty()) arrayOf("*/*") else it }
                            fileCompletion = { uris ->
                                callback(uris)
                                complete(uris)
                            }
                            runCatching {
                                when (type) {
                                    GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE ->
                                        filePickerLauncher.launch(accepted)
                                    GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER ->
                                        folderPickerLauncher.launch(null)
                                    GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE ->
                                        singleFilePickerLauncher.launch(accepted)
                                    else ->
                                        singleFilePickerLauncher.launch(accepted)
                                }
                            }.onFailure {
                                fileCompletion = null
                                val none = emptyArray<Uri>()
                                callback(none)
                                complete(none)
                            }
                        },
                        cancel = { callback(emptyArray()) },
                    )
                }
            },
        )
        val iconsDir = File(filesDir, "icons")
        // Расширения ставятся один раз за процесс; тумблер применяется сразу после первого чтения настроек.
        lifecycleScope.launch {
            val prefs = settingsRepo.prefs.first()
            ExtensionLoader.installAll(Engine.runtime, prefs.adblockEnabled, prefs.votEnabled)
        }

        setContent {
            val prefs by settingsRepo.prefs.collectAsStateWithLifecycle(Prefs())
            val extensionStates by ExtensionLoader.state.collectAsStateWithLifecycle()
            val adblockStatus = extensionStates[ExtensionLoader.UBLOCK_ID]?.status
            val votStatus = extensionStates[ExtensionLoader.VOT_ID]?.status
            val scope = rememberCoroutineScope()
            val darkTheme = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val browserUi by browserViewModel.state.collectAsStateWithLifecycle()
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

            // Уход с браузера закрывает подсказки омнибокса (попап рисуется поверх любых экранов).
            LaunchedEffect(screen, showSwitcher) {
                if (screen != BrowserScreen.Browser || showSwitcher) focusManager.clearFocus(force = true)
            }
            // Светлая тема — тёмные иконки системных баров, тёмная — светлые.
            LaunchedEffect(darkTheme) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.isAppearanceLightStatusBars = !darkTheme
                c.isAppearanceLightNavigationBars = !darkTheme
            }
            LaunchedEffect(currentTab?.isPrivate) {
                if (currentTab?.isPrivate == true) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }

            LaunchedEffect(Unit) {
                externalNavigation.setHandler { uri -> tabManager.newTab(uri) }
            }

            var bookmarks by remember { mutableStateOf(emptyList<Bookmark>()) }
            var bmReload by remember { mutableIntStateOf(0) }
            LaunchedEffect(bmReload) { bookmarks = bookmarksRepo.all() }
            var bookmarked by remember { mutableStateOf(false) }
            LaunchedEffect(currentTab?.url) {
                val u = currentTab?.url
                bookmarked = !u.isNullOrBlank() && u.startsWith("http") && bookmarksRepo.isBookmarked(u)
            }
            var recent by remember { mutableStateOf(emptyList<HistoryEntry>()) }
            var recentReload by remember { mutableIntStateOf(0) }
            val showStart = screen == BrowserScreen.Browser &&
                (currentTab?.url.isNullOrBlank() || currentTab.url == "about:blank")
            LaunchedEffect(showStart, currentTab?.url, recentReload) {
                if (showStart) recent = historyRepo.recent(3)
            }

            BackHandler(enabled = screen == BrowserScreen.Browser && currentTab?.canGoBack == true) {
                currentTab?.session?.goBack()
            }
            val inFullscreen = currentTab?.fullscreen == true
            BackHandler(enabled = inFullscreen) { currentSession?.exitFullScreen() }
            BackHandler(enabled = showFind) {
                currentSession?.finder?.clear()
                browserViewModel.showFind(false)
            }
            LaunchedEffect(inFullscreen) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                if (inFullscreen) {
                    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    c.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            val toggleAdblock: (Boolean) -> Unit = { b ->
                scope.launch {
                    settingsRepo.setAdblock(b)
                    ExtensionLoader.setAdblock(Engine.runtime, b)
                }
            }
            val retryAdblock: () -> Unit = {
                ExtensionLoader.retryAdblock(Engine.runtime, prefs.adblockEnabled)
            }
            val toggleVot: (Boolean) -> Unit = { enabled ->
                scope.launch {
                    settingsRepo.setVot(enabled)
                    ExtensionLoader.setVot(Engine.runtime, enabled)
                }
            }
            val retryVot: () -> Unit = {
                ExtensionLoader.retryVot(Engine.runtime, prefs.votEnabled)
            }
            val onShare: () -> Unit = {
                currentTab?.url?.let { u ->
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, u)
                            },
                            "Поделиться",
                        ),
                    )
                }
            }

            MinibrowserTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .focusable(),
                ) {
                    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        Column(Modifier.fillMaxSize()) {
                            if (!inFullscreen) TopBar(
                                currentTab,
                                engine = prefs.searchEngine,
                                tabCount = tabs.size,
                                bookmarked = bookmarked,
                                iconsDir = iconsDir,
                                omniboxFocus = omniboxFocus,
                                onNavigate = { uri ->
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onExternal = ::openExternalUri,
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
                                    scope.launch {
                                        if (bookmarked) bookmarksRepo.remove(t.url)
                                        else bookmarksRepo.add(t.url, t.title)
                                        bookmarked = !bookmarked
                                        bmReload++
                                    }
                                },
                                onBookmarks = { browserViewModel.screen(BrowserScreen.Bookmarks) },
                                onHistory = { browserViewModel.screen(BrowserScreen.History) },
                                onShare = onShare,
                                onSettings = { browserViewModel.screen(BrowserScreen.Settings) },
                                onSuggest = { q -> historyRepo.suggest(q) },
                                onToggleAdblock = toggleAdblock,
                                onRetryAdblock = retryAdblock,
                                adblockStatus = adblockStatus,
                                onTranslate = {
                                    val u = currentTab?.url ?: return@TopBar
                                    buildTranslateUri(u, prefs.translateTarget)?.let(currentTab.session::loadUri)
                                },
                            )
                            if (showFind && currentSession != null && !inFullscreen) {
                                key(currentTab.id) {
                                    FindBar(currentSession) { browserViewModel.showFind(false) }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                GeckoContent(currentTab, Modifier.fillMaxSize())
                                SmoothPageProgress(currentTab)
                                if (showStart) {
                                    StartPage(
                                        bookmarks, iconsDir, recent, currentTab?.isPrivate == true,
                                        onOpen = { uri -> currentTab?.session?.loadUri(uri) },
                                        onAllBookmarks = { browserViewModel.screen(BrowserScreen.Bookmarks) },
                                        onAllHistory = { browserViewModel.screen(BrowserScreen.History) },
                                        onRefreshRecent = { recentReload++ },
                                        onRename = { url, t -> scope.launch { bookmarksRepo.rename(url, t); bmReload++ } },
                                        onDelete = { url -> scope.launch { bookmarksRepo.remove(url); bmReload++ } },
                                        onAdd = { url, title ->
                                            scope.launch {
                                                bookmarksRepo.add(url, title)
                                                bmReload++
                                            }
                                        },
                                    )
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
                                    onEngine = { e -> scope.launch { settingsRepo.setSearchEngine(e) } },
                                    onTheme = { t -> scope.launch { settingsRepo.setTheme(t) } },
                                    onAdblock = toggleAdblock,
                                    onRetryAdblock = retryAdblock,
                                    adblockStatus = adblockStatus,
                                    votEnabled = prefs.votEnabled,
                                    votStatus = votStatus,
                                    onVot = toggleVot,
                                    onRetryVot = retryVot,
                                    onTranslateLang = { lang -> scope.launch { settingsRepo.setTranslateTarget(lang) } },
                                    onClearData = { withBookmarks ->
                                        scope.launch {
                                            historyRepo.clear()
                                            if (withBookmarks) bookmarksRepo.clearAll()
                                            com.artt.minibrowser.ui.clearFaviconCaches(iconsDir)
                                            tabManager.clearWebData()
                                            bmReload++
                                            recentReload++
                                        }
                                    },
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
                                bookmarks, iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onRename = { url, t -> scope.launch { bookmarksRepo.rename(url, t); bmReload++ } },
                                onDelete = { url -> scope.launch { bookmarksRepo.remove(url); bmReload++ } },
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

    override fun onPause() {
        super.onPause()
        if (::tabManager.isInitialized) {
            tabManager.setAppVisible(false)
            tabManager.persist()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tabManager.isInitialized) tabManager.setAppVisible(true)
    }

    override fun onDestroy() {
        permissionRequests.cancelAll()
        fileRequests.cancelAll()
        pendingPermissionRequest = emptySet()
        permissionCompletion = null
        fileCompletion = null
        if (::tabManager.isInitialized) tabManager.close()
        super.onDestroy()
    }
}

@Composable
private fun GeckoContent(
    tab: Tab?,
    modifier: Modifier = Modifier,
) {
    val session = tab?.session
    val tabId = tab?.id
    val url = tab?.url.orEmpty()
    val isPrivate = tab?.isPrivate == true
    val pageSettled = tab != null && tab.progress < 0f &&
        (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))

    AndroidView(
        factory = { context -> GeckoView(context) },
        update = { view ->
            if (view.session !== session) {
                TabPreviewStore.captureBeforeSessionSwap(view)
                view.releaseSession()
                session?.let(view::setSession)
            }
            TabPreviewStore.maybeCapture(
                view = view,
                tabId = tabId,
                url = url,
                isPrivate = isPrivate,
                pageSettled = pageSettled,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun TopBar(
    tab: Tab?,
    engine: SearchEngine,
    tabCount: Int,
    bookmarked: Boolean,
    iconsDir: File,
    omniboxFocus: FocusRequester,
    adblockStatus: ExtensionLoader.Status?,
    onToggleAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onNavigate: (String) -> Unit,
    onExternal: (String) -> Unit,
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
    onSettings: () -> Unit,
    onSuggest: suspend (String) -> List<Suggestion>,
    onTranslate: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val rawUrl = tab?.url ?: ""
    val newTab = rawUrl.isBlank() || rawUrl == "about:blank"
    val shown = if (focused) text else (if (newTab) "" else rawUrl)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused, text, rawUrl, newTab) {
        val userHasEdited = newTab || text != rawUrl
        suggestions = if (focused && text.isNotBlank() && userHasEdited) onSuggest(text) else emptyList()
    }
    val navigate: (String) -> Unit = { q ->
        when (val target = com.artt.minibrowser.engine.resolveNavigation(q)) {
            is NavigationTarget.External -> onExternal(target.uri)
            is NavigationTarget.Web, is NavigationTarget.Internal, is NavigationTarget.Search ->
                onNavigate(buildLoadUri(q, engine))
        }
        focusManager.clearFocus(force = true)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .onGloballyPositioned { fieldSize = it.size },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(Radius.field)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leadingIsSearch = newTab
                IconButton(
                    onClick = {
                        if (leadingIsSearch) {
                            if (text.isBlank()) omniboxFocus.requestFocus() else navigate(text)
                        } else {
                            onSiteInfo()
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .semantics {
                            contentDescription = when {
                                !leadingIsSearch -> "Информация о сайте"
                                text.isNotBlank() -> "Искать"
                                else -> "Поиск"
                            }
                        },
                ) {
                    if (leadingIsSearch) {
                        if (tab?.isPrivate == true) {
                            Icon(
                                AppIcons.Incognito,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Search,
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (tab?.isPrivate == true) {
                                Icon(
                                    AppIcons.Incognito,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (tab?.securityState == SecurityState.Secure) {
                                Icon(
                                    Icons.Filled.Lock,
                                    null,
                                    Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Icon(
                                    AppIcons.Globe,
                                    null,
                                    Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (shown.isEmpty()) {
                        Text(
                            "Поиск или адрес",
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = shown,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(omniboxFocus)
                            .onFocusChanged {
                                if (it.isFocused && !focused) {
                                    text = if (newTab) "" else rawUrl
                                }
                                focused = it.isFocused
                                if (!it.isFocused) text = ""
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { navigate(text) }),
                    )
                }
            }
            if (focused && suggestions.isNotEmpty()) {
                val density = LocalDensity.current
                val offsetY = with(density) { 8.dp.roundToPx() }
                val suggestionsWidth = with(density) {
                    (fieldSize.width + 48.dp.roundToPx() + 46.dp.roundToPx() + 48.dp.roundToPx() + 8.dp.roundToPx()).toDp()
                }
                val suggestionsHeight = (suggestions.size * 56 + 8).coerceAtMost(176).dp
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, fieldSize.height + offsetY),
                    onDismissRequest = { focusManager.clearFocus(force = true) },
                ) {
                    Column(
                        Modifier
                            .width(suggestionsWidth)
                            .height(suggestionsHeight)
                            .clip(Radius.card)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.card),
                    ) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            suggestions.forEach { s ->
                                SuggestionRow(s, iconsDir) {
                                    focusManager.clearFocus()
                                    onNavigate(s.url)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = { focusManager.clearFocus(force = true); onNewTab() },
            modifier = Modifier.semantics { contentDescription = "Новая вкладка" },
        ) {
            Icon(Icons.Filled.Add, null)
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(Radius.button)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { focusManager.clearFocus(force = true); onSwitcher() }
                .semantics { contentDescription = "Вкладки" },
            contentAlignment = Alignment.Center,
        ) {
            Text("$tabCount", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(
            onClick = { focusManager.clearFocus(force = true); menuOpen = true },
            modifier = Modifier.semantics { contentDescription = "Меню" },
        ) {
            Icon(Icons.Filled.MoreVert, null)
        }
    }

    if (menuOpen) {
        MenuSheet(
            tab = tab,
            bookmarked = bookmarked,
            adblockStatus = adblockStatus,
            onDismiss = { menuOpen = false },
            onNewTab = onNewTab,
            onNewPrivateTab = onNewPrivateTab,
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
            onToggleBookmark = onToggleBookmark,
            onBookmarks = onBookmarks,
            onHistory = onHistory,
            onFind = onFind,
            onShare = onShare,
            onTranslate = onTranslate,
            onToggleAdblock = onToggleAdblock,
            onRetryAdblock = onRetryAdblock,
            onSettings = onSettings,
            onToggleDesktop = {
                val t = tab ?: return@MenuSheet
                t.desktop = !t.desktop
                t.session.settings.userAgentMode =
                    if (t.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                t.session.settings.viewportMode =
                    if (t.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                    else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                t.session.reload()
            },
        )
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, iconsDir: File, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favicon(s.url, iconsDir, 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.label.ifBlank { s.url },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.label.isNotBlank()) {
                Text(
                    hostOf(s.url).ifBlank { s.url },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MenuSheet(
    tab: Tab?,
    bookmarked: Boolean,
    adblockStatus: ExtensionLoader.Status?,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onTranslate: () -> Unit,
    onToggleAdblock: (Boolean) -> Unit,
    onRetryAdblock: () -> Unit,
    onSettings: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    val httpPage = tab?.url?.startsWith("http") == true
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius.button)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuNavigationAction(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Назад",
                tab?.canGoBack == true,
                Modifier.weight(1f),
            ) { onDismiss(); onBack() }
            MenuNavigationAction(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Вперёд",
                tab?.canGoForward == true,
                Modifier.weight(1f),
            ) { onDismiss(); onForward() }
            MenuNavigationAction(
                Icons.Filled.Refresh,
                "Обновить",
                httpPage,
                Modifier.weight(1f),
            ) { onDismiss(); onReload() }
            MenuNavigationAction(
                if (bookmarked) Icons.Filled.Star else AppIcons.Star,
                if (bookmarked) "Убрать из закладок" else "В закладки",
                httpPage,
                Modifier.weight(1f),
            ) { onDismiss(); onToggleBookmark() }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Filled.Add, "Новая\nвкладка", { onDismiss(); onNewTab() })
            QuickAction(AppIcons.Incognito, "Приватная\nвкладка", { onDismiss(); onNewPrivateTab() })
            QuickAction(AppIcons.Star, "Закладки", { onDismiss(); onBookmarks() })
            QuickAction(AppIcons.History, "История", { onDismiss(); onHistory() })
        }
        MenuDivider()
        SheetRow(Icons.Filled.Search, "Найти на странице", enabled = httpPage, onClick = { onDismiss(); onFind() })
        tab?.takeIf { httpPage }?.let { currentTab ->
            ToggleRow(
                AppIcons.Desktop,
                "Версия для ПК",
                currentTab.desktop,
                onChecked = { onDismiss(); onToggleDesktop() },
            )
        }
        SheetRow(Icons.Filled.Share, "Поделиться", enabled = httpPage, onClick = { onDismiss(); onShare() })
        SheetRow(AppIcons.Globe, "Перевести страницу", enabled = httpPage, onClick = { onDismiss(); onTranslate() })
        MenuDivider()
        when (adblockStatus) {
            null, ExtensionLoader.Status.Installing ->
                SheetRow(
                    AppIcons.Shield,
                    "Блокировка рекламы",
                    enabled = false,
                    trailing = { Text("Запуск…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            ExtensionLoader.Status.Error ->
                SheetRow(
                    AppIcons.Shield,
                    "Блокировка рекламы",
                    trailing = { Text("Ошибка · повторить", color = MaterialTheme.colorScheme.error) },
                    onClick = { onDismiss(); onRetryAdblock() },
                )
            ExtensionLoader.Status.Enabled ->
                ToggleRow(AppIcons.Shield, "Блокировка рекламы", true, onToggleAdblock, subtitle = "Блокирует рекламу и трекеры")
            ExtensionLoader.Status.Disabled ->
                ToggleRow(AppIcons.Shield, "Блокировка рекламы", false, onToggleAdblock, subtitle = "Блокирует рекламу и трекеры")
        }
        SheetRow(Icons.Filled.Settings, "Настройки", onClick = { onDismiss(); onSettings() })
    }
}

@Composable
private fun MenuNavigationAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier
            .height(48.dp)
            .clip(Radius.small)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    }
}

@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(4.dp))
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun FindBar(session: GeckoSession, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val doFind: (Boolean) -> Unit = { backward ->
        if (q.isBlank()) {
            session.finder.clear()
            total = 0
            current = 0
        } else {
            session.finder.find(
                q,
                if (backward) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD,
            ).accept { result ->
                total = result?.total ?: 0
                current = result?.current ?: 0
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(Radius.field)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserTextField(q, { q = it; doFind(false) }, Modifier.weight(1f), placeholder = "Найти на странице")
            if (total > 0) {
                Text(
                    formatFindCounter(current, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconBtn(Icons.Filled.KeyboardArrowUp, "Предыдущее") { doFind(true) }
        IconBtn(Icons.Filled.KeyboardArrowDown, "Следующее") { doFind(false) }
        IconBtn(Icons.Filled.Close, "Закрыть поиск") { session.finder.clear(); onClose() }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = desc }) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SiteInfoSheet(tab: Tab, adblockEnabled: Boolean, onDismiss: () -> Unit) {
    val host = hostOf(tab.url).ifBlank { tab.url.ifBlank { "Новая вкладка" } }
    val message = when (tab.securityState) {
        SecurityState.Secure -> "Соединение защищено"
        SecurityState.Exception -> "Есть исключение безопасности"
        SecurityState.Insecure -> "Соединение не защищено"
        SecurityState.Unknown -> "Состояние соединения неизвестно"
    }
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Text(host, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SheetRow(
            AppIcons.Shield,
            "Блокировка рекламы",
            trailing = { Text(if (adblockEnabled) "Вкл" else "Выкл", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Проверьте адрес и соединение",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Повторить") }
    }
}

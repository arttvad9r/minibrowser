@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.artt.minibrowser

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.browser.BrowserScreen
import com.artt.minibrowser.browser.ActivityRequestCoordinator
import com.artt.minibrowser.browser.NavigationController
import com.artt.minibrowser.browser.BrowserViewModel
import com.artt.minibrowser.browser.areRequestedPermissionsSatisfied
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.NavigationTarget
import com.artt.minibrowser.engine.SecurityState
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.engine.buildTranslateUri
import com.artt.minibrowser.engine.createSafeExternalIntent
import com.artt.minibrowser.engine.formatFindCounter
import com.artt.minibrowser.engine.safeExternalFallbackUrl
import com.artt.minibrowser.ui.AddBookmarkSheet
import com.artt.minibrowser.ui.AppIcons
import com.artt.minibrowser.ui.BookmarkActionsSheet
import com.artt.minibrowser.ui.BrowserBottomSheet
import com.artt.minibrowser.ui.BrowserTextField
import com.artt.minibrowser.ui.EmptyState
import com.artt.minibrowser.ui.Favicon
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.QuickAction
import com.artt.minibrowser.ui.Radius
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.SheetRow
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.ToggleRow
import com.artt.minibrowser.ui.hostOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

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
        permissionCompletion?.invoke(areRequestedPermissionsSatisfied(requested, grants))
        permissionCompletion = null
    }
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        fileCompletion?.invoke(uris.toTypedArray())
        fileCompletion = null
    }
    private val singleFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        fileCompletion?.invoke(uri?.let { arrayOf(it) } ?: emptyArray())
        fileCompletion = null
    }
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        fileCompletion?.invoke(uri?.let { arrayOf(it) } ?: emptyArray())
        fileCompletion = null
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
                fileRequests.enqueue(
                    start = { complete ->
                        fileCompletion = { uris ->
                            callback(uris)
                            complete(uris)
                        }
                        when (type) {
                            GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE ->
                                filePickerLauncher.launch(mimeTypes.ifEmpty { arrayOf("*/*") })
                            GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER ->
                                folderPickerLauncher.launch(null)
                            GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE ->
                                singleFilePickerLauncher.launch(mimeTypes.ifEmpty { arrayOf("*/*") })
                            else ->
                                singleFilePickerLauncher.launch(mimeTypes.ifEmpty { arrayOf("*/*") })
                        }
                    },
                    cancel = { callback(emptyArray()) },
                )
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
            // Недавние страницы для домашнего экрана.
            var recent by remember { mutableStateOf(emptyList<HistoryEntry>()) }
            var recentReload by remember { mutableIntStateOf(0) }
            val showStart = screen == BrowserScreen.Browser &&
                (currentTab?.url.isNullOrBlank() || currentTab.url == "about:blank")
            LaunchedEffect(showStart, currentTab?.url, recentReload) {
                if (showStart) recent = historyRepo.recent(3)
            }

            BackHandler(enabled = screen != BrowserScreen.Browser) { browserViewModel.screen(BrowserScreen.Browser) }
            // Системный back на странице браузера — назад по истории вкладки.
            BackHandler(enabled = screen == BrowserScreen.Browser && currentTab?.canGoBack == true) {
                currentTab?.session?.goBack()
            }
            // Во время fullscreen-видео back выходит из полноэкранного режима.
            val inFullscreen = currentTab?.fullscreen == true
            BackHandler(enabled = inFullscreen) { currentSession?.exitFullScreen() }
            BackHandler(enabled = showSwitcher) { browserViewModel.showSwitcher(false) }
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
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, u)
                            },
                            "Поделиться")
                    )
                }
            }

            MinibrowserTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        // Принимает на себя restoreDefaultFocus при получении окном фокуса,
                        // иначе фокус уходит в первое поле (омнибокс) и выезжает клавиатура.
                        .focusable(),
                ) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
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
                                onSwitcher = { browserViewModel.showSwitcher(true) },
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
                                adblockEnabled = prefs.adblockEnabled,
                                adblockStatus = adblockStatus,
                                onTranslate = {
                                    val u = currentTab?.url ?: return@TopBar
                                    buildTranslateUri(u, prefs.translateTarget)?.let(currentTab.session::loadUri)
                                })
                            if (showFind && currentSession != null && !inFullscreen) {
                                key(currentTab.id) {
                                    FindBar(currentSession) { browserViewModel.showFind(false) }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                GeckoContent(currentSession, Modifier.fillMaxSize())
                                PageProgress(currentTab)
                                if (showStart) {
                                    StartPage(
                                        bookmarks, iconsDir, recent, currentTab?.isPrivate == true,
                                        onSearchFocus = { omniboxFocus.requestFocus() },
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
                                        })
                                }
                                if (!showStart && currentTab?.loadError != null) {
                                    ErrorOverlay(currentTab.loadError.orEmpty()) { currentSession?.reload() }
                                }
                            }
                        }
                        if (screen == BrowserScreen.Settings) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                                            iconsDir.deleteRecursively()
                                            tabManager.clearWebData()
                                            bmReload++
                                            recentReload++
                                        }
                                    },
                                )
                            }
                        }
                        if (screen == BrowserScreen.History) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HistoryScreen(
                                historyRepo,
                                iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                })
                        }
                        if (screen == BrowserScreen.Bookmarks) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            BookmarksScreen(
                                bookmarks, iconsDir,
                                onBack = { browserViewModel.screen(BrowserScreen.Browser) },
                                onOpen = { uri ->
                                    browserViewModel.screen(BrowserScreen.Browser)
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onRename = { url, t -> scope.launch { bookmarksRepo.rename(url, t); bmReload++ } },
                                onDelete = { url -> scope.launch { bookmarksRepo.remove(url); bmReload++ } })
                        }
                        // Переключатель вкладок — полноэкранный слой поверх браузера.
                        if (showSwitcher) {
                            TabSwitcher(
                                tabs, currentId, iconsDir,
                                onSelect = { tabManager.select(it); browserViewModel.showSwitcher(false) },
                                onClose = { tabManager.closeTab(it) },
                                onNew = { browserViewModel.showSwitcher(false); tabManager.newTab(null) },
                                onDismiss = { browserViewModel.showSwitcher(false) })
                        }
                        if (showSiteInfo && currentTab != null) {
                            SiteInfoSheet(currentTab, prefs.adblockEnabled) { browserViewModel.showSiteInfo(false) }
                        }
                    }
                }
            }
        }
        // Холодный старт с VIEW-интентом: хук ещё не готов — uri уйдёт в pendingUri.
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
        super.onDestroy()
    }
}

@Composable
private fun GeckoContent(
    session: GeckoSession?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> GeckoView(context) },
        update = { view ->
            if (view.session !== session) {
                view.releaseSession()
                session?.let(view::setSession)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun PageProgress(tab: Tab?) {
    val progress = tab?.progress ?: -1f
    if (progress >= 0f) {
        LinearProgressIndicator(
            progress = { progress },
            Modifier.fillMaxWidth().height(2.dp),
        )
    }
}

// ---------- Верхняя панель: омнибокс + подсказки + действия ----------

@Composable
private fun TopBar(
    tab: Tab?,
    engine: SearchEngine,
    tabCount: Int,
    bookmarked: Boolean,
    iconsDir: File,
    omniboxFocus: FocusRequester,
    adblockEnabled: Boolean,
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
    // Пока поле в фокусе — текст пользователя, иначе живой URL текущей вкладки.
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val rawUrl = tab?.url ?: ""
    val newTab = rawUrl.isBlank() || rawUrl == "about:blank"
    val shown = if (focused) text else (if (newTab) "" else rawUrl)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused, text) {
        suggestions = if (focused) onSuggest(text) else emptyList()
    }
    val navigate: (String) -> Unit = { q ->
        when (val target = com.artt.minibrowser.engine.resolveNavigation(q, engine)) {
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
                IconButton(onClick = onSiteInfo, modifier = Modifier.size(32.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        if (tab?.isPrivate == true) {
                            Icon(AppIcons.Incognito, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (tab?.securityState != SecurityState.Secure) {
                            Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (tab?.securityState == SecurityState.Secure) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // Подсказки: лёгкая панель под адресной строкой вместо desktop-dropdown.
            if (focused && suggestions.isNotEmpty()) {
                val density = LocalDensity.current
                val offsetY = with(density) { 8.dp.roundToPx() }
                // Расширяем surface до полезной ширины top bar, а не оставляем узкой
                // карточкой только по ширине поля ввода.
                val suggestionsWidth = with(density) {
                    (fieldSize.width + 48.dp.roundToPx() + 46.dp.roundToPx() + 48.dp.roundToPx() + 8.dp.roundToPx()).toDp()
                }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, fieldSize.height + offsetY),
                    onDismissRequest = {},
                ) {
                    Column(
                        Modifier
                            .width(suggestionsWidth)
                            // Видно ~3 строки, остальное — прокрутка (список из 8 закрывал пол-экрана).
                            .height(176.dp)
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
        IconButton(onClick = { focusManager.clearFocus(force = true); onNewTab() },
            modifier = Modifier.semantics { contentDescription = "Новая вкладка" }) {
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
        IconButton(onClick = { focusManager.clearFocus(force = true); menuOpen = true },
            modifier = Modifier.semantics { contentDescription = "Меню" }) {
            Icon(Icons.Filled.MoreVert, null)
        }
    }

    if (menuOpen) {
        MenuSheet(
            tab = tab,
            bookmarked = bookmarked,
            adblockEnabled = adblockEnabled,
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

// bookmarked передаётся из MainActivity (состояние «текущий url в закладках»).

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
        Favicon(hostOf(s.url), iconsDir, 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.label.ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (s.label.isNotBlank()) {
                Text(hostOf(s.url).ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Главное меню: bottom sheet ----------

@Composable
private fun MenuSheet(
    tab: Tab?,
    bookmarked: Boolean,
    adblockEnabled: Boolean,
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
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Назад",
                enabled = tab?.canGoBack == true,
                modifier = Modifier.weight(1f),
            ) { onDismiss(); onBack() }
            MenuNavigationAction(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                description = "Вперёд",
                enabled = tab?.canGoForward == true,
                modifier = Modifier.weight(1f),
            ) { onDismiss(); onForward() }
            MenuNavigationAction(
                icon = Icons.Filled.Refresh,
                description = "Обновить",
                enabled = tab != null && tab.url.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { onDismiss(); onReload() }
            MenuNavigationAction(
                icon = if (bookmarked) Icons.Filled.Star else AppIcons.Star,
                description = if (bookmarked) "Убрать из закладок" else "В закладки",
                enabled = httpPage,
                modifier = Modifier.weight(1f),
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
        SheetRow(Icons.Filled.Search, "Найти на странице", enabled = true, onClick = { onDismiss(); onFind() })
        SheetRow(AppIcons.Desktop, "Версия для ПК", enabled = tab != null,
            trailing = {
                Switch(
                    checked = tab?.desktop == true,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            onClick = { onDismiss(); onToggleDesktop() })
        SheetRow(Icons.Filled.Share, "Поделиться", enabled = httpPage, onClick = { onDismiss(); onShare() })
        SheetRow(AppIcons.Globe, "Перевести страницу", enabled = httpPage, onClick = { onDismiss(); onTranslate() })
        MenuDivider()
        when (adblockStatus) {
            null, ExtensionLoader.Status.Installing ->
                SheetRow(AppIcons.Shield, "Блокировка рекламы", enabled = false, trailing = { Text("Запуск…", color = MaterialTheme.colorScheme.onSurfaceVariant) })
            ExtensionLoader.Status.Error ->
                SheetRow(AppIcons.Shield, "Блокировка рекламы", trailing = { Text("Ошибка · повторить", color = MaterialTheme.colorScheme.error) }, onClick = { onDismiss(); onRetryAdblock() })
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
        Icon(
            icon,
            null,
            Modifier.size(23.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}

@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(4.dp))
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

// ---------- Переключатель вкладок ----------

@Composable
private fun TabSwitcher(
    tabs: List<Tab>,
    currentId: Long?,
    iconsDir: File,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Закрыть" }) {
                Icon(AppIcons.ChevronDown, null)
            }
            Text(
                "${tabs.size} ${tabsPlural(tabs.size)}",
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = onNew, modifier = Modifier.semantics { contentDescription = "Новая вкладка" }) {
                Icon(Icons.Filled.Add, null)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabCard(
                    tab, isCurrent = tab.id == currentId, iconsDir,
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) })
            }
        }
    }
}

private fun tabsPlural(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "вкладка"
    n % 10 in 2..4 && (n % 100 < 12 || n % 100 > 14) -> "вкладки"
    else -> "вкладок"
}

/** Карточка вкладки: заголовок с favicon, мини-адресная строка, зона превью. */
@Composable
private fun TabCard(
    tab: Tab,
    isCurrent: Boolean,
    iconsDir: File,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val host = hostOf(tab.url)
    Column(
        Modifier
            .clip(Radius.card)
            .background(
                if (tab.isPrivate) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (isCurrent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                Radius.card,
            )
            .clickable(onClick = onSelect),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (tab.isPrivate) {
                Icon(AppIcons.Incognito, "Приватная вкладка", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Favicon(host, iconsDir, 18.dp)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                tab.title.ifBlank {
                    if (tab.url.isBlank() || tab.url == "about:blank") "Новая вкладка" else tab.url
                },
                Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = "Закрыть вкладку" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Мини-адресная строка, как на референсе.
        Row(
            Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                host.ifBlank { "Поиск" },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Зона превью: снапшотов нет — честный плейсхолдер с favicon (без фейковых скриншотов).
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (tab.isPrivate) {
                Icon(AppIcons.Incognito, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            } else if (host.isNotBlank()) {
                Favicon(host, iconsDir, 40.dp)
            } else {
                Icon(AppIcons.Globe, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ---------- Поиск по странице ----------

@Composable
private fun FindBar(session: GeckoSession, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val doFind: (Boolean) -> Unit = { backward ->
        if (q.isBlank()) {
            session.finder.clear(); total = 0; current = 0
        } else {
            session.finder.find(
                q,
                if (backward) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD,
            ).accept { r ->
                total = r?.total ?: 0; current = r?.current ?: 0
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
                Text(formatFindCounter(current, total), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Проверьте адрес и соединение", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Повторить") }
    }
}

// ---------- История ----------

@Composable
private fun HistoryScreen(
    repo: HistoryRepository,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LaunchedEffect(reload) { entries = repo.recent(200) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("История", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { scope.launch { repo.clear(); reload++ } }) {
                Text("Очистить", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entries.isEmpty()) {
            EmptyState(AppIcons.History, "История пуста", "Посещённые страницы появятся здесь.")
        } else {
            val groups = remember(entries) { groupByDay(entries) }
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (label, items) ->
                    item(key = "header_$label") {
                        Text(
                            label,
                            Modifier.padding(start = 24.dp, top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(items, key = { it.url }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(e.url) }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Favicon(hostOf(e.url), iconsDir, 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title.ifBlank { e.url }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${hostOf(e.url)} · ${timeFormat.format(Date(e.visitedAt))}",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Группировка по «Сегодня / Вчера / Ранее» на основе visitedAt. */
private fun groupByDay(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> {
    val cal = Calendar.getInstance()
    val today = cal.clone() as Calendar
    today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0)
    val todayStart = today.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
    val groups = linkedMapOf("Сегодня" to mutableListOf<HistoryEntry>(),
        "Вчера" to mutableListOf<HistoryEntry>(), "Ранее" to mutableListOf<HistoryEntry>())
    entries.forEach { e ->
        when {
            e.visitedAt >= todayStart -> groups.getValue("Сегодня").add(e)
            e.visitedAt >= yesterdayStart -> groups.getValue("Вчера").add(e)
            else -> groups.getValue("Ранее").add(e)
        }
    }
    return groups.filter { it.value.isNotEmpty() }.map { it.key to it.value.toList() }
}

// ---------- Закладки ----------

@Composable
private fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<Bookmark?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Закладки", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        if (bookmarks.isEmpty()) {
            EmptyState(AppIcons.Star, "Закладок пока нет", "Сохранённые страницы появятся здесь.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(bookmarks, key = { it.url }) { bm ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(bm.url) }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Favicon(bm.host, iconsDir, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bm.title.ifBlank { bm.host }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(hostOf(bm.url).ifBlank { bm.url }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { selected = bm },
                            modifier = Modifier.size(40.dp).semantics { contentDescription = "Действия" },
                        ) { Icon(Icons.Filled.MoreVert, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    val sel = selected
    if (sel != null) {
        BookmarkActionsSheet(
            bookmark = sel,
            onDismiss = { selected = null },
            onOpen = { onOpen(sel.url); selected = null },
            onRename = { onRename(sel.url, it); selected = null },
            onDelete = { onDelete(sel.url); selected = null },
        )
    }
}

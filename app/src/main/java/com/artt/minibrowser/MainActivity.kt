@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.artt.minibrowser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.engine.buildTranslateUri
import com.artt.minibrowser.engine.enqueueDownload
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

enum class Screen { Browser, Settings, History, Bookmarks }

class MainActivity : ComponentActivity() {
    private val settingsRepo by lazy { SettingsRepository(this) }
    private val historyRepo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val bookmarksRepo by lazy { BookmarksRepository(DbHolder.db.dao()) }
    private lateinit var tabManager: TabManager

    // Навигация из VIEW-интентов; заполняется в setContent.
    private var navigateHook: ((String) -> Unit)? = null
    private var pendingUri: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent?) {
        val uri = intent?.data?.toString() ?: return
        val hook = navigateHook
        if (hook != null) hook(uri) else pendingUri = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabManager = TabManager(Engine.runtime, File(filesDir, "tabs"), applicationContext)
        val iconsDir = File(filesDir, "icons")
        // Расширения ставятся один раз за процесс; тумблер применяется сразу после первого чтения настроек.
        lifecycleScope.launch {
            ExtensionLoader.installAll(Engine.runtime, settingsRepo.prefs.first().adblockEnabled)
        }

        setContent {
            val prefs by settingsRepo.prefs.collectAsStateWithLifecycle(Prefs())
            val scope = rememberCoroutineScope()
            val darkTheme = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            var screen by remember { mutableStateOf(Screen.Browser) }
            var showSwitcher by remember { mutableStateOf(false) }

            val tabs by tabManager.tabs.collectAsStateWithLifecycle()
            val currentId by tabManager.currentId.collectAsStateWithLifecycle()
            val currentTab = tabs.firstOrNull { it.id == currentId }
            val currentSession = currentTab?.session
            val focusManager = LocalFocusManager.current
            val omniboxFocus = remember { FocusRequester() }

            // Уход с браузера закрывает подсказки омнибокса (попап рисуется поверх любых экранов).
            LaunchedEffect(screen, showSwitcher) {
                if (screen != Screen.Browser || showSwitcher) focusManager.clearFocus(force = true)
            }
            // Светлая тема — тёмные иконки системных баров, тёмная — светлые.
            LaunchedEffect(darkTheme) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.isAppearanceLightStatusBars = !darkTheme
                c.isAppearanceLightNavigationBars = !darkTheme
            }

            navigateHook = { uri ->
                val t = tabs.firstOrNull { it.id == tabManager.currentId.value } ?: tabManager.newTab(null)
                t.session.loadUri(uri)
            }
            // Интент, пришедший до готовности хука (холодный старт), доставляем один раз.
            pendingUri?.let { u -> pendingUri = null; navigateHook?.invoke(u) }

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
            val showStart = screen == Screen.Browser &&
                (currentTab?.url.isNullOrBlank() || currentTab.url == "about:blank")
            LaunchedEffect(showStart, currentTab?.url, recentReload) {
                if (showStart) recent = historyRepo.recent(3)
            }

            BackHandler(enabled = screen != Screen.Browser) { screen = Screen.Browser }
            // Системный back на странице браузера — назад по истории вкладки.
            BackHandler(enabled = screen == Screen.Browser && currentTab?.canGoBack == true) {
                currentTab?.session?.goBack()
            }
            // Во время fullscreen-видео back выходит из полноэкранного режима.
            val inFullscreen = currentTab?.fullscreen == true
            BackHandler(enabled = inFullscreen) { currentSession?.exitFullScreen() }
            BackHandler(enabled = showSwitcher) { showSwitcher = false }
            LaunchedEffect(inFullscreen) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                if (inFullscreen) {
                    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    c.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            var showFind by remember { mutableStateOf(false) }

            val toggleAdblock: (Boolean) -> Unit = { b ->
                scope.launch {
                    settingsRepo.setAdblock(b)
                    ExtensionLoader.setAdblock(Engine.runtime, b)
                }
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
            val onDownload: () -> Unit = {
                currentTab?.url?.let { u ->
                    val fallback = u.substringAfterLast('/').substringBefore('?').ifBlank { "page" }
                    runCatching { enqueueDownload(applicationContext, u, fallback) }
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
                                onSwitcher = { showSwitcher = true },
                                onNewTab = { tabManager.newTab(null) },
                                onNewPrivateTab = { tabManager.newTab(null, private = true) },
                                onFind = { showFind = true },
                                onToggleBookmark = {
                                    val t = currentTab ?: return@TopBar
                                    scope.launch {
                                        if (bookmarked) bookmarksRepo.remove(t.url)
                                        else bookmarksRepo.add(t.url, t.title)
                                        bookmarked = !bookmarked
                                        bmReload++
                                    }
                                },
                                onBookmarks = { screen = Screen.Bookmarks },
                                onHistory = { screen = Screen.History },
                                onShare = onShare,
                                onDownload = onDownload,
                                onSettings = { screen = Screen.Settings },
                                onSuggest = { q -> historyRepo.suggest(q) },
                                onToggleAdblock = toggleAdblock,
                                adblockEnabled = prefs.adblockEnabled,
                                onTranslate = {
                                    val u = currentTab?.url ?: return@TopBar
                                    buildTranslateUri(u, prefs.translateTarget)?.let(currentTab.session::loadUri)
                                })
                            if (showFind && currentSession != null && !inFullscreen) {
                                FindBar(currentSession) { showFind = false }
                            }
                            Box(Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { ctx ->
                                        GeckoView(ctx).apply {
                                            // Тап по странице снимает фокус с омнибокса — закрывает подсказки.
                                            setOnTouchListener { _, ev ->
                                                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                                                    focusManager.clearFocus(force = true)
                                                }
                                                false
                                            }
                                        }
                                    },
                                    update = { v ->
                                        if (v.session !== currentSession) {
                                            v.releaseSession()
                                            currentSession?.let(v::setSession)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize())
                                if (currentTab != null && currentTab.progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { currentTab.progress },
                                        Modifier.fillMaxWidth().height(2.dp))
                                }
                                if (showStart) {
                                     StartPage(
                                         bookmarks, iconsDir, recent, currentTab?.isPrivate == true,
                                        onSearchFocus = { omniboxFocus.requestFocus() },
                                        onOpen = { uri -> currentTab?.session?.loadUri(uri) },
                                        onAllBookmarks = { screen = Screen.Bookmarks },
                                        onAllHistory = { screen = Screen.History },
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
                            }
                        }
                        if (screen == Screen.Settings) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                SettingsScreen(
                                    prefs,
                                    onBack = { screen = Screen.Browser },
                                    onEngine = { e -> scope.launch { settingsRepo.setSearchEngine(e) } },
                                    onTheme = { t -> scope.launch { settingsRepo.setTheme(t) } },
                                    onAdblock = toggleAdblock,
                                    onTranslateLang = { lang -> scope.launch { settingsRepo.setTranslateTarget(lang) } },
                                    onClearData = { withBookmarks ->
                                        scope.launch {
                                            historyRepo.clear()
                                            if (withBookmarks) bookmarksRepo.clearAll()
                                            iconsDir.deleteRecursively()
                                            bmReload++
                                            recentReload++
                                        }
                                    },
                                )
                            }
                        }
                        if (screen == Screen.History) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HistoryScreen(
                                historyRepo,
                                iconsDir,
                                onBack = { screen = Screen.Browser },
                                onOpen = { uri ->
                                    screen = Screen.Browser
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                })
                        }
                        if (screen == Screen.Bookmarks) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            BookmarksScreen(
                                bookmarks, iconsDir,
                                onBack = { screen = Screen.Browser },
                                onOpen = { uri ->
                                    screen = Screen.Browser
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onRename = { url, t -> scope.launch { bookmarksRepo.rename(url, t); bmReload++ } },
                                onDelete = { url -> scope.launch { bookmarksRepo.remove(url); bmReload++ } })
                        }
                        // Переключатель вкладок — полноэкранный слой поверх браузера.
                        if (showSwitcher) {
                            TabSwitcher(
                                tabs, currentId, iconsDir,
                                onSelect = { tabManager.select(it); showSwitcher = false },
                                onClose = { tabManager.closeTab(it) },
                                onNew = { showSwitcher = false; tabManager.newTab(null) },
                                onDismiss = { showSwitcher = false })
                        }
                    }
                }
            }
        }
        // Холодный старт с VIEW-интентом: хук ещё не готов — uri уйдёт в pendingUri.
        openFromIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        if (::tabManager.isInitialized) tabManager.persist()
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
    onToggleAdblock: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
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
        if (q.isNotBlank()) onNavigate(buildLoadUri(q, engine))
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
                 Icon(
                     if (tab?.isPrivate == true) AppIcons.Incognito else Icons.Filled.Search,
                     null,
                     Modifier.size(20.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant,
                 )
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
            onDismiss = { menuOpen = false },
            onNewTab = onNewTab,
            onNewPrivateTab = onNewPrivateTab,
            onToggleBookmark = onToggleBookmark,
            onBookmarks = onBookmarks,
            onHistory = onHistory,
            onFind = onFind,
            onShare = onShare,
            onDownload = onDownload,
            onTranslate = onTranslate,
            onToggleAdblock = onToggleAdblock,
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
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onTranslate: () -> Unit,
    onToggleAdblock: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onToggleDesktop: () -> Unit,
) {
    val httpPage = tab?.url?.startsWith("http") == true
    BrowserBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Filled.Add, "Новая\nвкладка", { onDismiss(); onNewTab() })
            QuickAction(AppIcons.Incognito, "Приватная\nвкладка", { onDismiss(); onNewPrivateTab() })
            QuickAction(AppIcons.Star, "Закладки", { onDismiss(); onBookmarks() })
            QuickAction(AppIcons.History, "История", { onDismiss(); onHistory() })
        }
        MenuDivider()
        SheetRow(if (bookmarked) Icons.Filled.Star else AppIcons.Star,
            if (bookmarked) "Убрать из закладок" else "В закладки",
            enabled = httpPage, onClick = { onDismiss(); onToggleBookmark() })
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
        SheetRow(AppIcons.Download, "Скачать", enabled = httpPage, onClick = { onDismiss(); onDownload() })
        SheetRow(Icons.Filled.Share, "Поделиться", enabled = httpPage, onClick = { onDismiss(); onShare() })
        SheetRow(AppIcons.Globe, "Перевести страницу", enabled = httpPage, onClick = { onDismiss(); onTranslate() })
        MenuDivider()
        ToggleRow(AppIcons.Shield, "Блокировка рекламы", adblockEnabled, onToggleAdblock,
            subtitle = "Блокирует рекламу и трекеры")
        SheetRow(Icons.Filled.Settings, "Настройки", onClick = { onDismiss(); onSettings() })
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
                Text("${current + 1}/$total", style = MaterialTheme.typography.bodySmall,
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

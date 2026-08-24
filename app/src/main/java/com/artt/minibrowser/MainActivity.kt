@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.HistoryRepository
import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.BookmarksRepository
import com.artt.minibrowser.data.Prefs
import com.artt.minibrowser.data.SettingsRepository
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.engine.Engine
import com.artt.minibrowser.engine.ExtensionLoader
import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.TabManager
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.ui.SettingsScreen
import com.artt.minibrowser.ui.StartPage
import com.artt.minibrowser.ui.TileGrid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import java.io.File
import java.text.DateFormat
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
        tabManager = TabManager(Engine.runtime, File(filesDir, "tabs"))
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
            var showTray by remember { mutableStateOf(false) }

            val tabs by tabManager.tabs.collectAsStateWithLifecycle()
            val currentId by tabManager.currentId.collectAsStateWithLifecycle()
            val currentTab = tabs.firstOrNull { it.id == currentId }
            val currentSession = currentTab?.session

            navigateHook = { uri ->
                val t = tabs.firstOrNull { it.id == tabManager.currentId.value } ?: tabManager.newTab(null)
                android.util.Log.d("MB", "hook: $uri -> tab ${t.id}")
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

            BackHandler(enabled = screen != Screen.Browser) { screen = Screen.Browser }

            val toggleAdblock: (Boolean) -> Unit = { b ->
                scope.launch {
                    settingsRepo.setAdblock(b)
                    ExtensionLoader.setAdblock(Engine.runtime, b)
                }
            }

            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { ctx -> GeckoView(ctx) },
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
                                val showStart = screen == Screen.Browser &&
                                    (currentTab?.url.isNullOrBlank() || currentTab?.url == "about:blank")
                                if (showStart) {
                                    Surface(Modifier.fillMaxSize()) {
                                        StartPage(
                                            bookmarks, iconsDir, prefs.searchEngine,
                                            onSearch = { q ->
                                                if (q.isNotBlank()) {
                                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(buildLoadUri(q, prefs.searchEngine))
                                                }
                                            },
                                            onOpen = { uri -> currentTab?.session?.loadUri(uri) },
                                            onRename = { url, t -> scope.launch { bookmarksRepo.rename(url, t); bmReload++ } },
                                            onDelete = { url -> scope.launch { bookmarksRepo.remove(url); bmReload++ } })
                                    }
                                }
                            }
                             BottomBar(
                                 currentTab,
                                 prefs.searchEngine,
                                 tabCount = tabs.size,
                                 bookmarked = bookmarked,
                                 adblockEnabled = prefs.adblockEnabled,
                                 onToggleAdblock = toggleAdblock,
                                onNavigate = { uri ->
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                },
                                onTray = { showTray = true },
                                onNewTab = { showTray = false; tabManager.newTab(null) },
                                onNewPrivateTab = { tabManager.newTab(null, private = true) },
                                onToggleBookmark = {
                                    val t = currentTab ?: return@BottomBar
                                    scope.launch {
                                        if (bookmarked) bookmarksRepo.remove(t.url)
                                        else bookmarksRepo.add(t.url, t.title)
                                        bookmarked = !bookmarked
                                        bmReload++
                                    }
                                },
                                onBookmarks = { screen = Screen.Bookmarks },
                                onHistory = { screen = Screen.History },
                                onSettings = { screen = Screen.Settings },
                                onSuggest = { q -> historyRepo.suggest(q) })
                        }
                        if (screen == Screen.Settings) {
                            Surface(Modifier.fillMaxSize()) {
                                SettingsScreen(
                                    prefs,
                                    onBack = { screen = Screen.Browser },
                                    onEngine = { e -> scope.launch { settingsRepo.setSearchEngine(e) } },
                                    onTheme = { t -> scope.launch { settingsRepo.setTheme(t) } },
                                    onAdblock = toggleAdblock,
                                    onHomepage = { u -> scope.launch { settingsRepo.setHomepage(u) } },
                                )
                            }
                        }
                        if (screen == Screen.History) Surface(Modifier.fillMaxSize()) {
                            HistoryScreen(
                                historyRepo,
                                onBack = { screen = Screen.Browser },
                                onOpen = { uri ->
                                    screen = Screen.Browser
                                    (currentTab ?: tabManager.newTab(null)).session.loadUri(uri)
                                })
                        }
                        if (screen == Screen.Bookmarks) Surface(Modifier.fillMaxSize()) {
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
                        if (showTray) {
                            ModalBottomSheet(onDismissRequest = { showTray = false }) {
                                TabTray(
                                    tabs, currentId,
                                    onSelect = { tabManager.select(it); showTray = false },
                                    onClose = { tabManager.closeTab(it) },
                                    onNew = { showTray = false; tabManager.newTab(null) })
                            }
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

@Composable
private fun BottomBar(
    tab: Tab?,
    engine: SearchEngine,
    tabCount: Int,
    bookmarked: Boolean,
    adblockEnabled: Boolean,
    onToggleAdblock: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onTray: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onSuggest: suspend (String) -> List<Suggestion>,
) {
    // Пока поле в фокусе — текст пользователя, иначе живой URL текущей вкладки.
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val shown = if (focused) text else (tab?.url ?: "")
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused, shown) {
        suggestions = if (focused) onSuggest(text) else emptyList()
    }
    val navigate: (String) -> Unit = { q ->
        if (q.isNotBlank()) onNavigate(buildLoadUri(q, engine))
        focusManager.clearFocus(force = true)
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = shown,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) text = ""
                },
                singleLine = true, placeholder = { Text("Поиск или адрес") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { navigate(text) }))
            DropdownMenu(
                expanded = focused && suggestions.isNotEmpty(),
                onDismissRequest = {},
                // focusable=false: иначе попап перехватывает ввод с клавиатуры у текстового поля.
                properties = PopupProperties(focusable = false)) {
                suggestions.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(s.label.ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            focusManager.clearFocus()
                            onNavigate(s.url)
                        })
                }
            }
        }
        TextButton(onClick = { navigate(shown); text = "" }) { Text("→") }
        IconButton(onClick = onTray, modifier = Modifier.semantics { contentDescription = "Вкладки" }) {
            Text("$tabCount", style = MaterialTheme.typography.titleMedium)
        }
        Box {
            IconButton(onClick = { menuOpen = true },
                modifier = Modifier.semantics { contentDescription = "Меню" }) {
                Icon(Icons.Filled.MoreVert, "Меню")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Новая вкладка") }, onClick = { menuOpen = false; onNewTab() })
                DropdownMenuItem(text = { Text("Приватная вкладка") }, onClick = { menuOpen = false; onNewPrivateTab() })
                DropdownMenuItem(
                    text = { Text(if (bookmarked) "Убрать из закладок" else "В закладки") },
                    enabled = tab?.url?.startsWith("http") == true,
                    onClick = { menuOpen = false; onToggleBookmark() })
                DropdownMenuItem(text = { Text("Закладки") }, onClick = { menuOpen = false; onBookmarks() })
                DropdownMenuItem(text = { Text("История") }, onClick = { menuOpen = false; onHistory() })
                DropdownMenuItem(
                    text = { Text("Блокировка рекламы") },
                    trailingIcon = { Checkbox(checked = adblockEnabled, onCheckedChange = null) },
                    onClick = { menuOpen = false; onToggleAdblock(!adblockEnabled) })
                DropdownMenuItem(text = { Text("Настройки") }, onClick = { menuOpen = false; onSettings() })
            }
        }
    }
}

@Composable
private fun TabTray(
    tabs: List<Tab>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(8.dp).verticalScroll(rememberScrollState())) {
        tabs.forEach { tab ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(tab.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tab.title.ifBlank { tab.url.ifBlank { "Новая вкладка" } },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (tab.id == currentId) FontWeight.Bold else null)
                    if (tab.url.isNotBlank()) {
                        Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.semantics { contentDescription = "Закрыть вкладку" }) {
                    Icon(Icons.Filled.Close, "Закрыть вкладку")
                }
            }
        }
        TextButton(onClick = onNew) { Text("+ Новая вкладка") }
    }
}

@Composable
private fun HistoryScreen(repo: HistoryRepository, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LaunchedEffect(reload) { entries = repo.recent(200) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("История", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { scope.launch { repo.clear(); reload++ } }) { Text("Очистить") }
        }
        if (entries.isEmpty()) {
            Text("История пуста", Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(entries) { e ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(e.url) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title.ifBlank { e.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.url, Modifier.weight(1f, fill = false),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                Text("  ·  ${visitsLabel(e.visits)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(dateFormat.format(Date(e.visitedAt)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun visitsLabel(visits: Int) =
    when (visits) {
        1 -> "1 визит"
        in 2..4 -> "$visits визита"
        else -> "$visits визитов"
    }

@Composable
private fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    iconsDir: File,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Закладки", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        if (bookmarks.isEmpty()) {
            Text("Закладок нет", Modifier.padding(16.dp))
        } else {
            TileGrid(bookmarks, iconsDir, Modifier.fillMaxSize().padding(horizontal = 8.dp),
                onOpen = onOpen, onRename = onRename, onDelete = onDelete)
        }
    }
}
